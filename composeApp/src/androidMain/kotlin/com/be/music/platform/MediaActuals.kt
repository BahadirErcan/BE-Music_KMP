package com.be.music.platform

import android.content.ContentUris
import android.provider.MediaStore
import com.be.music.BeMusicApplication
import com.be.music.data.MusicRepository
import com.be.music.platform.ScannedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

actual object MusicScanner {
    actual suspend fun scanSongs(): List<ScannedSong> = withContext(Dispatchers.IO) {
        val context = BeMusicApplication.app()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val songsList = mutableListOf<ScannedSong>()
        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Song"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val duration = c.getLong(durationCol)
                    val path = c.getString(dataCol) ?: ""
                    val dateModified = c.getLong(dateCol)
                    val songUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    songsList.add(
                        ScannedSong(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = path,
                            dateModifiedSec = dateModified,
                            uriString = songUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("[MediaStoreScanner] Tarama hatası: $e")
        }
        songsList
    }

    actual suspend fun legacyPlaylistDirs(): List<String> {
        val context = BeMusicApplication.app()
        val dirs = mutableListOf<String>()
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            .let { dirs.add(File(it, "Playlist").absolutePath) }
        context.getExternalFilesDir(null)?.let { dirs.add(File(it, "Music/pl").absolutePath) }
        dirs.add(File(context.filesDir, "Music/pl").absolutePath)
        return dirs
    }
}

actual object SongIO {
    /** Dosyanın gerçek metadata'sını (jaudiotagger) yazar. */
    actual suspend fun writeMetadata(path: String, title: String, artist: String, album: String) {
        val f = File(path)
        if (!f.exists()) return
        val extension = f.extension.lowercase()
        if (extension !in listOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus")) return
        try {
            val audioFile = AudioFileIO.read(f)
            val tag = audioFile.tag ?: audioFile.createDefaultTag() ?: return
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, album)
            audioFile.commit()
        } catch (e: Exception) {
            println("[SongIO] metadata yazma hatası: $e")
            throw e
        }
    }

    actual suspend fun removeFromSystemCatalog(path: String) {
        val context = BeMusicApplication.app()
        try {
            val query = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA}=?",
                arrayOf(path),
                null
            )
            query?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    try {
                        context.contentResolver.delete(songUri, null, null)
                    } catch (e: SecurityException) {
                        println("[SongIO] MediaStore silme hatası ama devam ediyor: $e")
                    }
                }
            }
        } catch (e: Exception) {
            println("[SongIO] removeFromSystemCatalog hatası: $e")
        }
    }

    actual suspend fun renameForTitle(path: String, safeTitle: String): String? {
        val context = BeMusicApplication.app()
        val oldFile = File(path)
        val newFile = File(oldFile.parent, "$safeTitle.${oldFile.extension}")
        var renamedPath: String? = null

        // 1. Medya dosyasını MediaStore üzerinden güncelle (API 29+ DISPLAY_NAME+RELATIVE_PATH ile ad değişimi)
        try {
            val query = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA}=?",
                arrayOf(path),
                null
            )
            val songId = query?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            if (songId != null) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, safeTitle)
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newFile.name)
                    } else {
                        put(MediaStore.Audio.Media.DATA, newFile.absolutePath)
                    }
                }
                val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                val rows = context.contentResolver.update(songUri, values, null, null)
                if (rows > 0) {
                    // API 29+ provider dosyayı taşır
                    if (newFile.exists()) renamedPath = newFile.absolutePath
                    else if (oldFile.exists()) renamedPath = path // provider taşımadi, biz taşırız: FS.move home lazım
                    // orijinal dizindeki dosyaya da dokunalım (verilen isim değişmişse)
                    if (oldFile.exists() && !newFile.exists()) {
                        // provider yalnızca DB row rename; fiziksel dosya eski isimde kaldıysa biz rename ederiz
                        if (oldFile.renameTo(newFile)) renamedPath = newFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            println("[SongIO] MediaStore rename hatası: $e")
        }

        // 2. Fallback: fiziksel rename
        if (renamedPath == null && oldFile.exists() && !newFile.exists()) {
            if (oldFile.renameTo(newFile)) renamedPath = newFile.absolutePath
        }

        // MediaScanner ile yeniden tara
        renamedPath?.let { newPath ->
            try {
                android.media.MediaScannerConnection.scanFile(
                    BeMusicApplication.app(), arrayOf(newPath), null, null
                )
            } catch (_: Exception) {}
        }

        return renamedPath ?: path
    }

    actual suspend fun registerDownloaded(paths: List<String>): List<ScannedSong> {
        val context = BeMusicApplication.app()
        val out = mutableListOf<ScannedSong>()
        if (paths.isEmpty()) return out

        // 1. MediaStore'a kaydet
        try {
            android.media.MediaScannerConnection.scanFile(
                context, paths.toTypedArray(), null, null
            )
        } catch (_: Exception) {}

        // 2. Her path için MediaStore'dan bilgi çek
        for (p in paths) {
            try {
                val query = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DATE_MODIFIED
                    ),
                    "${MediaStore.Audio.Media.DATA}=?",
                    arrayOf(p),
                    null
                )
                query?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        val title = c.getString(1) ?: "Unknown"
                        val artist = c.getString(2) ?: "Unknown"
                        val album = c.getString(3) ?: "Unknown"
                        val duration = c.getLong(4)
                        val path2 = c.getString(5) ?: p
                        val dateModified = c.getLong(6)
                        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        out.add(
                            ScannedSong(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                filePath = path2,
                                dateModifiedSec = dateModified,
                                uriString = songUri.toString()
                            )
                        )
                    }
                }
                if (out.lastOrNull()?.filePath != p) {
                    // MediaStore hâlâ taranmadiysa fallback — dosya bilgisi ile ekle
                    val seed = File(p)
                    if (seed.exists() && !out.any { it.filePath == p }) {
                        out.add(
                            ScannedSong(
                                id = MusicRepository.stablePathId(p),
                                title = seed.nameWithoutExtension,
                                artist = "",
                                album = "",
                                durationMs = 0L,
                                filePath = p,
                                dateModifiedSec = seed.lastModified() / 1000L,
                                uriString = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                println("[SongIO] registerDownloaded okuma hatası: $p $e")
            }
        }
        return out
    }

    /** Albüm metadata'sını düzeltir (Regex ile aynı işlemler, yerine playlist name). */
    actual suspend fun fixAlbumMetadata(path: String, playlistName: String?) {
        try {
            val f = File(path)
            if (!f.exists()) return
            val audioFile = AudioFileIO.read(f)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()

            val currentAlbum = try {
                tag.getFirst(FieldKey.ALBUM)
            } catch (_: Exception) { "" }

            val albumToSet = when {
                !currentAlbum.isNullOrBlank() && currentAlbum != "Music" && currentAlbum != "Unknown" -> return
                !playlistName.isNullOrBlank() -> playlistName
                else -> "<Unknown>"
            }

            tag.setField(FieldKey.ALBUM, albumToSet)
            audioFile.commit()
            println("[SongIO] Album güncellendi: $albumToSet -> $path")
        } catch (e: Exception) {
            println("[SongIO] Album metadata hatası: $path $e")
        }
    }
}

actual fun openExternalUrl(url: String) {
    try {
        val ctx = BeMusicApplication.app()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        ctx.startActivity(intent)
    } catch (e: Exception) {
        println("[Android] URL açma hatası: $e")
    }
}
