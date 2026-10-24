package com.be.music.platform

/** Şarkı için kapak görseli URI/path üretimi (platforma özgü kaynaklar). */
expect fun artworkUriForSong(songId: Long, songPath: String): String?
