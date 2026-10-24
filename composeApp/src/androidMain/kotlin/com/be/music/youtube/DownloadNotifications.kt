package com.be.music.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.be.music.R

/** İndirme bildirim yardımcıları (3 worker için ortak). */
object DownloadNotifications {

    const val SINGLE_CHANNEL = "download_channel"
    const val SPOTIFY_CHANNEL = "spotify_download_channel"
    const val BATCH_CHANNEL = "batch_download_channel"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(SINGLE_CHANNEL, "BE-Music İndirme", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(SPOTIFY_CHANNEL, "Spotify Listesi İndirme", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(BATCH_CHANNEL, "Liste İndirme", NotificationManager.IMPORTANCE_LOW)
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    private fun base(context: Context, channelId: String) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOnlyAlertOnce(true)
        .setSilent(true)

    fun foregroundInfo(context: Context, notificationId: Int, channelId: String, title: String, text: String): ForegroundInfo {
        val notification = base(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun updateProgress(context: Context, notificationId: Int, channelId: String, title: String, text: String, progressPercent: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = base(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progressPercent.coerceIn(0, 100), false)
                .setOngoing(true)
                .build()
            nm.notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    fun showFinished(context: Context, notificationId: Int, channelId: String, title: String, text: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(notificationId + 1, notification)
        } catch (_: Exception) {}
    }
}
