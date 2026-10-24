package com.be.music.player

import androidx.compose.ui.graphics.ImageBitmap
import com.be.music.desktop.audio.DesktopAudioEngine
import com.be.music.player.PlayerRepeat
import com.be.music.player.PlayerTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

actual fun createPlayerBridge(): PlayerBridge = DesktopPlayerBridge()

/**
 * Masaüstü oynatıcı köprüsü: DesktopAudioEngine'i PlayerBridge'e sarar.
 */
class DesktopPlayerBridge : PlayerBridge, QueueAwarePlayerBridge {

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _trackId = MutableStateFlow<Long?>(null)
    override val currentTrackId: StateFlow<Long?> = _trackId.asStateFlow()
    private val _repeat = MutableStateFlow(PlayerRepeat.OFF)
    override val repeatMode: StateFlow<PlayerRepeat> = _repeat.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _trackEnded = MutableStateFlow(0)
    override val trackEnded: StateFlow<Int> = _trackEnded.asStateFlow()

    private val _queueIds = MutableStateFlow(emptyList<Long>())
    override val queueIds: StateFlow<List<Long>> = _queueIds.asStateFlow()
    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var tracks: List<PlayerTrack> = emptyList()
    private var monitorJob: kotlinx.coroutines.Job? = null

    init {
        monitorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                if (DesktopAudioEngine.playing.value) {
                    if (_positionMs.value != DesktopAudioEngine.positionMs.value) {
                        _positionMs.value = DesktopAudioEngine.positionMs.value
                    }
                    _durationMs.value = DesktopAudioEngine.durationMs.value
                }
                _trackEnded.value = DesktopAudioEngine.trackEnded.value
            }
        }
    }

    override fun setQueue(newTracks: List<PlayerTrack>, startIndex: Int, startPositionMs: Long) {
        tracks = newTracks
        _queueIds.value = newTracks.map { it.id }
        val start = startIndex.coerceIn(0, (newTracks.size - 1).coerceAtLeast(0))
        _currentIndex.value = start
        DesktopAudioEngine.setQueue(newTracks, start, startPositionMs)
    }

    override fun play() = DesktopAudioEngine.play()
    override fun pause() = DesktopAudioEngine.pause()
    override fun playPause() {
        if (DesktopAudioEngine.playing.value) DesktopAudioEngine.pause() else DesktopAudioEngine.play()
    }

    override fun seekTo(ms: Long) = DesktopAudioEngine.seekTo(ms)
    override fun seekNext() = DesktopAudioEngine.seekNext()
    override fun seekPrevious() = DesktopAudioEngine.seekPrevious()

    override fun setRepeat(mode: PlayerRepeat) {
        _repeat.value = mode
        DesktopAudioEngine.setRepeat(mode)
    }

    override fun setShuffle(enabled: Boolean) {
        _shuffle.value = enabled
        DesktopAudioEngine.setShuffle(enabled)
    }

    override fun updateCurrentTrackMetadata(track: PlayerTrack) {
        DesktopAudioEngine.updateCurrentTrackMetadata(track)
    }

    override fun release() {
        monitorJob?.cancel()
        DesktopAudioEngine.release()
    }
}
