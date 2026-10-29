package com.be.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.be.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.be.music.platform.EmbeddedArtLoader

object AlbumArtHelper {
    private val albumArtCache = LinkedHashMap<String, ImageBitmap>() // basit LRU (max 100)

    // Sadece Main thread'den okunur/yazılır (loadArt put'u Main'e taşır),
    // böylece JVM'e özgü @Synchronized kullanılmaz.
    fun getCachedArt(path: String): ImageBitmap? = albumArtCache[path]

    fun putCachedArt(path: String, bitmap: ImageBitmap) {
        if (albumArtCache.size >= 100) {
            val first = albumArtCache.keys.firstOrNull()
            if (first != null) albumArtCache.remove(first)
        }
        albumArtCache[path] = bitmap
    }

    suspend fun loadArt(path: String): ImageBitmap? {
        val cached = getCachedArt(path)
        if (cached != null) return cached
        val art = EmbeddedArtLoader.loadArt(path)
        if (art != null) {
            withContext(Dispatchers.Main.immediate) { putCachedArt(path, art) }
        }
        return art
    }
}

@Composable
fun SongAlbumArt(
    song: Song,
    modifier: Modifier = Modifier
) {
    var albumArtBitmap by remember(song.path) { mutableStateOf(AlbumArtHelper.getCachedArt(song.path)) }

    if (albumArtBitmap == null) {
        LaunchedEffect(song.path) {
            albumArtBitmap = AlbumArtHelper.loadArt(song.path)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        albumArtBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
