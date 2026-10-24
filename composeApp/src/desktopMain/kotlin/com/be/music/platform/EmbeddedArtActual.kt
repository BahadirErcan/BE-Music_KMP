package com.be.music.platform

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import androidx.compose.ui.graphics.toComposeImageBitmap

/** Masaüstünde gömülü kapak: jaudiotagger tag artwork + skia decode. */
actual object EmbeddedArtLoader {
    actual suspend fun loadArt(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(java.io.File(path))
            val artwork = audioFile.tag?.firstArtwork ?: return@withContext null
            val bytes = artwork.binaryData
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            println("[ArtLoader] Kapak yüklenemedi: $path $e")
            null
        }
    }
}
