package com.be.music.platform

import com.be.music.data.MusicRepository
import com.be.music.data.ScannedSong
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Şimdilik Documents dizinini gezer; metadata'sız (dosya adı = başlık). */
actual object MusicScanner {
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

    actual suspend fun scanSongs(): List<ScannedSong> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ScannedSong>()
        walk(docsDir(), out)
        out
    }

    actual suspend fun legacyPlaylistDirs(): List<String> = emptyList()

    private fun walk(dir: String, out: MutableList<ScannedSong>) {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dir, null) as? List<*> ?: return
        for (child in contents) {
            val name = child as? String ?: continue
            val path = joinPaths(dir, name)
            if (isDirectoryPath(path)) {
                walk(path, out)
            } else if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                out.add(toScannedSong(path))
            }
        }
    }

    private fun isDirectoryPath(path: String): Boolean {
        return kotlinx.cinterop.memScoped {
            val isDir = kotlinx.cinterop.alloc<kotlinx.cinterop.BooleanVar>()
            NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
            isDir.value
        }
    }

    private fun toScannedSong(path: String): ScannedSong {
        val base = FS.baseName(path)
        val artistTitle = base.split(" - ", limit = 2)
        val title = artistTitle.getOrNull(1) ?: base
        val artist = artistTitle.getOrNull(0) ?: ""
        return ScannedSong(
            id = MusicRepository.stablePathId(path),
            title = title,
            artist = artist,
            album = "",
            durationMs = 0L,
            filePath = path,
            dateModifiedSec = FS.lastModifiedMs(path) / 1000L,
            uriString = null
        )
    }

    private fun docsDir(): String {
        val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(url).path
    }

    private fun joinPaths(dir: String, child: String): String =
        if (dir.endsWith("/")) dir + child else dir + "/" + child
}

/** iOS SongIO: metadata kütüphanesi daha sonra (AVFoundation) eklenir; şimdilik şarkı ID tabanlı işlemler pas geçilir. */
actual object SongIO {
    actual suspend fun writeMetadata(path: String, title: String, artist: String, album: String) {
        // TODO: AVFoundation metadata (kullanıcı Swift kodu geldikten sonra)
    }

    actual suspend fun removeFromSystemCatalog(path: String) {}

    actual suspend fun renameForTitle(path: String, safeTitle: String): String? {
        val old = FS.baseName(path)
        val ext = FS.extension(path)
        val parent = FS.parentDir(path) ?: return path
        val newPath = FS.join(parent, "$safeTitle.$ext")
        return if (FS.move(path, newPath)) newPath else path
    }

    actual suspend fun registerDownloaded(paths: List<String>): List<ScannedSong> {
        return paths.map { path ->
            val base = FS.baseName(path)
            val artistTitle = base.split(" - ", limit = 2)
            ScannedSong(
                id = MusicRepository.stablePathId(path),
                title = artistTitle.getOrNull(1) ?: base,
                artist = artistTitle.getOrNull(0) ?: "",
                album = "",
                durationMs = 0L,
                filePath = path,
                dateModifiedSec = FS.lastModifiedMs(path) / 1000L,
                uriString = null
            )
        }
    }

    actual suspend fun fixAlbumMetadata(path: String, playlistName: String?) {
        // TODO: AVFoundation metadata
    }
}

actual fun openExternalUrl(url: String) {
    ToastBus.show(url) // TODO: UIApplication.shared.openURL (Swift köprüsünden sonra)
}
