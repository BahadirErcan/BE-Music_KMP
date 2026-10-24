package com.be.music.player

import com.be.music.desktop.audio.DesktopAudioEngine
import com.be.music.platform.FS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 8D sürüm: ffmpeg apulsator filtresi (head-related rotation yerine pan modülasyonu). */
actual object EightDProcessor {
    actual val supported: Boolean get() = false

    actual suspend fun create8DVersion(
        sourcePath: String,
        songTitle: String,
        songArtist: String,
        onProgress: (Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val outDir = FS.musicExportDir
            FS.makeDirs(outDir)
            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val outFile = File(outDir, "8D_$safeTitle.m4a")

            val proc = ProcessBuilder(
                "ffmpeg", "-y",
                "-i", File(sourcePath).absolutePath,
                "-af", "apulsator=hz=0.125:mode=sine:width=1.4:amount=1",
                "-c:a", "aac",
                outFile.absolutePath
            ).redirectErrorStream(true).start()

            proc.inputStream.bufferedReader().forEachLine { line ->
                if (line.contains("time=")) {
                    val t = line.substringAfter("time=").substringBefore(" ")
                    val sec = t.hourSecToMs()
                    if (sec > 0) {
                        val total = DesktopAudioEngine.ffprobeDurationMs(File(sourcePath)) ?: 0L
                        if (total > 0) onProgress(((sec * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            proc.waitFor()
            if (outFile.exists() && outFile.length() > 0) outFile.absolutePath else null
        } catch (e: Exception) {
            println("[EightD] 8D üretim hatası: $e")
            null
        }
    }
}

private fun String.hourSecToMs(): Long = try {
    val parts = split(":")
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0
    val s = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
    ((h * 3600 + m * 60 + s) * 1000).toLong()
} catch (e: Exception) { 0L }
