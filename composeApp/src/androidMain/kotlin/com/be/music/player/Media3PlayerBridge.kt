package com.be.music.player

import android.content.ComponentName
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.be.music.player.PlayerRepeat
import com.be.music.player.PlayerTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android uygulama Media3 MediaController köprüsü.
 * PlaybackService MediaSession'ına bağlanır; bildirim/medya oturumu aynen korunur.
 */
actual fun createPlayerBridge(): PlayerBridge = Media3PlayerBridge()

class Media3PlayerBridge : PlayerBridge {

    private var controller: MediaController? = null

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    init {
        val appContext = com.be.music.BeMusicApplication.app()
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val c = controllerFuture.get()
                this.controller = c
                _repeatMode.value = when (c.repeatMode) {
                    Player.REPEAT_MODE_ALL -> PlayerRepeat.ALL
                    Player.REPEAT_MODE_ONE -> PlayerRepeat.ONE
                    else -> PlayerRepeat.OFF
                }
                _shuffleEnabled.value = c.shuffleModeEnabled
                syncState()
                c.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentTrackId.value = mediaItem?.mediaId?.toLongOrNull()
                        syncState()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playing.value = isPlaying
                        if (isPlaying) startProgress() else stopProgress()
                    }

                    override fun onRepeatModeChanged(newRepeatMode: Int) {
                        _repeatMode.value = when (newRepeatMode) {
                            Player.REPEAT_MODE_ALL -> PlayerRepeat.ALL
                            Player.REPEAT_MODE_ONE -> PlayerRepeat.ONE
                            else -> PlayerRepeat.OFF
                        }
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleEnabled.value = shuffleModeEnabled
                    }
                })
            } catch (e: Exception) {
                println("[Media3PlayerBridge] Controller bağlanamadı: $e")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncState() {
        mainHandler.post {
            val c = controller ?: return@post
            _playing.value = c.isPlaying
            _currentTrackId.value = c.currentMediaItem?.mediaId?.toLongOrNull()
            _positionMs.value = c.currentPosition.coerceAtLeast(0)
            _durationMs.value = if (c.duration > 0) c.duration else 0L
        }
    }

    private val progress = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null) {
                _positionMs.value = c.currentPosition.coerceAtLeast(0)
                _durationMs.value = if (c.duration > 0) c.duration else _durationMs.value
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    private fun startProgress() {
        mainHandler.removeCallbacks(progress)
        mainHandler.postDelayed(progress, 300)
    }

    private fun stopProgress() {
        mainHandler.removeCallbacks(progress)
    }

    override fun setQueue(tracks: List<PlayerTrack>, startIndex: Int, startPositionMs: Long) {
        mainHandler.post {
            val c = controller ?: return@post
            val items = tracks.map { t ->
                MediaItem.Builder()
                    .setMediaId(t.id.toString())
                    .setUri(t.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(t.title)
                            .setArtist(t.artist)
                            .setAlbumTitle(t.album)
                            .setArtworkUri(t.artUri?.let { it.toUri() })
                            .build()
                    )
                    .build()
            }
            val startIndexSafe = if (items.isEmpty()) 0 else startIndex.coerceIn(0, items.size - 1)
            c.setMediaItems(items, startIndexSafe, startPositionMs)
            c.prepare()
            c.play()
            if (items.size > 1) {
                c.repeatMode = Player.REPEAT_MODE_ALL
                _repeatMode.value = PlayerRepeat.ALL
            }
        }
    }

    private fun trackKey(items: List<MediaItem>) = items.size - 1
    @Suppress("unused")
    private fun listKeyGuard(items: List<MediaItem>) = items.isNotEmpty()

    override fun play() {
        mainHandler.post { controller?.play() }
    }

    override fun pause() {
        mainHandler.post { controller?.pause() }
    }

    override fun playPause() {
        mainHandler.post {
            val c = controller ?: return@post
            if (c.isPlaying) {
                c.pause()
            } else if (c.mediaItemCount == 0) {
                // restore edilen şarkı için item yoksa bridge çağıran tarafın VM'i queue kurar
                c.play()
            } else {
                c.play()
            }
        }
    }

    override fun seekTo(ms: Long) {
        mainHandler.post { controller?.seekTo(ms) }
    }

    override fun seekNext() {
        mainHandler.post { controller?.seekToNext() }
    }

    override fun seekPrevious() {
        mainHandler.post { controller?.seekToPrevious() }
    }

    override fun setRepeat(mode: PlayerRepeat) {
        mainHandler.post {
            val c = controller ?: return@post
            val m3mode = when (mode) {
                PlayerRepeat.OFF -> Player.REPEAT_MODE_OFF
                PlayerRepeat.ALL -> Player.REPEAT_MODE_ALL
                PlayerRepeat.ONE -> Player.REPEAT_MODE_ONE
            }
            c.repeatMode = m3mode
            _repeatMode.value = mode
        }
    }

    override fun setShuffle(enabled: Boolean) {
        mainHandler.post {
            val c = controller ?: return@post
            c.shuffleModeEnabled = enabled
            _shuffleEnabled.value = enabled
        }
    }

    override fun updateCurrentTrackMetadata(track: PlayerTrack) {
        mainHandler.post {
            val c = controller ?: return@post
            val currentItem = c.currentMediaItem ?: return@post
            if (currentItem.mediaId != track.id.toString()) return@post
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artUri?.let { it.toUri() })
                        .build()
                )
                .build()
            c.replaceMediaItem(c.currentMediaItemIndex, updatedItem)
        }
    }

    override fun release() {
        stopProgress()
        mainHandler.post {
            try {
                controller?.release()
            } catch (_: Exception) {}
            controller = null
        }
    }
}
