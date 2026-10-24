package com.be.music.platform

import com.be.music.PlatformDirs
import com.be.music.PlatformDirs.osName
import com.be.music.data.MusicRepository
import com.be.music.platform.ScannedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import java.io.File

val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus", "wav", "wma")

actual object MusicScanner {
    private var cachedDirs: List<String> = emptyList()

    fun updateScanDirs(dirs: List<String>) {
        cachedDirs = dirs
    }

    actual suspend fun legacyPlaylistDirs(): List<String> = emptyList()

    actual suspend fun scanSongs(): List<ScannedSong> = withContext(Dispatchers.IO) {
        val dirs = buildScanDirs()
        val out = LinkedHashMap<Long, ScannedSong>()
        for (dir in dirs) {
            walk(File(dir), out)
        }
        out.values.toList()
    }

    fun buildScanDirs(): List<String> {
        val dirs = mutableListOf<String>()
        val configured = kotlinx.coroutines.runBlocking {
            com.be.music.data.AppPreferences.desktopScanDirs()
        }
        if (configured.isNotEmpty()) {
            dirs.addAll(configured)
        } else {
            val music = PlatformDirs.defaultMusicDir
            if (music.exists()) dirs.add(music.absolutePath)
        }
        return dirs
    }

    fun scanDirectories(dirs: List<String>): List<ScannedSong> = run {
        val out = LinkedHashMap<Long, ScannedSong>()
        for (dir in dirs) walk(File(dir), out)
        out.values.toList()
    }

    private fun walk(dir: File, out: MutableMap<Long, ScannedSong>) {
        if (!dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                walk(child, out)
            } else {
                val ext = child.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS) {
                    val path = child.absolutePath
                    val id = MusicRepository.stablePathId(path)
                    val info = readMetadata(path)
                    out[id] = ScannedSong(
                        id = id,
                        title = info.title.ifBlank { child.nameWithoutExtension },
                        artist = info.artist,
                        album = info.album,
                        durationMs = info.durationMs,
                        filePath = path,
                        dateModifiedSec = child.lastModified() / 1000L,
                        uriString = null
                    )
                }
            }
        }
    }

    data class Meta(val title: String, val artist: String, val album: String, val durationMs: Long)

    private fun readMetadata(path: String): Meta {
        return try {
            val f = AudioFileIO.read(File(path))
            val tag = f.tag
            val header: AudioHeader = f.audioHeader
            val title = tag?.getFirst(FieldKey.TITLE) ?: ""
            val artist = tag?.getFirst(FieldKey.ARTIST) ?: ""
            val album = tag?.getFirst(FieldKey.ALBUM) ?: ""
            val durationSec = try { header.trackLength.toLong() * 1000L } catch (_: Exception) { 0L }
            Meta(title, artist, album, durationSec)
        } catch (e: Exception) {
            Meta("", "", "", 0L)
        }
    }
}

actual object SongIO {
    actual suspend fun writeMetadata(path: String, title: String, artist: String, album: String) {
        val f = File(path)
        if (!f.exists()) return
        try {
            val audioFile = AudioFileIO.read(f)
            val tag = audioFile.tag ?: audioFile.createDefaultTag() ?: return
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, album)
            audioFile.commit()
        } catch (e: Exception) {
            println("[SongIO] metadata yazma hatası: $e")
        }
    }

    actual suspend fun removeFromSystemCatalog(path: String) { /* masaüstünde sistem katalog yok */ }

    actual suspend fun renameForTitle(path: String, safeTitle: String): String? {
        val old = File(path)
        val nu = File(old.parentFile, "$safeTitle.${old.extension}")
        return if (old.renameTo(nu)) nu.absolutePath else path
    }

    actual suspend fun registerDownloaded(paths: List<String>): List<ScannedSong> {
        val out = mutableListOf<ScannedSong>()
        for (p in paths) {
            try {
                val f = File(p)
                if (!f.exists()) continue
                val audioFile = AudioFileIO.read(f)
                val tag = audioFile.tag
                val header = audioFile.audioHeader
                out.add(
                    ScannedSong(
                        id = MusicRepository.stablePathId(f.absolutePath),
                        title = runCatching { tag.getFirst(FieldKey.TITLE) }.getOrDefault(""),
                        artist = runCatching { tag.getFirst(FieldKey.ARTIST) }.getOrDefault(""),
                        album = runCatching { tag.getFirst(FieldKey.ALBUM) }.getOrDefault(""),
                        durationMs = try { header.trackLength.toLong() * 1000L } catch (_: Exception) { 0L },
                        filePath = f.absolutePath,
                        dateModifiedSec = f.lastModified() / 1000L,
                        uriString = null
                    )
                )
            } catch (e: Exception) {
                println("[SongIO] register error: $p $e")
            }
        }
        return out
    }

    /** Albüm metadata düzeltme: mevcut albüm boş/"Music"/"Unknown" ise playlist adıyla değiştir */
    actual suspend fun fixAlbumMetadata(path: String, playlistName: String?) {
        try {
            val f = File(path)
            if (!f.exists()) return
            val audioFile = AudioFileIO.read(f)
            val tag = audioFile.tag ?: audioFile.createDefaultTag() ?: return
            val currentAlbum = try { tag.getFirst(FieldKey.ALBUM) } catch (_: Exception) { "" }
            val albumToSet = when {
                !currentAlbum.isNullOrBlank() && currentAlbum != "Music" && currentAlbum != "Unknown" -> return
                !playlistName.isNullOrBlank() -> playlistName
                else -> "<Unknown>"
            }
            tag.setField(FieldKey.ALBUM, albumToSet)
            audioFile.commit()
        } catch (e: Exception) {
            println("[SongIO] Album metadata hatası: $path $e")
        }
    }
}

actual fun openExternalUrl(url: String) {
    try {
        val cmd = when (osName) {
            "windows" -> listOf("cmd", "/c", "start", url)
            "macos" -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        ProcessBuilder(cmd).start()
    } catch (e: Exception) {
        println("[Desktop] URL açılamadı: $e")
    }
}
