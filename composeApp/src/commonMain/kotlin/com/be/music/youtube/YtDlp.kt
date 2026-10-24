package com.be.music.youtube

/**
 * yt-dlp motor köprüsü:
 * - Android: yerleşik youtubedl-android (Yausername) — self-update destekli
 * - Masaüstü: paketle beraber dağıtılan yt-dlp binary'si (m4a)</TODO>
 * - iOS: yok (sunucu tabanlı indirmeye geçilecek — İLE. STUB)
 */
expect object YtDlp {
    val supported: Boolean
    /** Android: init + arkaplan self-update; masaüstü: binary yolu hazırla */
    suspend fun init()

    /** Genel yürütme: options yt-dlp CLI seçenekleri (URL içerir). Dönen: çıkış kodu 0 ise success */
    suspend fun run(
        options: List<Pair<String, String>>,
        onProgress: (percent: Float, etaSeconds: Long, line: String?) -> Unit = { _, _, _ -> }
    ): Result<Unit>

    /** --dump-json sonucunu döndürür (subtitle tespiti vb.) */
    suspend fun dumpJson(url: String): String?

    fun cancelAll()
}

/** Kullanıcı kalite tercihi metni -> yt-dlp video format seçimi (mp4+yükleme ortak). */
fun defaultUserAgent(): String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

fun defaultExtractorArgs(): String = "youtube:player-client=android,web"
