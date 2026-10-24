package com.be.music.player

import android.os.Build
import com.be.music.BeMusicApplication
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android actüel: mevcut EightDAudioProcessor (ffmpeg kabuk) sarmalayıcısı. */
actual object EightDProcessor {
    actual val supported: Boolean = true

    actual suspend fun create8DVersion(
        sourcePath: String,
        songTitle: String,
        songArtist: String,
        onProgress: (Int) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val out = EightDAudioProcessor.create8DVersion(
                context = BeMusicApplication.app(),
                inputPath = sourcePath,
                songTitle = songTitle,
                songArtist = songArtist,
                onProgress = onProgress
            )
            out.absolutePath
        } catch (e: Exception) {
            println("[EightDProcessor] 8D oluşturulamadı: $e")
            null
        }
    }
}
