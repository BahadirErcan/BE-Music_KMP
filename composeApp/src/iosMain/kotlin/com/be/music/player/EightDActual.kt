package com.be.music.player

actual object EightDProcessor {
    actual val supported: Boolean = false

    actual suspend fun create8DVersion(
        sourcePath: String,
        songTitle: String,
        songArtist: String,
        onProgress: (Int) -> Unit
    ): String? = null
}
