package com.be.music.youtube

/** Ortak Spotify metadata soyutlaması (JVM'de web parse, iOS'ta sunucu RAW). */
data class SpotifyTrack(
    val name: String,
    val artist: String
)

expect object SpotifyTracks {
    suspend fun parseUrl(url: String): List<SpotifyTrack>
    suspend fun fetchPlaylistName(url: String): String?
}
