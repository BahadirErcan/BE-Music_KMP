@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.youtube

import kotlin.time.Clock

import com.be.music.data.AppDatabase
import com.be.music.data.Playlist
import com.be.music.data.Song
import com.be.music.premium.PremiumManager
import com.be.music.platform.FS
import com.be.music.platform.SongIO
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Platform-bağımsız indirme iş mantığı.
 * Android: DownloadWorker/BatchDownloadWorker/SpotifyDownloadWorker bunu sarmalar.
 * Masaüstü/iOS: doğrudan ararız.
 */
object YoutubeDownloadCoordinator {

    data class TrackResult(val success: Boolean, val label: String)

    data class BatchOutcome(
        val downloaded: Int,
        val total: Int,
        val failed: Int,
        val playlistName: String?,
        val playlistSongCount: Int
    )

    /** Tek video için: önce altyazı tespiti (dump-json), sonrasında indirme — eski Pourakos akışı. */
    suspend fun determineBestSubtitleLang(videoUrl: String): Pair<String?, Boolean> {
        return try {
            val jsonStr = YtDlp.dumpJson(videoUrl)
            var bestLang: String? = null
            var isAutomatic = false
            if (!jsonStr.isNullOrBlank()) {
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val subtitles = json["subtitles"]?.jsonObject
                val autoCaptions = json["automatic_captions"]?.jsonObject
                if (subtitles != null && subtitles.keys.any { it == "tr" || it.startsWith("tr-") }) {
                    bestLang = subtitles.keys.first { it == "tr" || it.startsWith("tr-") }
                    isAutomatic = false
                } else if (subtitles != null && subtitles.keys.any { it == "en" || it.startsWith("en-") }) {
                    bestLang = subtitles.keys.first { it == "en" || it.startsWith("en-") }
                    isAutomatic = false
                } else if (autoCaptions != null && autoCaptions.keys.any { it == "tr" || it.startsWith("tr-") }) {
                    bestLang = autoCaptions.keys.first { it == "tr" || it.startsWith("tr-") }
                    isAutomatic = true
                } else if (autoCaptions != null && autoCaptions.keys.any { it == "en" || it.startsWith("en-") }) {
                    bestLang = autoCaptions.keys.first { it == "en" || it.startsWith("en-") }
                    isAutomatic = true
                }
            }
            (bestLang to isAutomatic)
        } catch (e: Exception) {
            println("[DownloadCoordinator] Subtitle tespit hatası: $e")
            (null to false)
        }
    }

    /** Altyazı (vtt) dosyasını outDir'e indirir. */
    suspend fun downloadSubtitle(videoUrl: String, outDir: String, lang: String, isAutomatic: Boolean): Boolean {
        return try {
            val options = mutableListOf<Pair<String, String>>(
                "--user-agent" to defaultUserAgent(),
                "--extractor-args" to defaultExtractorArgs(),
                "--skip-download" to "",
                (if (isAutomatic) "--write-auto-sub" else "--write-subs") to "",
                "--sub-langs" to lang,
                "--sub-format" to "vtt",
                "-o" to "$outDir/%(title)s.%(ext)s"
            )
            options.add("" to videoUrl)
            YtDlp.run(options).isSuccess
        } catch (e: Exception) {
            println("[DownloadCoordinator] Subtitle indirme hatası: $e")
            false
        }
    }

