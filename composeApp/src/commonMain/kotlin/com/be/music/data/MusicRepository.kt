@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.data

import com.be.music.platform.FS
import com.be.music.platform.MusicScanner
import com.be.music.platform.SongIO
import com.be.music.platform.FilePicker
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.math.abs

class MusicRepository(
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val filterSettingsDao: FilterSettingsDao,
    private val songOverrideDao: SongOverrideDao,
    val playHistoryManager: PlayHistoryManager
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allArtists: Flow<List<Artist>> = artistDao.getAllArtists()
    val allAlbums: Flow<List<Album>> = albumDao.getAllAlbums()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    val filterSettings: Flow<FilterSettings> = filterSettingsDao.getSettings()
        .map { it ?: FilterSettings() }

    val filteredSongs: Flow<List<Song>> = songDao.getAllSongs()
        .combine(filterSettings) { songs, settings ->
            songs.filter { song ->
                val durationSec = song.duration / 1000
                if (durationSec < settings.durationThresholdSec) {
                    return@filter false
                }
                when (settings.filterMode) {
                    "BLACKLIST" -> settings.folders.none { folder -> song.path.startsWith(folder) }
                    "WHITELIST" -> settings.folders.any { folder -> song.path.startsWith(folder) }
                    else -> true
                }
            }
        }

    suspend fun saveFilterSettings(settings: FilterSettings) {
        filterSettingsDao.saveSettings(settings)
    }

    suspend fun getSongsByIds(ids: List<Long>): List<Song> {
        return songDao.getSongsByIds(ids)
    }

    suspend fun scanLocalMusic(forceRefresh: Boolean = false) = withContext(IoDispatcher) {
        val existingSongs = songDao.getAllSongsOnce()
        if (!forceRefresh && existingSongs.isNotEmpty()) {
            return@withContext
        }

        val scanned = try {
            MusicScanner.scanSongs()
        } catch (e: Exception) {
            println("[MusicRepository] Tarama hatası: $e")
            emptyList()
        }
        if (scanned.isEmpty()) return@withContext

        val overrides = songOverrideDao.getAllOnce().associateBy { it.songId }

        val songsList = mutableListOf<Song>()
        val artistsSet = mutableSetOf<String>()
        val albumsMap = mutableMapOf<String, String>()

        for (scannedSong in scanned) {
            val override = overrides[scannedSong.id]
            val effectiveTitle = override?.title ?: scannedSong.title.ifBlank { "Unknown Song" }
            val effectiveArtist = override?.artist ?: scannedSong.artist.ifBlank { "Unknown Artist" }
            val effectiveAlbum = override?.album ?: scannedSong.album.ifBlank { "Unknown Album" }
            val effectivePath = override?.path ?: scannedSong.filePath
            val effectiveDateModified =
                if (override != null && override.dateModified > 0L) override.dateModified else scannedSong.dateModifiedSec

            songsList.add(
                Song(
                    id = scannedSong.id,
                    title = effectiveTitle,
                    artistName = effectiveArtist,
                    albumName = effectiveAlbum,
                    duration = scannedSong.durationMs,
                    path = effectivePath,
                    uriString = effectivePath,
                    dateModified = effectiveDateModified,
                    lyricsPath = findLyricsPathForSong(effectivePath)
                )
            )
            artistsSet.add(effectiveArtist)
            albumsMap[effectiveAlbum] = effectiveArtist
        }

        songDao.deleteAllSongs()
        artistDao.deleteAll()
        albumDao.deleteAll()
        artistDao.insertArtists(artistsSet.map { Artist(it) })
        albumDao.insertAlbums(albumsMap.map { Album(it.key, it.value) })
        songDao.insertSongs(songsList)
    }

    private fun findLyricsPathForSong(path: String): String? {
        val base = path.substringBeforeLast('.')
        val candidates = listOf("$base.vtt", "$base.srt", "$base.lrc")
        for (file in candidates) {
            if (FS.exists(file)) return file
        }
        return null
    }

    suspend fun updateSongLyricsPath(song: Song, lyricsPath: String?) = withContext(IoDispatcher) {
        songDao.updateSong(song.copy(lyricsPath = lyricsPath))
    }

    suspend fun createPlaylist(name: String, songIds: List<Long>): Playlist {
        val now = Clock.System.now().toEpochMilliseconds()
        val playlist = Playlist(
            name = name,
            songIds = songIds,
            songAddedAt = songIds.withIndex().associate { (i, id) -> id to now + i }
        )
        val id = playlistDao.insertPlaylist(playlist)
        val finalPlaylist = playlist.copy(id = id.toInt())
        try { exportPlaylistToJson(finalPlaylist) } catch (_: Exception) {}
        return finalPlaylist
    }

    fun withAddTimestamps(current: Playlist, newSongIds: List<Long>): Playlist {
        val stamps = current.songAddedAt.toMutableMap()
        val newIds = newSongIds.filter { !stamps.containsKey(it) }
        var next = Clock.System.now().toEpochMilliseconds()
        newIds.forEach { id -> stamps[id] = next++ }
        val keptStamps = stamps.filterKeys { it in newSongIds }.toMutableMap()
        return current.copy(songIds = newSongIds, songAddedAt = keptStamps)
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist)
        try { exportPlaylistToJson(playlist) } catch (_: Exception) {}
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
        playlist.filePath?.let { path ->
            try {
                if (FS.exists(path)) FS.delete(path)
            } catch (e: Exception) {
                println("[MusicRepository] Playlist dosyası silme hatası: $e")
            }
        }
    }

    private suspend fun exportPlaylistToJson(playlist: Playlist): String? = withContext(IoDispatcher) {
        try {
            val plDir = FS.playlistExportDir
            FS.makeDirs(plDir)

            val sanitizedName = playlist.name.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
            val plFile = FS.join(plDir, "playlist_${playlist.id}_$sanitizedName.json")

            val jsonString = Json.encodeToString(Playlist.serializer(), playlist)
            FS.writeText(plFile, jsonString)

            val updatedPlaylist = playlist.copy(filePath = plFile)
            playlistDao.updatePlaylist(updatedPlaylist)
            plFile
        } catch (e: Exception) {
            println("[MusicRepository] Playlist JSON yazma hatası: $e")
            null
        }
    }

    suspend fun exportPlaylistFilePath(playlist: Playlist): String? = exportPlaylistToJson(playlist)

    suspend fun importPlaylistsFromJson() = withContext(IoDispatcher) {
        try {
            val dirs = mutableListOf<String>()
            dirs.add(FS.playlistExportDir)
            dirs.addAll(legacyPlaylistImportDirs())

            for (plDir in dirs) {
                if (!FS.isDirectory(plDir)) continue
                val files = FS.listFiles(plDir).filter { it.endsWith(".json") }
                for (file in files) {
                    try {
                        val jsonString = FS.readText(file) ?: continue
                        val importedPlaylist = Json.decodeFromString(Playlist.serializer(), jsonString)
                        val existing = playlistDao.getPlaylistByName(importedPlaylist.name)
                        if (existing == null) {
                            playlistDao.insertPlaylist(
                                withAddTimestamps(
                                    importedPlaylist.copy(id = 0, filePath = file),
                                    importedPlaylist.songIds
                                )
                            )
                        } else {
                            playlistDao.updatePlaylist(
                                withAddTimestamps(
                                    existing.copy(
                                        songIds = importedPlaylist.songIds,
                                        filePath = file
                                    ),
                                    importedPlaylist.songIds
                                )
                            )
                        }
                    } catch (e: Exception) {
                        println("[MusicRepository] İçe aktarma hatası ${FS.fileName(file)}: $e")
                    }
                }
            }
        } catch (e: Exception) {
            println("[MusicRepository] İçe aktarma genel hata: $e")
        }
    }

    suspend fun importPlaylistFromFile(file: String): Pair<Boolean, String> = withContext(IoDispatcher) {
        try {
            val jsonString = FS.readText(file)
                ?: return@withContext Pair(false, "Playlist import failed: file not readable")
            val importedPlaylist = Json.decodeFromString(Playlist.serializer(), jsonString)

            val plDir = FS.playlistExportDir
            FS.makeDirs(plDir)
            val sanitizedName = importedPlaylist.name.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
            val targetFile = FS.join(plDir, "playlist_${Clock.System.now().toEpochMilliseconds()}_$sanitizedName.json")
            FS.writeText(targetFile, jsonString)

            val existing = playlistDao.getPlaylistByName(importedPlaylist.name)
            if (existing == null) {
                playlistDao.insertPlaylist(
                    withAddTimestamps(
                        importedPlaylist.copy(id = 0, filePath = targetFile),
                        importedPlaylist.songIds
                    )
                )
            } else {
                playlistDao.updatePlaylist(
                    withAddTimestamps(
                        existing.copy(
                            songIds = importedPlaylist.songIds,
                            filePath = targetFile
                        ),
                        importedPlaylist.songIds
                    )
                )
            }
            Pair(true, "Playlist imported successfully")
        } catch (e: Exception) {
            println("[MusicRepository] Playlist import hatası: $e")
            Pair(false, "Playlist import failed: ${e.message ?: "Unknown error"}")
        }
    }

    /** Android: eski Music/pl dizinleri; desktop & iOS boş */
    private suspend fun legacyPlaylistImportDirs(): List<String> = try {
        MusicScanner.legacyPlaylistDirs()
    } catch (e: Exception) { emptyList() }

    suspend fun updateSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String) = withContext(IoDispatcher) {
        val oldFile = song.path
        if (!FS.exists(oldFile)) {
            updateSongMetadataDbOnly(song, newTitle, newArtist, newAlbum)
            return@withContext
        }

        var currentPath = song.path
        var currentLyricsPath = song.lyricsPath
        val originalMtimeMillis = if (song.dateModified > 0L) song.dateModified * 1000L else FS.lastModifiedMs(oldFile)

        // 0. Dosya metadata'sını kalıcı yaz (jaudiotagger / AVAudio) — rename'den ÖNCE
        try {
            SongIO.writeMetadata(oldFile, newTitle, newArtist, newAlbum)
            if (originalMtimeMillis > 0L) FS.setLastModified(oldFile, originalMtimeMillis)
        } catch (e: Exception) {
            println("[MusicRepository] Metadata yazma hatası: $e")
        }

        val extension = FS.extension(oldFile)
        val sanitizedTitle = newTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val newFile = FS.join(FS.parentDir(oldFile) ?: "", "$sanitizedTitle.$extension")
        val shouldRename = newFile != oldFile && !FS.exists(newFile)

        // 1. Platform yeniden adlandırma + sistem katalogu (MediaStore / Finder) güncelleme
        if (shouldRename) {
            try {
                val renamed = SongIO.renameForTitle(oldFile, sanitizedTitle)
                if (renamed != null) currentPath = renamed
                // Eski tarih korunur
                if (renamed != null && FS.exists(renamed) && originalMtimeMillis > 0L) {
                    FS.setLastModified(renamed, originalMtimeMillis)
                }
            } catch (e: Exception) {
                println("[MusicRepository] Rename hatası: $e")
            }
        }

        // 2. Lyric dosyasını yeniden adlandır
        if (currentPath != oldFile) {
            song.lyricsPath?.let { oldLyrics ->
                val newBase = currentPath.substringBeforeLast('.')
                val lyricsExt = FS.extension(oldLyrics)
                val newLyrics = "$newBase.$lyricsExt"
                if (FS.exists(oldLyrics) && FS.move(oldLyrics, newLyrics)) {
                    currentLyricsPath = newLyrics
                }
            }
        }

        // 3. DB güncelle
        updateSongMetadataDbOnly(
            song,
            newTitle,
            newArtist,
            newAlbum,
            currentPath = currentPath,
            currentLyricsPath = currentLyricsPath,
            mtimeSec = originalMtimeMillis / 1000L
        )
    }

    private suspend fun updateSongMetadataDbOnly(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        currentPath: String = song.path,
        currentLyricsPath: String? = song.lyricsPath,
        mtimeSec: Long = 0L
    ) {
        try {
            val updatedSong = song.copy(
                title = newTitle,
                artistName = newArtist,
                albumName = newAlbum,
                path = currentPath,
                lyricsPath = currentLyricsPath
            )
            songDao.updateSong(updatedSong)
            songOverrideDao.upsert(
                SongOverride(
                    songId = song.id,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    path = currentPath,
                    dateModified = mtimeSec
                )
            )
        } catch (e: Exception) {
            println("[MusicRepository] Room güncelleme hatası: $e")
        }
    }

    suspend fun deleteSong(song: Song) = withContext(IoDispatcher) {
        try {
            // 1. Fiziksel dosyayı sil
            if (FS.exists(song.path)) FS.delete(song.path)
            // 2. Lyrics dosyasını sil
            song.lyricsPath?.let { lyricsPath ->
                if (FS.exists(lyricsPath)) FS.delete(lyricsPath)
            }
            // 3. Sistem katalogundan çıkar (Android MediaStore)
            try { SongIO.removeFromSystemCatalog(song.path) } catch (_: Exception) {}
            // 4. DB'den sil
            songDao.deleteSongs(listOf(song))
            // 5. Override'ı temizle
            songOverrideDao.deleteBySongId(song.id)
        } catch (e: Exception) {
            println("[MusicRepository] Şarkı silinemedi: $e")
        }
    }

    companion object {
        /** Path'ten kararlı ve çakışmayan platform bağımsız song id üretir (desktop/iOS). */
        fun stablePathId(path: String): Long {
            return abs(path.hashCode().toLong()).coerceAtLeast(1L)
        }
    }
}
