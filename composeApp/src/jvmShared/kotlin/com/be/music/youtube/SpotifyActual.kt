package com.be.music.youtube

/**
 * JVM (Android + Masaüstü) Spotify actual: SpotifyPlaylistParser (web scraping) kullanır.
 */
actual object SpotifyTracks {
    actual suspend fun parseUrl(url: String): List<SpotifyTrack> {
        return SpotifyPlaylistParser.parseUrl(url)
    }

    actual suspend fun fetchPlaylistName(url: String): String? {
        return SpotifyPlaylistParser.fetchPlaylistName(url)
    }
}