    /** Ana indirme çağrısı (m4a veya mp4) — progress callback'i yüzde döner. */
    suspend fun downloadTrack(
        videoUrl: String,
        type: String,
        quality: String,
        outDir: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean {
        val options = mutableListOf<Pair<String, String>>(
            "--user-agent" to defaultUserAgent(),
            "--extractor-args" to defaultExtractorArgs(),
            "--no-check-certificate" to "",
            "--no-part" to "",
            "--embed-metadata" to "",
            "--embed-thumbnail" to "",
            "-o" to "$outDir/%(title)s.%(ext)s"
        )
        if (type == "m4a") {
        options.add("" to videoUrl)
            options.add("--extract-audio" to "")
            options.add("--audio-format" to "m4a")
        } else {
            options.add("-f" to DownloadFormatResolver.resolveVideoFormat(type, quality))
            options.add("--merge-output-format" to "mp4")
        }
        return try {
            YtDlp.run(options) { progress, eta, line ->
                onProgress(progress.toInt().coerceIn(0, 100))
            }.isSuccess
        } catch (e: Exception) {
            println("[DownloadCoordinator] İndirme hatası: $e")
            false
        }
    }

    /** İndirilenler dizinindeki yeni dosyaları yakalar (başlangıç isim setine göre). */
    fun newDownloadedFiles(downloadDir: String, initialNames: Set<String>): List<String> {
        return try {
            FS.listFiles(downloadDir).filter { FS.fileName(it) !in initialNames }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** ".<lang>.vtt" dosyalarını ".vtt" olarak yeniden adlandırır. */
    fun promoteSubtitleFiles(downloadDir: String, bestLang: String?) {
        if (bestLang == null) return
        try {
            for (path in FS.listFiles(downloadDir)) {
                val name = FS.fileName(path)
                if (name.endsWith(".$bestLang.vtt")) {
                    val baseName = name.substringBeforeLast(".$bestLang.vtt")
                    val targetFile = FS.join(downloadDir, "$baseName.vtt")
                    if (FS.exists(targetFile)) FS.delete(targetFile)
                    FS.move(path, targetFile)
                }
            }
        } catch (e: Exception) {
            println("[DownloadCoordinator] Altyazı promote hatası: $e")
        }
    }

    /** Son indirme sonrası metadata düzeltmeleri (media taraması platform tarafında). */
    suspend fun postProcessFiles(paths: List<String>, playlistBaseName: String?) = withContext(IoDispatcher) {
        for (path in paths) {
            val ext = FS.extension(path).lowercase()
            if (ext in listOf("mp3", "m4a", "aac", "mp4", "flac", "ogg", "opus")) {
                try {
                    SongIO.fixAlbumMetadata(path, playlistBaseName)
                } catch (e: Exception) {
                    println("[DownloadCoordinator] Album metadata hatası: $path $e")
                }
            }
        }
    }

    /** İndirilen dosyalardan otomatik çalma listesi kurar; (isim, şarkı sayısı) döner. */
    suspend fun createPlaylistFromDownloaded(
        paths: List<String>,
        baseName: String,
        numberPosition: String,
        multiSong: Boolean
    ): Pair<String, Int>? = withContext(IoDispatcher) {
        try {
            val registered = SongIO.registerDownloaded(paths)
            if (registered.isEmpty()) return@withContext null

            val db = AppDatabase.getDatabase()
            val songRows = registered.map { sc ->
                Song(
                    id = sc.id,
                    title = sc.title,
                    artistName = sc.artist,
                    albumName = sc.album,
                    duration = sc.durationMs,
                    path = sc.filePath,
                    uriString = sc.uriString ?: sc.filePath,
                    dateModified = sc.dateModifiedSec,
                    lyricsPath = lyricsFor(sc.filePath)
                )
            }
            db.songDao().insertSongs(songRows)

            val dao = db.playlistDao()
            val now = Clock.System.now().toEpochMilliseconds()
            if (multiSong) {
                val playlistName = findNextPlaylistName(dao, baseName, numberPosition)
                val songIds = songRows.map { it.id }
                val stamps = songIds.withIndex().associate { (i, id) -> id to now + i }
                val playlist = Playlist(name = playlistName, songIds = songIds, songAddedAt = stamps)
                dao.insertPlaylist(playlist)
                playlistName to songIds.size
            } else {
                var createdName: String? = null
                var count = 0
                for (song in songRows) {
                    val playlistName = findNextPlaylistName(dao, baseName, numberPosition)
                    val playlist = Playlist(name = playlistName, songIds = listOf(song.id), songAddedAt = mapOf(song.id to now))
                    dao.insertPlaylist(playlist)
                    createdName = playlistName
                    count = 1
                }
                createdName?.let { it to count }
            }
        } catch (e: Exception) {
            println("[DownloadCoordinator] Playlist oluşurma hatası: $e")
            null
        }
    }

    private fun lyricsFor(path: String): String? {
        val base = path.substringBeforeLast('.')
        for (ext in listOf("vtt", "srt", "lrc")) {
            val p = "$base.$ext"
            if (FS.exists(p)) return p
        }
        return null
    }

    suspend fun findNextPlaylistName(
        playlistDao: com.be.music.data.PlaylistDao,
        baseName: String,
        numberPosition: String
    ): String {
        val safeBase = baseName.ifBlank { "List" }
        val existingNames = playlistDao.getAllPlaylistNames()
        val numbers = existingNames.mapNotNull { name ->
            when (numberPosition) {
                "PREFIX" -> {
                    val parts = name.split("-", limit = 2)
                    if (parts.size == 2 && parts[1] == safeBase) parts[0].toIntOrNull() else null
                }
                else -> {
                    val parts = name.split("-", limit = 2)
                    if (parts.size == 2 && parts[0] == safeBase) parts[1].toIntOrNull() else null
                }
            }
        }
        val nextNumber = if (numbers.isEmpty()) 1 else numbers.max() + 1
        return when (numberPosition) {
            "PREFIX" -> "$nextNumber-$safeBase"
            else -> "$safeBase-$nextNumber"
        }
    }

    fun ensureDir(dir: String): String {
        if (!FS.exists(dir)) FS.makeDirs(dir)
        return dir
    }

    /**
     * Tek video indirir (altyazı mantığı dahil); çağıran bildirim/progress yönetir.
     * Bool = başarı.
     */
    suspend fun runSingleDownload(
        videoId: String,
        videoTitle_: String,
        type: String,
        quality: String,
        downloadLyrics: Boolean,
        outDir: String,
        onTrackProgress: (Int) -> Unit = {}
    ): Boolean = supervisorScope {
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        ensureDir(outDir)
        val downloadDir = outDir
        val initialNames = FS.listFiles(downloadDir).map { FS.fileName(it) }.toSet()

        val isPremium = PremiumManager.isPremiumStatic

        if (isPremium && !downloadLyrics) {
            val ok = downloadTrack(videoUrl, type, quality, downloadDir, onTrackProgress)
            if (ok) postProcessFiles(newFiles(downloadDir, initialNames), null)
            return@supervisorScope ok
        }

        if (isPremium && downloadLyrics) {
            // premium: indirme ile paralel altyazı
            var bestLang: String? = null
            val subtitleJob = launch(IoDispatcher) {
                val (lang, auto) = determineBestSubtitleLang(videoUrl)
                if (lang != null) {
                    bestLang = lang
                    downloadSubtitle(videoUrl, downloadDir, lang, auto)
                }
            }
            val ok = downloadTrack(videoUrl, type, quality, downloadDir, onTrackProgress)
            subtitleJob.join()
            promoteSubtitleFiles(downloadDir, bestLang)
            if (ok) postProcessFiles(newFiles(downloadDir, initialNames), null)
            return@supervisorScope ok
        }

        // Normal kullanıcı: sıralı
        var bestLang: String? = null
        var isAuto = false
        val (lang, auto) = determineBestSubtitleLang(videoUrl)
        if (lang != null) {
            bestLang = lang
            isAuto = auto
            downloadSubtitle(videoUrl, downloadDir, lang, auto)
        }
        val ok = downloadTrack(videoUrl, type, quality, downloadDir, onTrackProgress)
        promoteSubtitleFiles(downloadDir, bestLang)
        if (ok) postProcessFiles(newFiles(downloadDir, initialNames), null)
        return@supervisorScope ok
    }

    private fun newFiles(downloadDir: String, initialNames: Set<String>): List<String> {
        return try {
            FS.listFiles(downloadDir).filter { FS.fileName(it) !in initialNames }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Manuel liste (Batch) indirme — premium ise paralel + otomatik çalma listesi. */
    suspend fun runBatchDownload(
        trackLines: List<String>,
        quality: String,
        downloadLyrics: Boolean,
        parallelLimit: Int,
        downloadDir: String,
        onTrackProgress: (downloaded: Int, total: Int, currentTrack: String, progress: Int) -> Unit = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): BatchOutcome {
        ensureDir(downloadDir)
        val totalTracks = trackLines.size
        var downloadedCount = 0
        var failedCount = 0

        val db = AppDatabase.getDatabase()
        val settings = db.filterSettingsDao().getSettingsOnce()
        val playlistBaseName = settings?.autoPlaylistName ?: "List"
        val numberPosition = settings?.autoPlaylistNumberPosition ?: "SUFFIX"
        val multiSong = settings?.autoPlaylistMultiSong ?: true
        val isPremium = PremiumManager.isPremiumStatic

        val downloadedPaths = mutableListOf<String>()
        val counterLock = kotlinx.coroutines.sync.Mutex()

        suspend fun downloadOne(trackQuery: String, parallel: Boolean) {
            val currentLabel = trackQuery.trim()
            val searchResults = YoutubeSearchEngine.searchPage(currentLabel, null).first
            if (searchResults.isEmpty()) {
                println("[Batch] Sonuç bulunamadı: $currentLabel")
                counterLock.lock(); failedCount++; counterLock.unlock()
                return
            }
            val bestMatch = searchResults.first()
            val videoUrl = "https://www.youtube.com/watch?v=${bestMatch.id}"
            val initialNames = FS.listFiles(downloadDir).map { FS.fileName(it) }.toSet()

            val ok = downloadTrack(videoUrl, "m4a", quality, downloadDir) { p ->
                onTrackProgress(0, totalTracks, currentLabel, p)
            }
            if (ok) {
                val files = newFiles(downloadDir, initialNames)
                counterLock.lock(); downloadedPaths.addAll(files); counterLock.unlock()
                postProcessFiles(files, playlistBaseName)
                counterLock.lock(); downloadedCount++; counterLock.unlock()
                DownloadCounter.incrementDownloadCount()
            } else {
                counterLock.lock(); failedCount++; counterLock.unlock()
            }
        }

        if (isPremium && parallelLimit > 1) {
            val semaphore = Semaphore(parallelLimit.coerceIn(1, parallelLimit))
            supervisorScope {
                val jobs = trackLines.mapIndexed { _, trackQuery ->
                    async {
                        semaphore.withPermit {
                            try {
                                downloadOne(trackQuery, true)
                            } catch (e: Exception) {
                                println("[Batch] İndirme hatası: ${trackQuery.trim()} → $e")
                                counterLock.lock(); failedCount++; counterLock.unlock()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        } else {
            for ((_, trackQuery) in trackLines.withIndex()) {
                if (isCancelled()) break
                try {
                    downloadOne(trackQuery, false)
                    onTrackProgress(downloadedCount, totalTracks, trackQuery.trim(), (downloadedCount * 100) / totalTracks.coerceAtLeast(1))
                } catch (e: Exception) {
                    println("[Batch] İndirme hatası: ${trackQuery.trim()} → $e")
                    failedCount++
                }
            }
        }

        var playlistName: String? = null
        var playlistCount = 0
        if (isPremium && downloadedPaths.isNotEmpty()) {
            val result = createPlaylistFromDownloaded(downloadedPaths.toList(), playlistBaseName, numberPosition, multiSong)
            playlistName = result?.first
            playlistCount = result?.second ?: 0
        }

        return BatchOutcome(downloadedCount, totalTracks, failedCount, playlistName, playlistCount)
    }

    /** Spotify playlist indirme: playlist title → her parça YouTube'da aranır. */
    suspend fun runSpotifyPlaylistDownload(
        playlistUrl: String,
        quality: String,
        downloadLyrics: Boolean,
        downloadDir: String,
        onTrackProgress: (downloaded: Int, total: Int, currentTrack: String, progress: Int) -> Unit = { _, _, _, _ -> }
    ): BatchOutcome {
        ensureDir(downloadDir)
        val tracks = SpotifyTracks.parseUrl(playlistUrl)
        if (tracks.isEmpty()) throw Exception("no songs")
        val spPlaylistName = SpotifyTracks.fetchPlaylistName(playlistUrl)

        val totalTracks = tracks.size
        var downloadedCount = 0
        var failedCount = 0

        val db = AppDatabase.getDatabase()
        val settings = db.filterSettingsDao().getSettingsOnce()
        val playlistBaseName = spPlaylistName ?: settings?.autoPlaylistName ?: "Spotify"
        val numberPosition = settings?.autoPlaylistNumberPosition ?: "SUFFIX"
        val multiSong = settings?.autoPlaylistMultiSong ?: true
        val isPremium = PremiumManager.isPremiumStatic

        val downloadedPaths = mutableListOf<String>()

        for (track in tracks) {
            val searchQuery = "${track.artist} ${track.name}".trim()
            val currentTrackLabel = "${track.name} - ${track.artist}"
            onTrackProgress(downloadedCount, totalTracks, currentTrackLabel, 0)
            try {
                val searchResults = YoutubeSearchEngine.searchPage(searchQuery, null).first
                if (searchResults.isEmpty()) { failedCount++; continue }
                val bestMatch = searchResults.first()
                val videoUrl = "https://www.youtube.com/watch?v=${bestMatch.id}"
                val initialNames = FS.listFiles(downloadDir).map { FS.fileName(it) }.toSet()
                val ok = downloadTrack(videoUrl, "m4a", quality, downloadDir) { p ->
                    onTrackProgress(downloadedCount, totalTracks, currentTrackLabel, p)
                }
                if (ok) {
                    val files = newFiles(downloadDir, initialNames)
                    downloadedPaths.addAll(files)
                    postProcessFiles(files, playlistBaseName)
                    downloadedCount++
                    DownloadCounter.incrementDownloadCount()
                } else failedCount++
            } catch (e: Exception) {
                println("[SpotifyWorker] İndirme hatası: $searchQuery → $e")
                failedCount++
            }
            onTrackProgress(downloadedCount, totalTracks, currentTrackLabel, (downloadedCount * 100) / totalTracks.coerceAtLeast(1))
        }

        var createdPlaylist: String? = null
        var createdCount = 0
        if (isPremium && downloadedPaths.isNotEmpty()) {
            val result = createPlaylistFromDownloaded(downloadedPaths.toList(), playlistBaseName, numberPosition, multiSong)
            createdPlaylist = result?.first
            createdCount = result?.second ?: 0
        }

        return BatchOutcome(downloadedCount, totalTracks, failedCount, createdPlaylist, createdCount)
    }
}
