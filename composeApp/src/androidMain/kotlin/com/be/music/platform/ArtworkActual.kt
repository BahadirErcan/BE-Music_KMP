package com.be.music.platform

import android.content.ContentUris
import android.provider.MediaStore
import java.io.File

actual fun artworkUriForSong(songId: Long, songPath: String): String? = try {
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId).toString()
} catch (e: Exception) {
    null
}
