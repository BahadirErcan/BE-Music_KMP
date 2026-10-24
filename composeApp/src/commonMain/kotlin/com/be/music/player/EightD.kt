package com.be.music.player

import com.be.music.data.PlaylistDao

/** 8D ses dosyası üretimi (ffmpeg filtresi). iOS şu an desteklemiyor. */
expect object EightDProcessor {
    val supported: Boolean
    /** Üretilen 8D dosyasının yolu ya da null */
    suspend fun create8DVersion(
        sourcePath: String,
        songTitle: String,
        songArtist: String,
        onProgress: (Int) -> Unit
    ): String?
}
