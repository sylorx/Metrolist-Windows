package com.metrolist.desktop

import java.io.File
import kotlin.math.abs

/** One line of an imported playlist: what is known about the song before it is looked up. */
internal data class ImportRow(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val videoId: String? = null,
    val localPath: String? = null,
)

/** Reads M3U/M3U8, CSV (Exportify, YouTube Takeout and similar) or one "Artist - Title" per line. */
internal fun parsePlaylistFile(name: String, text: String): List<ImportRow> {
    val body = text.removePrefix("﻿")
    return when {
        name.endsWith(".csv", true) -> parseCsvPlaylist(body)
        name.endsWith(".m3u", true) || name.endsWith(".m3u8", true) || body.trimStart().startsWith("#EXTM3U") -> parseM3u(body)
        else -> body.lines().mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") }?.let(::rowFromText) }
    }.filter { it.title.isNotBlank() || it.videoId != null || it.localPath != null }
}

private val VIDEO_ID = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")

private fun rowFromText(text: String): ImportRow {
    VIDEO_ID.find(text)?.let { return ImportRow(title = "", videoId = it.groupValues[1]) }
    val parts = text.split(" - ", " – ", " — ", limit = 2)
    return if (parts.size == 2) ImportRow(title = parts[1].trim(), artist = parts[0].trim()) else ImportRow(title = text.trim())
}

internal fun parseM3u(text: String): List<ImportRow> {
    val rows = mutableListOf<ImportRow>()
    var pending: ImportRow? = null
    for (raw in text.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("#EXTINF", true) -> {
                val seconds = line.substringAfter(':').substringBefore(',').trim().toLongOrNull() ?: 0
                pending = rowFromText(line.substringAfter(',', "")).copy(durationMs = seconds.coerceAtLeast(0) * 1000)
            }
            line.isEmpty() || line.startsWith("#") -> Unit
            else -> {
                val base = pending ?: ImportRow(title = "")
                val id = VIDEO_ID.find(line)?.groupValues?.get(1)
                rows += when {
                    id != null -> base.copy(videoId = id)
                    line.startsWith("http", true) -> base
                    else -> base.copy(
                        localPath = line,
                        title = base.title.ifBlank { File(line).nameWithoutExtension },
                    )
                }
                pending = null
            }
        }
    }
    return rows
}

/** RFC 4180 CSV with a header row; column names are matched loosely so most exporters work. */
internal fun parseCsvPlaylist(text: String): List<ImportRow> {
    val records = csvRecords(text)
    if (records.isEmpty()) return emptyList()
    val header = records.first().map { fold(it) }
    fun column(vararg names: String) = header.indexOfFirst { name -> names.any { name == it } }.takeIf { it >= 0 }
        ?: header.indexOfFirst { name -> names.any { name.contains(it) } }.takeIf { it >= 0 }
    val title = column("track name", "title", "song", "name", "sarki", "parca") ?: return emptyList()
    val artist = column("artist name(s)", "artist", "artists", "sanatci")
    val album = column("album name", "album")
    val duration = column("duration (ms)", "duration", "sure")
    val video = column("video id", "videoid", "url")
    return records.drop(1).map { cells ->
        fun cell(index: Int?) = index?.let { cells.getOrNull(it) }?.trim().orEmpty()
        val length = cell(duration)
        val ms = when {
            length.contains(':') -> length.split(':').fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0) } * 1000
            else -> length.toDoubleOrNull()?.let { if (header.getOrNull(duration ?: -1)?.contains("ms") == true || it > 10_000) it.toLong() else (it * 1000).toLong() } ?: 0
        }
        val id = cell(video).let { value -> VIDEO_ID.find(value)?.groupValues?.get(1) ?: value.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) } }
        ImportRow(cell(title), cell(artist).replace(";", ","), cell(album), ms, id)
    }
}

internal fun csvRecords(text: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    fun endCell() { cells += cell.toString(); cell.clear() }
    fun endRecord() { endCell(); if (cells.any { it.isNotBlank() }) records += cells.toList(); cells.clear() }
    val separator = if (text.lineSequence().firstOrNull()?.count { it == ';' } ?: 0 > (text.lineSequence().firstOrNull()?.count { it == ',' } ?: 0)) ';' else ','
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == separator -> endCell()
            !quoted && (c == '\n' || c == '\r') -> { if (c == '\r' && text.getOrNull(i + 1) == '\n') i++; endRecord() }
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || cells.isNotEmpty()) endRecord()
    return records
}

/**
 * How well a search result fits a row, 0–100. The title has to fit; the artist and the length move the score.
 * Versions the row did not ask for (live, remix, karaoke…) are pushed down.
 */
internal fun matchScore(row: ImportRow, candidate: Track): Int {
    val wanted = titleWords(row.title)
    val found = titleWords(candidate.title)
    if (wanted.isEmpty() || found.isEmpty()) return 0
    val shared = wanted.intersect(found.toSet()).size.toDouble()
    // Both directions count: "Aşk" is not "Ey Aşk", and "Unutamadım" is not "Unut".
    var score = if (wanted == found) 60 else (35 * (shared / wanted.size) * (shared / found.size)).toInt()
    if (row.artist.isNotBlank()) {
        val artists = artistNames(row.artist).map(::fold)
        val candidateArtists = fold(candidate.artists)
        score += if (artists.any { it.isNotEmpty() && it in candidateArtists }) 30 else -15
    } else score += 10
    if (row.durationMs > 0 && candidate.durationMs > 0) {
        val gap = abs(row.durationMs - candidate.durationMs)
        score += when { gap <= 3_000 -> 10; gap <= 10_000 -> 4; gap > 60_000 -> -20; else -> 0 }
    }
    val unwanted = listOf("live", "canli", "remix", "karaoke", "instrumental", "cover", "slowed", "sped", "8d", "akustik", "acoustic")
    val candidateText = fold(candidate.title)
    if (unwanted.any { it in candidateText.split(' ', '(', ')', '-', '[', ']') && it !in fold(row.title) }) score -= 15
    return score.coerceIn(0, 100)
}

/** Title words without the release labels that vary between services ("Remastered 2011", "Orijinal Versiyon"). */
internal fun titleWords(title: String): List<String> = fold(lyricTitle(title))
    .split(' ', '-', '(', ')', '[', ']', ',', '.', '/', '\'', '’', '!', '?', ':')
    .filter { it.isNotBlank() && it !in VERSION_WORDS && !Regex("(19|20)\\d\\d").matches(it) }

private val VERSION_WORDS = setOf(
    "remaster", "remastered", "version", "versiyon", "orijinal", "original", "edit", "radio", "single", "album",
    "official", "audio", "video", "mono", "stereo", "feat", "ft", "with", "deluxe", "mix",
)

/** Searches songs first and, when nothing fits, videos too; many Turkish songs only exist as uploads. */
internal suspend fun bestMatch(row: ImportRow): Track? {
    val query = listOf(row.artist.substringBefore(','), lyricTitle(row.title)).filter { it.isNotBlank() }.joinToString(" ")
    if (query.isBlank()) return null
    for (filter in listOf(com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG, com.metrolist.innertube.YouTube.SearchFilter.FILTER_VIDEO)) {
        val results = com.metrolist.innertube.YouTube.search(query, filter).getOrNull()?.items.orEmpty().mapNotNull { it.asTrack() }.take(8)
        results.map { it to matchScore(row, it) }.filter { it.second >= MATCH_THRESHOLD }.maxByOrNull { it.second }?.let { return it.first }
    }
    return null
}

internal const val MATCH_THRESHOLD = 55
