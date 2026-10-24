package com.be.music.youtube

import com.be.music.BeMusicApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android actüel: yerleşik youtubedl-android motoru.
 * options'ta (flag="" -> value=URL) kuralı: değeri URL olarak kullanılır.
 */
actual object YtDlp {
    actual val supported: Boolean = true

    private val started = AtomicBoolean(false)

    actual suspend fun init() = withContext(Dispatchers.IO) {
        if (started.getAndSet(true)) return@withContext
        try {
            YoutubeDL.getInstance().init(BeMusicApplication.app())
        } catch (e: Exception) {
            println("[YtDlp] init hatası (zaten init edilmiş olabilir): ${e.message}")
        }
        try {
            YoutubeDL.getInstance().updateYoutubeDL(BeMusicApplication.app())
        } catch (e: Exception) {
            println("[YtDlp] self-update atlandı: ${e.message}")
        }
    }

    actual suspend fun run(
        options: List<Pair<String, String>>,
        onProgress: (percent: Float, etaSeconds: Long, line: String?) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            init()
            val url = options.firstOrNull { it.first.isEmpty() }?.second ?: ""
            val request = YoutubeDLRequest(url)
            for ((flag, value) in options) {
                if (flag.isEmpty()) continue
                if (value.isEmpty()) request.addOption(flag) else request.addOption(flag, value)
            }
            val response = YoutubeDL.getInstance().execute(request) { pct, eta, line ->
                try { onProgress(pct, eta, line) } catch (_: Exception) {}
            }
            val exitCode = response.exitCode ?: 0
            if (exitCode == 0) Result.success(Unit)
            else Result.failure(IllegalStateException("yt-dlp exit: $exitCode"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual suspend fun dumpJson(url: String): String? = withContext(Dispatchers.IO) {
        try {
            init()
            val request = YoutubeDLRequest(url)
            request.addOption("--user-agent", defaultUserAgent())
            request.addOption("--extractor-args", defaultExtractorArgs())
            request.addOption("--no-playlist", "")
            request.addOption("--dump-json")
            val response = YoutubeDL.getInstance().execute(request) { _, _, _ -> }
            val out = response.out
            if (!out.isNullOrBlank()) out.lines().firstOrNull { it.isNotBlank() } else null
        } catch (e: Exception) {
            println("[YtDlp] dumpJson hatası: ${e.message}")
            null
        }
    }

    actual fun cancelAll() {
        try {
            YoutubeDL.getInstance().destroyProcessById("")
        } catch (_: Exception) {
        }
    }
}
