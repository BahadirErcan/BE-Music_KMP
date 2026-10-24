package com.be.music.youtube

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/** Manuel liste (batch) indirme worker'ı — iş mantığı ortak koordinatörde. */
class BatchDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationId = 1101

    override suspend fun doWork(): Result {
        val trackList = inputData.getString("track_list") ?: return Result.failure(workDataOf("error" to applicationContext.getString(com.be.music.R.string.batch_list_empty)))
        val quality = inputData.getString("quality") ?: "m4a"
        val downloadLyrics = inputData.getBoolean("lyrics", false)
        val parallelLimit = inputData.getInt("parallel_limit", 3)

        val trackLines = trackList.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (trackLines.isEmpty()) {
            return Result.failure(workDataOf("error" to applicationContext.getString(com.be.music.R.string.batch_list_empty)))
        }

        DownloadNotifications.ensureChannels(applicationContext)
        val channelId = DownloadNotifications.BATCH_CHANNEL
        setForeground(
            DownloadNotifications.foregroundInfo(
                applicationContext, notificationId, channelId,
                applicationContext.getString(com.be.music.R.string.download_list_title),
                applicationContext.getString(com.be.music.R.string.batch_preparing)
            )
        )

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        return try {
            val outcome = YoutubeDownloadCoordinator.runBatchDownload(
                trackLines = trackLines,
                quality = quality,
                downloadLyrics = downloadLyrics,
                parallelLimit = parallelLimit,
                downloadDir = downloadDir.absolutePath,
                onTrackProgress = { downloaded, total, currentTrack, progress ->
                    DownloadBus.tryEmit(
                        DownloadBus.DownloadEvent.BatchProgress("batch", downloaded, total, currentTrack, progress)
                    )
                    DownloadNotifications.updateProgress(
                        applicationContext, notificationId, channelId,
                        applicationContext.getString(com.be.music.R.string.download_list_title),
                        "$downloaded/$total | $currentTrack", progress
                    )
                }
            )
            // İndirilen dosyaları sistem kataloğuna kaydet
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
                    "failed" to outcome.failed,
                    "playlist_name" to (outcome.playlistName ?: ""),
                    "playlist_songs" to outcome.playlistSongCount
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: applicationContext.getString(com.be.music.R.string.unknown_error))))
        }
    }
}
