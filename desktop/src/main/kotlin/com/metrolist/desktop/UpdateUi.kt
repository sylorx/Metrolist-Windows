package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun bytesLabel(bytes: Long) = "%.1f MB".format(bytes / (1024.0 * 1024.0))

@Composable
internal fun UpdateSettings(model: AppModel) {
    val updates = model.updates
    StudioCard("Güncelleme") {
        Text("Yüklü sürüm ${AppVersion.NAME}", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yeni sürümleri otomatik kontrol et", color = Color.White, modifier = Modifier.weight(1f))
            Switch(updates.automatic, updates::changeAutomatic, enabled = !updates.installing)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Güncellemeleri arka planda indir", color = Color.White, modifier = Modifier.weight(1f))
            Switch(updates.backgroundDownload, updates::changeBackgroundDownload, enabled = !updates.installing)
        }
        Text("Yeni sürüm hazır olduğunda içeriden yeniden başlatarak kurabilirsin. Çalma sıran ve konumun kaydedilir. Windows kurulum için izin isteyebilir.", color = Muted)
        updates.resultMessage?.let { Text(it, color = Accent) }
        Text(updates.message, color = Muted)
        updates.notes?.takeIf { updates.available != null }?.let { Text(it, color = Muted) }
        if (updates.downloading) {
            LinearProgressIndicator(progress = { updates.progress }, modifier = Modifier.fillMaxWidth())
            Text("${bytesLabel(updates.receivedBytes)} / ${bytesLabel(updates.totalBytes)} · %${(updates.progress * 100).toInt()}", color = Muted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = updates::requestCheck, enabled = !updates.checking && !updates.downloading && !updates.installing) {
                Text(if (updates.checking) "Kontrol ediliyor…" else "Güncellemeleri kontrol et")
            }
            if (updates.canDownload) {
                Button(onClick = model::installUpdate, enabled = !updates.checking && !updates.downloading && !updates.installing) {
                    Text(if (updates.installing) "Hazırlanıyor…" else if (updates.ready) "Yeniden başlat ve güncelle" else "Güncellemeyi indir")
                }
            }
            if (updates.downloading) TextButton(onClick = updates::cancelDownload) { Text("İndirmeyi iptal et") }
        }
    }
}

@Composable
internal fun UpdateNotice(model: AppModel) {
    val updates = model.updates
    val pending = updates.pending ?: return
    Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Metrolist $pending · ${updates.message}", color = Color.White, fontWeight = FontWeight.Medium)
            if (updates.downloading) {
                Text("%${(updates.progress * 100).toInt()}", color = Accent)
                TextButton(onClick = updates::cancelDownload) { Text("İptal") }
            } else if (updates.canDownload) {
                Button(onClick = model::installUpdate, enabled = !updates.installing && !updates.checking) {
                    Text(if (updates.installing) "Hazırlanıyor…" else if (updates.ready) "Yeniden başlat ve güncelle" else "İndir")
                }
            }
            TextButton(onClick = updates::dismiss, enabled = !updates.installing) { Text("Sonra") }
        }
        if (updates.downloading) LinearProgressIndicator(progress = { updates.progress }, modifier = Modifier.fillMaxWidth())
    }
}
