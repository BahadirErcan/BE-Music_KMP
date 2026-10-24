package com.be.music.youtube

/**
 * iOS yt-dlp stub: yerel indirme yok (App Store sandbox).
 * Gelecekteki 7/24 sunucu köprüsü buraya bağlanacak (DownloadEngine).
 */
actual object YtDlp {
    actual val supported: Boolean = false

    actual suspend fun init() {}

    actual suspend fun run(
        options: List<Pair<String, String>>,
        onProgress: (percent: Float, etaSeconds: Long, line: String?) -> Unit
    ): Result<Unit> {
        return Result.failure(IllegalStateException("iOS'ta yerel indirme yok — sunucu bağlantısı bekleniyor"))
    }

    actual suspend fun dumpJson(url: String): String? = null

    actual fun cancelAll() {}
}
