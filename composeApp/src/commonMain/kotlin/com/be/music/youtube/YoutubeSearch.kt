package com.be.music.youtube

/**
 * YouTube arama köprüsü.
 * - Android & Masaüstü (JVM): NewPipe Extractor ile sayfalı arama
 * - iOS: sunucu tabanlı indirme gelince bağlanacak (şimdilik boş)
 */
expect object YoutubeSearchEngine {
    suspend fun searchPage(query: String, token: SearchToken?): Pair<List<YoutubeVideo>, SearchToken?>
    fun init()
}
