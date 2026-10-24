package com.be.music.youtube

import com.be.music.platform.FS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Masaüstü DownloadLauncher: WorkManager yok — doğrudan koordinatörün
 * coroutine'lerini çalıştırır ve olayları DownloadBus'a yayınlar.
 */
actual object DownloadLauncher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var singleJob: Job? = null
    private var spotifyJob: Job? = null
    private var batchJob: Job? = null

    /** İndirme klasörü: ~/Music/BE-Music (m4a ve mp4 aynı yerde) */
    private fun downloadDir(): String {
        val dir = File(FS.musicExportDir)
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    actual fun single(videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean) {
        val compositeKey = "${videoId}_$type"
        singleJob = scope.launch {
            try {
                val ok = YoutubeDownloadCoordinator.runSingleDownload(
                    videoId = videoId,
                    videoTitle_ = videoTitle,
                    type = type,
                    quality = quality,
                    downloadLyrics = downloadLyrics,
                    outDir = downloadDir(),
                    onTrackProgress = { progress ->
                        DownloadBus.tryEmit(DownloadBus.DownloadEvent.SingleProgress(compositeKey, progress))
                    }
                )
                DownloadBus.tryEmit(DownloadBus.DownloadEvent.SingleFinished(compositeKey, ok))
            } catch (e: Exception) {
                println("[DownloadLauncher] single hatası: $e")
                DownloadBus.tryEmit(DownloadBus.DownloadEvent.SingleFinished(compositeKey, false))
            }
        }
    }

    actual fun spotify(url: String, quality: String, downloadLyrics: Boolean) {
        spotifyJob = scope.launch {
            try {
                val outcome = YoutubeDownloadCoordinator.runSpotifyPlaylistDownload(
                    playlistUrl = url,
                    quality = quality,
                    downloadLyrics = downloadLyrics,
                    downloadDir = downloadDir(),
                    onTrackProgress = { downloaded, total, currentTrack, progress ->
                        DownloadBus.tryEmit(
                            DownloadBus.DownloadEvent.BatchProgress("spotify", downloaded, total, currentTrack, progress)
                        )
                    }
                )
                DownloadBus.tryEmit(
                    DownloadBus.DownloadEvent.BatchFinished("spotify", outcome.failed < outcome.total, outcome.downloaded, outcome.total, outcome.failed, null)
                )
            } catch (e: Exception) {
                println("[DownloadLauncher] spotify hatası: $e")
                DownloadBus.tryEmit(DownloadBus.DownloadEvent.BatchFinished("spotify", false, 0, 0, 0, e.message))
            }
        }
    }

    actual fun batch(trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int) {
        batchJob = scope.launch {
            try {
                val outcome = YoutubeDownloadCoordinator.runBatchDownload(
                    trackLines = trackLines,
                    quality = quality,
                    downloadLyrics = downloadLyrics,
                    parallelLimit = parallelLimit,
                    downloadDir = downloadDir(),
                    onTrackProgress = { downloaded, total, currentTrack, progress ->
                        DownloadBus.tryEmit(
                            DownloadBus.DownloadEvent.BatchProgress("batch", downloaded, total, currentTrack, progress)
                        )
                    }
                )
                DownloadBus.tryEmit(
                    DownloadBus.DownloadEvent.BatchFinished("batch", outcome.failed < outcome.total, outcome.downloaded, outcome.total, outcome.failed, null)
                )
            } catch (e: Exception) {
                println("[DownloadLauncher] batch hatası: $e")
                DownloadBus.tryEmit(DownloadBus.DownloadEvent.BatchFinished("batch", false, 0, 0, 0, e.message))
            }
        }
    }

    actual fun cancelSingle(compositeKey: String) {
        singleJob?.cancel()
        singleJob = null
        YtDlp.cancelAll()
    }

    actual fun cancelSpotify() {
        spotifyJob?.cancel()
        spotifyJob = null
        YtDlp.cancelAll()
    }

    actual fun cancelBatch() {
        batchJob?.cancel()
        batchJob = null
        YtDlp.cancelAll()
    }
}
