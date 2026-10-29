@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.be.music.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.removeObserver
import platform.Foundation.NSURL

/**
 * iOS oynatıcı köprüsü — AVPlayer temelli.
 * Kuyruk yönetimi ortak PlayerBridge semantiğini izler (Android ile aynı davranış).
 * Not: AVPlayer AVAudioSession yapılandırması AppDelegate/Swift tarafında yapılır.
 */
class IosPlayerBridge : PlayerBridge, QueueAwarePlayerBridge {

    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentTrackId = MutableStateFlow<Long?>(null)
    override val currentTrackId: StateFlow<Long?> = _currentTrackId.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeat.OFF)
    override val repeatMode: StateFlow<PlayerRepeat> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _trackEnded = MutableStateFlow(0)
    override val trackEnded: StateFlow<Int> = _trackEnded.asStateFlow()

    private val _queueIds = MutableStateFlow(emptyList<Long>())
    override val queueIds: StateFlow<List<Long>> = _queueIds.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val player = AVPlayer()

    private var queue: List<PlayerTrack> = emptyList()
    private var index = 0
    private var itemObserver: Any? = null
    private var monitorJob: Job? = null

    init {
        monitorJob = scope.launch {
            while (isActive) {
                delay(500)
                if (_playing.value) {
                    val pos = CMTimeGetSeconds(player.currentTime())
                    if (!pos.isNaN() && pos.isFinite() && pos >= 0) {
                        _positionMs.value = (pos * 1000).toLong()
                    }
                    val dur = currentItemDurationMs()
                    if (dur > 0) _durationMs.value = dur
                }
            }
        }
    }

    private fun currentItemDurationMs(): Long {
        val item = player.currentItem ?: return 0L
        val seconds = CMTimeGetSeconds(item.duration)
        return if (!seconds.isNaN() && seconds.isFinite() && seconds > 0) (seconds * 1000).toLong() else 0L
    }

    private fun observeItemEnd(item: AVPlayerItem) {
        itemObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        itemObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            item,
            null
        ) { _ ->
            _trackEnded.value = _trackEnded.value + 1
            when (_repeatMode.value) {
                PlayerRepeat.ONE -> playIndex(index)
                PlayerRepeat.ALL -> playIndex((index + 1) % queue.size)
                PlayerRepeat.OFF -> {
                    if (index < queue.size - 1) playIndex(index + 1) else _playing.value = false
                }
            }
        }
    }

    private fun playIndex(newIndex: Int, positionMs: Long = 0L) {
        if (queue.isEmpty()) return
        index = newIndex.coerceIn(0, queue.size - 1)
        val track = queue[index]
        val path = track.uri.removePrefix("file://")
        val url = if (path.startsWith("/")) {
            NSURL.fileURLWithPath(path)
        } else {
            NSURL.URLWithString(track.uri)
        } ?: return
        val item = AVPlayerItem.playerItemWithURL(url)
        player.replaceCurrentItemWithPlayerItem(item)
        observeItemEnd(item)
        _currentTrackId.value = track.id
        _durationMs.value = track.durationMs
        _positionMs.value = positionMs
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
        player.play()
        _playing.value = true
        _queueIds.value = queue.map { it.id }
        _currentIndex.value = index
    }

    override fun setQueue(tracks: List<PlayerTrack>, startIndex: Int, startPositionMs: Long) {
        queue = tracks
        playIndex(startIndex, startPositionMs)
    }

    override fun play() {
        player.play()
        _playing.value = true
    }

    override fun pause() {
        player.pause()
        _playing.value = false
    }

    override fun playPause() {
        if (_playing.value) pause() else play()
    }

    override fun seekTo(ms: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 600))
        _positionMs.value = ms
    }

    override fun seekNext() {
        if (queue.isEmpty()) return
        if (index < queue.size - 1) playIndex(index + 1)
        else if (_repeatMode.value == PlayerRepeat.ALL) playIndex(0)
    }

    override fun seekPrevious() {
        if (queue.isEmpty()) return
        if (index > 0) playIndex(index - 1)
        else if (_repeatMode.value == PlayerRepeat.ALL) playIndex(queue.size - 1)
    }

    override fun setRepeat(mode: PlayerRepeat) {
        _repeatMode.value = mode
    }

    override fun setShuffle(enabled: Boolean) {
        _shuffleEnabled.value = enabled
    }

    override fun updateCurrentTrackMetadata(track: PlayerTrack) {
        queue = queue.map { if (it.id == track.id) track else it }
    }

    override fun release() {
        monitorJob?.cancel()
        itemObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        player.pause()
    }
}

actual fun createPlayerBridge(): PlayerBridge = IosPlayerBridge()
