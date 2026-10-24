package com.be.music.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Embed albüm kapağı yükleyici (platforma özgü decoder). */
expect object EmbeddedArtLoader {
    /** Dosyanın içine gömülü kapak resmi (yoksa null) */
    suspend fun loadArt(path: String): ImageBitmap?
}
