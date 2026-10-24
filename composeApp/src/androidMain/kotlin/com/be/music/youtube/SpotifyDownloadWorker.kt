package com.be.music.youtube

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/** Spotify çalma listesi indirme worker'ı — iş mantığı ortak koordinatörde. */
class SpotifyDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationId = 1201

    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString("playlist_url")
            ?: return Result.failure(workDataOf("error" to applicationContext.getString(com.be.music.R.string.playlist_url_not_found)))
        val quality = inputData.getString("quality") ?: "m4a"
        val downloadLyrics = inputData.getBoolean("lyrics", false)

        DownloadNotifications.ensureChannels(applicationContext)
        val channelId = DownloadNotifications.SPOTIFY_CHANNEL
        setForeground(
            DownloadNotifications.foregroundInfo(
                applicationContext, notificationId, channelId,
                applicationContext.getString(com.be.music.R.string.download_list_title),
                applicationContext.getString(com.be.music.R.string.spotify_fetching_playlist)
            )
        )

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        return try {
            val outcome = YoutubeDownloadCoordinator.runSpotifyPlaylistDownload(
                playlistUrl = playlistUrl,
                quality = quality,
                downloadLyrics = downloadLyrics,
                downloadDir = downloadDir.absolutePath,
                onTrackProgress = { downloaded, total, currentTrack, progress ->
                    DownloadBus.tryEmit(
                        DownloadBus.DownloadEvent.BatchProgress("spotify", downloaded, total, currentTrack, progress)
                    )
                    DownloadNotifications.updateProgress(
                        applicationContext, notificationId, channelId,
                        applicationContext.getString(com.be.music.R.string.download_list_title),
                        "$downloaded/$total | $currentTrack", progress
                    )
                }
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                android.media.MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(downloadDir.absolutePath),
                    null, null
                )
            }
            DownloadNotifications.showFinished(
                applicationContext, notificationId, channelId,
                applicationContext.getString(com.be.music.R.string.download_list_title),
                if (outcome.failed > 0)
                    applicationContext.getString(com.be.music.R.string.download_progress_with_fail, outcome.downloaded, outcome.total, outcome.failed)
                else
                    applicationContext.getString(com.be.music.R.string.download_progress_success, outcome.downloaded, outcome.total)
            )
            Result.success(
                workDataOf(
                    "downloaded" to outcome.downloaded,
                    "total" to outcome.total,
                    "failed" to outcome.failed
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: applicationContext.getString(com.be.music.R.string.unknown_error))))
        }
    }
}
