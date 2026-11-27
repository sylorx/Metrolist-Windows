package com.metrolist.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One year of listening, computed only from what this computer recorded. */
internal data class YearRecap(
    val year: Int,
    val totalMs: Long,
    val tracks: Int,
    val artists: Int,
    val topTracks: List<Pair<SavedTrack, Long>>,
    val topArtists: List<Pair<String, Long>>,
    val busiestDay: Pair<LocalDate, Long>?,
    val topMonth: Pair<Month, Long>?,
    val longestStreak: Int,
    val favoriteHour: Int?,
    val firstSong: HistoryEntry?,
    val newArtists: Int,
)

internal fun recapYears(sessions: List<ListeningSession>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
    sessions.filter { it.listenedMs > 0 }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).year }.distinct().sortedDescending()

internal fun yearRecap(
    sessions: List<ListeningSession>,
    history: List<HistoryEntry>,
    year: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): YearRecap {
    fun yearOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).year
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val inYear = sessions.filter { it.listenedMs > 0 && yearOf(it.startedAt) == year }
    val played = history.filter { yearOf(it.playedAt) == year }
    val byTrack = inYear.groupBy { it.track.id }.map { (_, list) -> list.first().track to list.sumOf { it.listenedMs } }.sortedByDescending { it.second }
    val byArtist = HashMap<String, Long>()
    val names = HashMap<String, String>()
    inYear.forEach { session ->
        artistNames(session.track.artists).forEach { name ->
            val key = artistKey(name)
            names.putIfAbsent(key, name)
            byArtist[key] = (byArtist[key] ?: 0) + session.listenedMs
        }
    }
    val byDay = inYear.groupBy { dayOf(it.startedAt) }.mapValues { (_, list) -> list.sumOf { it.listenedMs } }
    val days = byDay.keys.sorted()
    var streak = 0
    var best = 0
    days.forEachIndexed { at, day ->
        streak = if (at > 0 && days[at - 1].plusDays(1) == day) streak + 1 else 1
        best = maxOf(best, streak)
    }
    val earlier = (sessions.filter { yearOf(it.startedAt) < year }.map { it.track.artists } +
        history.filter { yearOf(it.playedAt) < year }.map { it.artists }).flatMap(::artistNames).map(::artistKey).toSet()
    return YearRecap(
        year = year,
        totalMs = inYear.sumOf { it.listenedMs },
        tracks = byTrack.size,
        artists = byArtist.size,
        topTracks = byTrack.take(5),
        topArtists = byArtist.entries.sortedByDescending { it.value }.take(5).map { names.getValue(it.key) to it.value },
        busiestDay = byDay.maxByOrNull { it.value }?.toPair(),
        topMonth = inYear.groupBy { dayOf(it.startedAt).month }.mapValues { (_, list) -> list.sumOf { it.listenedMs } }.maxByOrNull { it.value }?.toPair(),
        longestStreak = best,
        favoriteHour = played.groupingBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).hour }.eachCount().maxByOrNull { it.value }?.key,
        firstSong = played.minByOrNull { it.playedAt },
        newArtists = byArtist.keys.count { it !in earlier },
    )
}

private val turkish = Locale.forLanguageTag("tr")

private fun hours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 120) "$minutes dakika" else "%,d saat".format(turkish, minutes / 60)
}

private fun hourName(hour: Int) = when (hour) {
    in 5..11 -> "sabahları"
    in 12..16 -> "öğleden sonraları"
    in 17..21 -> "akşamları"
    else -> "gece yarısı"
}

private data class Slide(val colors: List<Color>, val content: @Composable () -> Unit)

