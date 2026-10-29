package com.be.music.youtube

import com.be.music.res.Res
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * iOS DownloadLauncher — SUNUCU STUB:
 * Yerel yt-dlp alternatifi hazır olana kadar bu akışlar "sunucu bağlantısı bekleniyor"
 * hatasını DownloadBus'a yayınlar. Kullanıcının 7/24 sunucu planı devreye girdiğinde
 * DownloadEngine buraya bağlanacak.
 */
actual object DownloadLauncher {

    actual fun single(videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean) {
        emitUnsupported()
    }

    actual fun spotify(url: String, quality: String, downloadLyrics: Boolean) {
        emitUnsupported()
    }

    actual fun batch(trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int) {
        emitUnsupported()
    }

    private fun emitUnsupported() {
        CoroutineScope(Dispatchers.Default).launch {
            DownloadBus.emit(
                DownloadBus.DownloadEvent.BatchFinished(
                    kind = "batch",
                    success = false,
                    downloaded = 0,
                    total = 0,
                    failed = 0,
                    error = getString(Res.string.server_pending)
                )
            )
        }
    }

    actual fun cancelSingle(compositeKey: String) {}
    actual fun cancelSpotify() {}
    actual fun cancelBatch() {}
}
