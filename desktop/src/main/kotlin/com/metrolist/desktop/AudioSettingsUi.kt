package com.metrolist.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AudioStudio(model: AppModel) {
    val tools = model.libraryTools
    val audio = model.player.audio
    var devices by remember { mutableStateOf(audioDevices()) }
    var deviceMenu by remember { mutableStateOf(false) }
    StudioCard("Ses çıkışı") {
        Box {
            Button(onClick = { devices = audioDevices(); deviceMenu = true }) { Text(audio.device.ifBlank { "Windows varsayılan aygıtı" }) }
            DropdownMenu(deviceMenu, { deviceMenu = false }) {
                (listOf("") + devices).forEach { name ->
                    DropdownMenuItem(text = { Text(name.ifBlank { "Windows varsayılan aygıtı" }) }, onClick = {
                        tools.configureAudio(audio.copy(device = name)); deviceMenu = false
                    })
                }
            }
        }
        if (audio.device.isNotBlank() && audio.device !in devices) Text("Seçilen aygıt bağlı değil. Varsayılan çıkış kullanılacak.", color = Muted)
    }
    StudioCard("10 bant ekolayzır") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            equalizerPresets.forEach { (label, gains) ->
                FilterChip(audio.bands == gains, { tools.configureAudio(audio.copy(bands = gains)) }, label = { Text(label) })
            }
        }
        equalizerFrequencies.forEachIndexed { index, hz ->
            val saved = audio.bands.getOrElse(index) { 0f }
            var draft by remember(saved) { mutableStateOf(saved) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", color = Muted, modifier = Modifier.width(64.dp))
                Slider(draft, { draft = it }, valueRange = -12f..12f, steps = 23, modifier = Modifier.weight(1f),
                    onValueChangeFinished = { tools.configureAudio(audio.copy(bands = audio.bands.toMutableList().apply { this[index] = draft })) })
                Text("%+.0f dB".format(draft), color = Color.White, modifier = Modifier.width(64.dp))
            }
        }
        Text("Kazanç için ses payı ayrılır; yüksek ayarlarda taşma sınırlanır.", color = Muted)
    }
    StudioCard("Oynatma") {
        ToggleSetting("Ses seviyesini dengele", audio.normalize) { tools.configureAudio(audio.copy(normalize = it)) }
        ToggleSetting("Kesintisiz geçiş için sonraki parçayı hazırla", audio.gapless) { tools.configureAudio(audio.copy(gapless = it)) }
        ToggleSetting("Liste bitince benzer şarkılar ekle", tools.autoplay, tools::changeAutoplay)
        Text("Crossfade · ${audio.crossfadeSeconds} saniye", color = Color.White)
        var seconds by remember(audio.crossfadeSeconds) { mutableStateOf(audio.crossfadeSeconds.toFloat()) }
        Slider(seconds, { seconds = it }, valueRange = 0f..12f, steps = 11,
            onValueChangeFinished = { tools.configureAudio(audio.copy(crossfadeSeconds = seconds.toInt())) })
        Text("0 saniye crossfade’i kapatır. Geçiş, sonraki parça hazırlanabildiğinde uygulanır.", color = Muted)
    }
}

@Composable
private fun ToggleSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
internal fun SleepSettings(player: PlaybackController) {
    StudioCard("Uyku zamanlayıcısı") {
        Text(if (player.sleepAfterTrack) "Bu parçanın sonunda duracak" else player.sleepDeadline?.let { "Kalan: ${formatTime(player.sleepRemainingMs)}" } ?: "Zamanlayıcı kapalı", color = Color.White)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                TextButton(onClick = { player.setSleep(minutes) }) { Text("$minutes dk") }
            }
        }
        var custom by remember { mutableStateOf("20") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(3) }, label = { Text("Dakika") }, singleLine = true, modifier = Modifier.width(140.dp))
            TextButton(onClick = { player.setSleep(custom.toIntOrNull()) }, enabled = (custom.toIntOrNull() ?: 0) in 1..999) { Text("Başlat") }
        }
        Row {
            TextButton(onClick = { player.setSleep(afterTrack = true) }) { Text("Parça sonunda durdur") }
            TextButton(onClick = { player.setSleep() }) { Text("İptal et") }
        }
        Text("Süreli zamanlayıcı son 10 saniyede sesi azaltır. Ses ayarın korunur.", color = Muted)
    }
}

@Composable
internal fun DataSettings(model: AppModel) {
    val tools = model.libraryTools
    StudioCard("Yedekleme ve taşıma") {
        Text("Beğeniler, listeler, dinleme geçmişi, istatistikler ve görünüm/ses ayarları JSON yedeğine eklenir. Müzik dosyaları ve hesap çerezi eklenmez.", color = Muted)
        Row {
            Button(onClick = tools::exportBackup, enabled = !tools.fileBusy) { Text("Yedeği dışa aktar") }
            TextButton(onClick = tools::importBackup, enabled = !tools.fileBusy) { Text("Yedekten birleştir") }
        }
        Text("İçe aktarım mevcut listeleri silmeden birleştirir. Aynı kimlikteki mevcut liste korunur. Önceki durum ayrıca saklanır.", color = Muted)
    }
    StudioCard("Hesap eşitlemesi") {
        Text(if (tools.syncing) "Eşitleniyor…" else "${tools.pendingCount} işlem bekliyor", color = Color.White)
        tools.syncMessage?.let { Text(it, color = Muted) }
        Text("Hesap açıkken yapılan yeni liste ve beğeni değişiklikleri sırayla gönderilir. Başarısız işlemler sonraki deneme için saklanır. Yerel müzik dosyaları YouTube’a yüklenmez.", color = Muted)
        TextButton(onClick = tools::retrySync, enabled = !tools.syncing) { Text("Yeniden dene") }
    }
    StudioCard("Kayıt koruması") {
        Text("Ayarlar ve kitaplık atomik kaydedilir; önceki sürümleri .bak dosyalarında tutulur. Oturum çerezi bu Windows hesabına bağlı DPAPI ile korunur.", color = Muted)
    }
}