/** Card by card, like a story: tap the right side for next, the left side for back; it advances by itself too. */
@Composable
internal fun YearRecapScreen(model: AppModel, requestedYear: Int?) {
    val sessions = model.store.sessions
    val years = remember(sessions) { recapYears(sessions) }
    var year by remember(requestedYear, years) { mutableStateOf(requestedYear?.takeIf { it in years } ?: years.firstOrNull()) }
    if (year == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yıllık özet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Henüz dinleme kaydı yok. Müzik dinledikçe yılının özeti burada oluşur.", color = Muted)
        }
        return
    }
    val recap = remember(year, sessions, model.history) { yearRecap(sessions, model.history, year!!) }
    val accent = Accent
    val slides = remember(recap, accent) { slidesFor(recap, accent) }
    var at by remember(recap) { mutableStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val motion = LocalMotion.current
    val progress = remember(at, recap) { Animatable(0f) }
    LaunchedEffect(at, recap) {
        if (at >= slides.lastIndex) { progress.snapTo(1f); return@LaunchedEffect }
        progress.animateTo(1f, tween(7_000, easing = LinearEasing))
        forward = true; at++
    }
    val background by animateColorAsState(slides[at].colors.first(), tween(600), label = "ozetZemin")
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${recap.year} özetin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            years.take(4).forEach { option -> FilterChip(option == year, { year = option }, label = { Text("$option") }, modifier = Modifier.padding(start = 6.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(background, slides[at].colors.last())))
                .pointerInput(slides.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                                if (change.position.x > size.width / 3) { if (at < slides.lastIndex) { forward = true; at++ } }
                                else if (at > 0) { forward = false; at-- }
                            }
                        }
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { index ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        val filled = when { index < at -> 1f; index == at -> progress.value; else -> 0f }
                        Box(Modifier.fillMaxHeight().fillMaxWidth(filled).background(Color.White))
                    }
                }
            }
            AnimatedContent(
                at, modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 56.dp),
                transitionSpec = {
                    if (!motion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(380)) + slideInHorizontally(tween(480, easing = FastOutSlowInEasing)) { (if (forward) 1 else -1) * it / 6 } + scaleIn(tween(480), 0.96f)) togetherWith
                        (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { (if (forward) -1 else 1) * it / 8 })
                },
                contentAlignment = Alignment.Center,
                label = "ozetKart",
            ) { index -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { slides[index].content() } }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (at > 0) { forward = false; at-- } }, enabled = at > 0) { Text("Geri") }
            TextButton(onClick = { if (at < slides.lastIndex) { forward = true; at++ } }, enabled = at < slides.lastIndex) { Text("İleri") }
            Spacer(Modifier.weight(1f))
            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { model.status = saveRecapImage(recap, accent) } }) { Text("Görsel olarak kaydet") }
        }
    }
}

@Composable
private fun Big(text: String, size: Int = 64) {
    Text(text, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = (size * 1.1).sp)
}

@Composable
private fun Small(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.82f), fontSize = 22.sp, textAlign = TextAlign.Center)
}

@Composable
private fun CountUp(target: Long, suffix: String) {
    val motion = LocalMotion.current
    val shown by animateIntAsState(target.toInt(), tween(if (motion) 1_400 else 0, easing = FastOutSlowInEasing), label = "say")
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    Big("%,d".format(turkish, if (started) shown else 0) + suffix, 84)
}

