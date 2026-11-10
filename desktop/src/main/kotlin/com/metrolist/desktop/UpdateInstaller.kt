package com.metrolist.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable internal data class UpdateResult(val version: String, val success: Boolean, val message: String)
@Serializable internal data class UpdatePlan(
    val parentPid: Long,
    val installer: String,
    val executable: String,
    val version: String,
    val sha256: String,
    val resultPath: String,
    val readyPath: String,
    val logPath: String,
)
internal interface UpdateInstaller {
    fun launch(packageFile: File, version: String, hash: String, directory: File)
}

/** The small helper outlives the JVM and runs the installer only after the app exits. */
internal class WindowsUpdateInstaller : UpdateInstaller {
    override fun launch(packageFile: File, version: String, hash: String, directory: File) {
        val command = System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse("")
        val executable = File(command)
        check(executable.isFile && executable.name.equals("Metrolist.exe", true)) {
            "İçeriden kurulum için paketlenmiş Metrolist.exe sürümünü aç"
        }
        val id = java.util.UUID.randomUUID().toString()
        val helper = File(directory, "apply-update.ps1")
        val planFile = File(directory, "plan-$id.json")
        val readyFile = File(directory, "ready-$id")
        val plan = UpdatePlan(ProcessHandle.current().pid(), packageFile.canonicalPath, executable.canonicalPath,
            version, hash, File(directory, "update-result.json").canonicalPath, readyFile.canonicalPath,
            File(directory, "installation.log").canonicalPath)
        val script = javaClass.getResourceAsStream("/updates/apply-update.ps1")?.use { it.readBytes() }
            ?: error("Güncelleme yardımcısı bulunamadı")
        atomicWrite(helper, String(script, Charsets.UTF_8), backup = false)
        atomicWrite(planFile, Json.encodeToString(plan), backup = false)
        val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
        val process = ProcessBuilder(powershell.absolutePath, "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", helper.absolutePath, "-PlanPath", planFile.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(directory, "helper.log")).start()
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!readyFile.exists() && process.isAlive && System.nanoTime() < deadline) Thread.sleep(50)
        if (!readyFile.exists()) {
            process.destroy()
            planFile.delete()
            error("Güncelleme yardımcısı başlayamadı; uygulama açık bırakıldı")
        }
    }
}
