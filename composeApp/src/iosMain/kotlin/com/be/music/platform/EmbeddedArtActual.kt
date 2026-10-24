package com.be.music.platform

/** Kapak görseli UI: AVFoundation metadata okuma sonra; şimdilik null (ikon fallback). */
actual object EmbeddedArtLoader {
    actual suspend fun loadArt(path: String): androidx.compose.ui.graphics.ImageBitmap? = null
}