@Composable
private fun Ranked(rows: List<Pair<String, String>>) {
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEachIndexed { index, (title, detail) ->
            Row(Modifier.fillMaxWidth().appear(index), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun slidesFor(recap: YearRecap, accent: Color): List<Slide> {
    val palettes = listOf(
        listOf(Color(0xFF1B1446), accent.copy(alpha = 0.85f)),
        listOf(Color(0xFF3A0F2E), Color(0xFFFF4D6D)),
        listOf(Color(0xFF07343A), Color(0xFF4CC9F0)),
        listOf(Color(0xFF3B2405), Color(0xFFF5A524)),
        listOf(Color(0xFF12301A), Color(0xFF7CDE6A)),
        listOf(Color(0xFF2A1848), Color(0xFFA78BFA)),
        listOf(Color(0xFF3A1210), Color(0xFFFF7A59)),
    )
    var turn = 0
    fun colors() = palettes[turn++ % palettes.size]
    val slides = mutableListOf<Slide>()
    slides += Slide(colors()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Small("${recap.year} yılında bu bilgisayarda")
            CountUp(recap.totalMs / 60_000, " dakika")
            Small("müzik dinledin. Bu ${hours(recap.totalMs)} eder.")
        }
    }
    slides += Slide(colors()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CountUp(recap.tracks.toLong(), " şarkı")
            Small("${recap.artists} farklı sanatçıdan" + if (recap.newArtists > 0) "; ${recap.newArtists} tanesini bu yıl keşfettin." else ".")
        }
    }
    recap.topArtists.firstOrNull()?.let { (name, ms) ->
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Small("Yılın sanatçısı")
                Big(name, 72)
                Small("${hours(ms)} boyunca seninleydi.")
            }
        }
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Small("En çok dinlediğin sanatçılar")
                Ranked(recap.topArtists.map { it.first to hours(it.second) })
            }
        }
    }
    recap.topTracks.firstOrNull()?.let { (song, ms) ->
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Small("Yılın şarkısı")
                Big(song.title, 64)
                Small("${song.artists} · ${hours(ms)}")
            }
        }
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Small("En çok dinlediğin şarkılar")
                Ranked(recap.topTracks.map { it.first.title to "${it.first.artists} · ${hours(it.second)}" })
            }
        }
    }
    if (recap.topMonth != null || recap.favoriteHour != null || recap.longestStreak > 1) {
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                recap.favoriteHour?.let { Big("En çok ${hourName(it)}", 54) }
                recap.topMonth?.let { (month, ms) -> Small("En yoğun ayın ${month.getDisplayName(TextStyle.FULL, turkish)}: ${hours(ms)}") }
                recap.busiestDay?.let { (day, ms) -> Small("En uzun günün ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, turkish)}: ${hours(ms)}") }
                if (recap.longestStreak > 1) Small("Üst üste ${recap.longestStreak} gün müziksiz kalmadın.")
            }
        }
    }
    recap.firstSong?.let { first ->
        slides += Slide(colors()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Small("Yılı açan şarkı")
                Big(first.title, 56)
                Small(first.artists)
            }
        }
    }
    slides += Slide(colors()) { ShareCard(recap, accent, Modifier) }
    return slides
}

@Composable
private fun ShareCard(recap: YearRecap, accent: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Metrolist · ${recap.year}", color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Big(hours(recap.totalMs), 60)
        Small("${recap.tracks} şarkı · ${recap.artists} sanatçı")
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.widthIn(max = 300.dp)) {
                Text("Sanatçılar", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                recap.topArtists.take(5).forEachIndexed { i, (name, _) -> Text("${i + 1}. $name", color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(Modifier.widthIn(max = 300.dp)) {
                Text("Şarkılar", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                recap.topTracks.take(5).forEachIndexed { i, (song, _) -> Text("${i + 1}. ${song.title}", color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Box(Modifier.size(width = 80.dp, height = 6.dp).clip(CircleShape).background(accent))
    }
}

/** Draws the summary card off screen and saves it to the Pictures folder. Returns a message for the user. */
@OptIn(ExperimentalComposeUiApi::class)
internal suspend fun saveRecapImage(recap: YearRecap, accent: Color): String = try {
    val bytes = withContext(Dispatchers.Main) {
        val scene = ImageComposeScene(1080, 1350, Density(1.6f)) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B1446), accent))).padding(40.dp), contentAlignment = Alignment.Center) {
                ShareCard(recap, accent, Modifier)
            }
        }
        try { scene.render().encodeToData()?.bytes ?: error("Görsel üretilemedi") } finally { scene.close() }
    }
    val folder = File(System.getProperty("user.home"), "Pictures").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
    val target = File(folder, "Metrolist-${recap.year}-ozet.png")
    withContext(Dispatchers.IO) { target.writeBytes(bytes) }
    runCatching { ProcessBuilder("explorer.exe", "/select,${target.absolutePath}").start() }
    "Görsel kaydedildi: ${target.name}"
} catch (failure: Exception) {
    "Görsel kaydedilemedi: ${friendlyError(failure)}"
}
