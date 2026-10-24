package com.be.music.youtube

/**
 * iOS arama stub: yerelde yt-dlp/NewPipe yok.
 * 7/24 sunucu planı devreye girdiğinde burası sunucu API'sine bağlanacak.
 */
actual object YoutubeSearchEngine {
    actual fun init() {}

    actual suspend fun searchPage(
        query: String,
        token: SearchToken?
    ): Pair<List<YoutubeVideo>, SearchToken?> {
        // sunucu bağlantısı bekleniyor — boş sonuç döndürülür
        return emptyList<YoutubeVideo>() to null
    }
}
