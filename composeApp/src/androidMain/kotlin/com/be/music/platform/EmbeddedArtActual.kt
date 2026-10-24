package com.be.music.platform

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual object EmbeddedArtLoader {
    actual suspend fun loadArt(path: String): ImageBitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val artBytes = retriever.embeddedPicture
            artBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (e: Exception) {
            Log.e("AlbumArtLoader", "Error loading art: $path")
            null
        } finally {
            try { retriever?.release() } catch (ex: Exception) { }
        }
    }
}
