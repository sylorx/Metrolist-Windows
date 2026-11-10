package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ListeningSummary(val totalMs: Long, val tracks: Int, val artists: List<Pair<String, Long>>, val daily: List<Pair<LocalDate, Long>>)
internal fun listeningSummary(sessions: List<ListeningSession>, days: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ListeningSummary {
    val first = today.minusDays(days.toLong() - 1)
    val since = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val relevant = sessions.filter { it.startedAt < until && it.startedAt + it.listenedMs > since }
    fun contribution(session: ListeningSession, start: Long, end: Long) =
        (minOf(session.startedAt + session.listenedMs, end) - maxOf(session.startedAt, start)).coerceAtLeast(0)
    val daily = (0 until days).map { offset ->
        val day = first.plusDays(offset.toLong())
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        day to relevant.sumOf { contribution(it, start, end) }
    }
    val artists = relevant.groupBy { it.track.artists }.map { (name, entries) -> name to entries.sumOf { contribution(it, since, until) } }
        .sortedByDescending { it.second }.take(10)
    return ListeningSummary(daily.sumOf { it.second }, relevant.map { it.track.id }.distinct().size, artists, daily)
}

@Composable
internal fun InsightsScreen(model: AppModel) {
    var days by remember { mutableStateOf(7) }
    var sessions by remember { mutableStateOf(model.store.sessions) }
    LaunchedEffect(Unit) { while (true) { sessions = model.store.sessions; delay(5000) } }
    val summary = remember(days, sessions) { listeningSummary(sessions, days) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Dinleme istatistikleri", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "Bu hafta", 30 to "Son 30 gün", 365 to "Son yıl").forEach { (period, label) ->
                FilterChip(days == period, { days = period }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column { Text("${summary.totalMs / 60_000} dk", style = MaterialTheme.typography.headlineLarge, color = Accent); Text("Dinleme süresi", color = Muted) }
            Column { Text("${summary.tracks}", style = MaterialTheme.typography.headlineLarge, color = Accent); Text("Farklı parça", color = Muted) }
            Column { Text("${sessions.sumOf { it.listenedMs } / 3_600_000} sa", style = MaterialTheme.typography.headlineLarge, color = Accent); Text("Tüm zamanlar", color = Muted) }
        }
        if (summary.totalMs == 0L) Text("Müzik dinledikçe gerçek dinleme süren burada birikir. Duraklatılan ve ileri sarılan süreler sayılmaz.", color = Muted)
        StudioCard("Günlük dinleme") {
            val daily = if (days > 30) summary.daily.chunked(7).map { group -> group.first().first to group.sumOf { it.second } } else summary.daily
            val peak = (daily.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                daily.forEach { (_, duration) -> Box(Modifier.weight(1f).height((140f * duration / peak).coerceAtLeast(2f).dp).background(Accent)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val format = DateTimeFormatter.ofPattern("dd MMM")
                Text(summary.daily.first().first.format(format), color = Muted)
                Text(summary.daily.last().first.format(format), color = Muted)
            }
            if (days > 30) Text("Her çubuk bir haftayı gösterir.", color = Muted)
        }
        StudioCard("En çok dinlenen sanatçılar") {
            summary.artists.forEachIndexed { index, (name, duration) ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${index + 1}. $name", color = Color.White, modifier = Modifier.weight(1f))
                    Text("${duration / 60_000} dk", color = Muted)
                }
                LinearProgressIndicator(progress = { (duration.toFloat() / summary.artists.first().second.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Text("Süre ölçümü bu sürümle başlar; eski geçmiş kayıtlarından dinleme süresi tahmin edilmez.", color = Muted)
    }
}
