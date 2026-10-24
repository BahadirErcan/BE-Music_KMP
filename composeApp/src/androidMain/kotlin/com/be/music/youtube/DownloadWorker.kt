package com.be.music.youtube

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/** Tek video indirme worker'ı — mantık ortak YoutubeDownloadCoordinator'da, bildirim burada. */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationId = 1001

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("video_id") ?: return Result.failure()
        val videoTitle = inputData.getString("video_title") ?: "video"
        val type = inputData.getString("type") ?: "m4a"
        val quality = inputData.getString("quality") ?: "720p"
        val downloadLyrics = inputData.getBoolean("lyrics", false)

        DownloadNotifications.ensureChannels(applicationContext)
        val channelId = DownloadNotifications.SINGLE_CHANNEL

        setForeground(
            DownloadNotifications.foregroundInfo(
                applicationContext, notificationId, channelId,
                videoTitle, applicationContext.getString(com.be.music.R.string.batch_preparing)
            )
        )

        val key = "${videoId}_$type"

        val downloadDir: File = if (type == "m4a") {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "BE-Music"
            )
        }
        if (!downloadDir.exists()) downloadDir.mkdirs()

        return try {
            val ok = YoutubeDownloadCoordinator.runSingleDownload(
                videoId = videoId,
                videoTitle_ = videoTitle,
                type = type,
                quality = quality,
                downloadLyrics = downloadLyrics,
                outDir = downloadDir.absolutePath,
                onTrackProgress = { progress ->
                    DownloadBus.tryEmit(
                        DownloadBus.DownloadEvent.SingleProgress(key, progress)
                    )
                    DownloadNotifications.updateProgress(
                        applicationContext, notificationId, channelId,
                        videoTitle, "%$progress", progress
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
            DownloadBus.tryEmit(DownloadBus.DownloadEvent.SingleFinished(key, ok))
            if (ok) Result.success(workDataOf("success" to true))
            else Result.failure(workDataOf("error" to applicationContext.getString(com.be.music.R.string.unknown_error)))
        } catch (e: Exception) {
            DownloadBus.tryEmit(DownloadBus.DownloadEvent.SingleFinished(key, false))
            Result.failure(workDataOf("error" to (e.message ?: "download error")))
        }
    }
}
