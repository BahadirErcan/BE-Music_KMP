package com.be.music.youtube

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.be.music.BeMusicApplication

actual object DownloadLauncher {
    private fun workManager(): WorkManager = WorkManager.getInstance(BeMusicApplication.app())

    actual fun single(videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean) {
        val compositeKey = "${videoId}_$type"
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "video_id" to videoId,
                    "video_title" to videoTitle,
                    "type" to type,
                    "quality" to quality,
                    "lyrics" to downloadLyrics
                )
            )
            .build()
        workManager().enqueueUniqueWork(
            "download_$compositeKey",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    actual fun spotify(url: String, quality: String, downloadLyrics: Boolean) {
        val request = OneTimeWorkRequestBuilder<SpotifyDownloadWorker>()
            .addTag("spotify_download")
            .setInputData(
                workDataOf(
                    "playlist_url" to url,
                    "quality" to quality,
                    "lyrics" to downloadLyrics
                )
            )
            .build()
        workManager().enqueue(request)
    }

    actual fun batch(trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int) {
        val request = OneTimeWorkRequestBuilder<BatchDownloadWorker>()
            .addTag("batch_download")
            .setInputData(
                workDataOf(
                    "track_list" to trackLines.joinToString("\n"),
                    "quality" to quality,
                    "lyrics" to downloadLyrics,
                    "parallel_limit" to parallelLimit
                )
            )
            .build()
        workManager().enqueue(request)
    }

    actual fun cancelSingle(compositeKey: String) {
        workManager().cancelUniqueWork("download_$compositeKey")
    }

    actual fun cancelSpotify() {
        workManager().cancelAllWorkByTag("spotify_download")
    }

    actual fun cancelBatch() {
        workManager().cancelAllWorkByTag("batch_download")
    }
}
