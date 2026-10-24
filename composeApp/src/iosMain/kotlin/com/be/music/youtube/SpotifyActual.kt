package com.be.music.youtube

/** iOS Spotify stub: sunucu köprüsü sonrası bağlanacak. */
actual object SpotifyTracks {
    actual suspend fun parseUrl(url: String): List<SpotifyTrack> = emptyList()
    actual suspend fun fetchPlaylistName(url: String): String? = null
}
