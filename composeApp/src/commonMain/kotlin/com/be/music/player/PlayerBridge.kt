package com.be.music.player

import kotlinx.coroutines.flow.StateFlow

/** Tüm platformlarda aynı semantiğe sahip tekrar modu. */
enum class PlayerRepeat { OFF, ALL, ONE }

data class PlayerTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artUri: String? = null,
    val durationMs: Long = 0L
)

data class PlayerSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentTrackId: Long? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0
)

/**
 * Ortak oynatıcı köprüsü:
 * - Android: Media3 MediaController (PlaybackService arkasında; bildirim/medya oturumu aynen korunur)
 * - Masaüstü: ffmpeg -> PCM -> javax.sound motoru (EQ + 8D DSP dahili)
 * - iOS: AVPlayer/AVAudioEngine
 */
interface PlayerBridge {
    val playing: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val currentTrackId: StateFlow<Long?>
    val repeatMode: StateFlow<PlayerRepeat>
    val shuffleEnabled: StateFlow<Boolean>
    /** Şarkı motor kendi içinde bittiğinde (desktop/iOS) tetiklenir; Android'de Media3 otomatik geçirir */
    val trackEnded: StateFlow<Int> // sirali sayaç: her track sonu bir artar

    fun setQueue(tracks: List<PlayerTrack>, startIndex: Int, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun playPause()
    fun seekTo(ms: Long)
    fun seekNext()
    fun seekPrevious()
    fun setRepeat(mode: PlayerRepeat)
    fun setShuffle(enabled: Boolean)
    /** Metadata düzenlemesi sonrası bildirim/Bindi güncelleme */
    fun updateCurrentTrackMetadata(track: PlayerTrack) {}
    fun setSpeed(speed: Float) {}
    val playbackSpeed: Float get() = 1f
    fun release()
}

/** Kuyruk bilgisi sunan köprüler (Android Media3 & desktop motor). */
interface QueueAwarePlayerBridge : PlayerBridge {
    /** oynatma sırasındaki track id'leri (shuffle uygulanmış sıra) */
    val queueIds: StateFlow<List<Long>>
    /** güncel kuyruk indeksi */
    val currentIndex: StateFlow<Int>
}

/** Kalıcı durum geri yüklemesi yapan köprüler. */
interface RestorablePlayerBridge : PlayerBridge {
    /** Uygulama açılışında kaydedilen şarkıyı "hazır" konuma getirir (oynatma başlamaz). */
    fun restoreState(songId: Long, positionMs: Long, durationMs: Long)
}

expect fun createPlayerBridge(): PlayerBridge
