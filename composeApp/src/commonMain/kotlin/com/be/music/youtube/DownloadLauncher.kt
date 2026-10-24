package com.be.music.youtube

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * İndirme olay veri yolu:
 * - Android: WorkManager worker'ları olayları buraya yayınlar
 * - Masaüstü/iOS: doğrudan koordinatör buraya yayınlar
 * Ortak ViewModel yalnızca bu akışı dinler.
 */
object DownloadBus {
    sealed class DownloadEvent {
        data class SingleProgress(val key: String, val progress: Int) : DownloadEvent()
        data class SingleFinished(val key: String, val success: Boolean) : DownloadEvent()
        data class BatchProgress(
            val kind: String, // "batch" | "spotify"
            val downloaded: Int,
            val total: Int,
            val currentTrack: String,
            val progress: Int
        ) : DownloadEvent()
        data class BatchFinished(
            val kind: String,
            val success: Boolean,
            val downloaded: Int,
            val total: Int,
            val failed: Int,
            val error: String?
        ) : DownloadEvent()
    }

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    suspend fun emit(event: DownloadEvent) {
        _events.emit(event)
    }

    /** Suspend olmayan bağlamlardan (progress callback vb.) olay yayını. */
    fun tryEmit(event: DownloadEvent): Boolean = _events.tryEmit(event)
}

/**
 * İndirme başlatma köprüsü:
 * - Android: WorkManager'a iş ekler (üzeri worker'larda foreground + bildirim)
 * - Masaüstü/iOS: doğrudan DownloadJob ile coroutines olarak çalıştırır
 */
expect object DownloadLauncher {
    fun single(videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean)
    fun spotify(url: String, quality: String, downloadLyrics: Boolean)
    fun batch(trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int)
    fun cancelSingle(compositeKey: String)
    fun cancelSpotify()
    fun cancelBatch()
}
