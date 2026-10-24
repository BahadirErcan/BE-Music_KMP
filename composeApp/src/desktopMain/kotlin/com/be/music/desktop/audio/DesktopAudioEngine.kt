package com.be.music.desktop.audio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.be.music.player.PlayerRepeat
import com.be.music.player.PlayerTrack
import com.be.music.platform.datastorePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Masaüstü ses motoru:
 *   ffmpeg (process) -> raw PCM s16le -> EQ biquad filtreleri -> 8D LFO pan -> SourceDataLine
 * Oynatıcı köprüsü (AudioPlayerBridge) bu motoru kullanır.
 */
object DesktopAudioEngine {

    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val FRAME_SIZE = SAMPLE_RATE * CHANNELS * 2 // 16-bit stereo frame bytes

    // ---- Grafik ekolayzer (10 bant, ISO merkez frekansları Hz) ----
    val BAND_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    // ---- State ----
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentTrackId = MutableStateFlow<Long?>(null)
    val currentTrackId: StateFlow<Long?> = _currentTrackId.asStateFlow()

    private val _trackEnded = MutableStateFlow(0)
    val trackEnded: StateFlow<Int> = _trackEnded.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeat.OFF)
    val repeatMode: StateFlow<PlayerRepeat> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val queue = MutableStateFlow<List<PlayerTrack>>(emptyList())
    private val queueIndex = MutableStateFlow(0)

    var onQueueAdvance: (() -> Unit)? = null

    // ---- Decoder & playback ----
    private var decodeJob: Job? = null
    private var sourceLine: SourceDataLine? = null
    @Volatile
    private var generation = 0L

    private var pausedBytes = 0L
    private var playedFrames = 0L

    // ---- EQ / 8D state (prefs'e yansır) ----
    @Volatile
    var eqEnabled: Boolean = false
        private set
    @Volatile
    var eqLevels: IntArray = IntArray(BAND_HZ.size)
        private set
    @Volatile
    var eightDEnabled: Boolean = false
        private set

    private val effectsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        effectsScope.launch {
            val store = prefsStoreOrNew()
            val data = store.data.first()
            eqEnabled = data[Keys.ENABLED] ?: false
            eqLevels = parseLevels(data[Keys.LEVELS])
            eightDEnabled = data[Keys.EIGHT_D] ?: false
            rebuildFilters()
        }
    }

    @Volatile
    private var cachedPrefs: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null

    private suspend fun prefsStoreOrNew(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        cachedPrefs?.let { return it }
        val store = PreferenceDataStoreFactory.createWithPath(
            produceFile = { datastorePath("equalizer_prefs.preferences_pb") }
        )
        cachedPrefs = store
        return store
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val LEVELS = stringPreferencesKey("levels")
        val EIGHT_D = booleanPreferencesKey("eight_d")
    }

    private fun parseLevels(csv: String?): IntArray {
        if (csv.isNullOrBlank()) return IntArray(BAND_HZ.size)
        val parts = csv.split(",")
        return IntArray(BAND_HZ.size) { i -> parts.getOrNull(i)?.toIntOrNull() ?: 0 }
    }

    private suspend fun persistCurrent() {
        val store = prefsStoreOrNew()
        store.edit { prefs ->
            prefs[Keys.ENABLED] = eqEnabled
            prefs[Keys.LEVELS] = eqLevels.joinToString(",")
            prefs[Keys.EIGHT_D] = eightDEnabled
        }
    }

    // ---- Biquad peaking filtreleri (RBJ cookbook) ----
    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a1 = 0.0; var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0
        private var y1L = 0.0; private var y2L = 0.0
        private var y1R = 0.0; private var y2R = 0.0

        fun setPeaking(freqHz: Double, gainDb: Double, sampleRate: Double = SAMPLE_RATE.toDouble(), q: Double = 0.9) {
            val A = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)
            b0 = 1.0 + alpha * A
            b1 = -2.0 * cosW0
            b2 = 1.0 - alpha * A
            val a0 = 1.0 + alpha / A
            a1 = -2.0 * cosW0
            a2 = 1.0 - alpha / A
            // normalize
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
        }

        fun apply(left: Double, right: Double): Pair<Double, Double> {
            val yL = b0 * left + b1 * x1 + b2 * x2 - a1 * y1L - a2 * y2L
            val yR = b0 * right + b1 * x1 + b2 * x2 - a1 * y1R - a2 * y2R
            x2 = x1; x1 = left; y2L = y1L; y1L = yL; y2R = y1R; y1R = yR
            return yL to yR
        }
    }

    private var filters: List<Biquad> = emptyList()

    private fun rebuildFilters() {
        filters = if (eqEnabled) {
            (0 until BAND_HZ.size).map { i ->
                Biquad().also {
                    val gainDb = eqLevels.getOrNull(i)?.let { mb -> mb / 100.0 } ?: 0.0
                    it.setPeaking(BAND_HZ[i].toDouble(), gainDb)
                }
            }
        } else {
            emptyList()
        }
    }

    fun setBandLevel(band: Int, milliBel: Int) {
        if (band < 0 || band >= eqLevels.size) return
        eqLevels = eqLevels.copyOf().also { it[band] = milliBel.coerceIn(-1500, 1500) }
        rebuildFilters()
        effectsScope.launch { persistCurrent() }
    }

    fun setEnabled(enabled: Boolean) {
        eqEnabled = enabled
        rebuildFilters()
        effectsScope.launch { persistCurrent() }
    }

    fun bandLevel(band: Int): Int = eqLevels.getOrNull(band) ?: 0

    fun setEightDEnabled(enabled: Boolean) {
        eightDEnabled = enabled
        effectsScope.launch { persistCurrent() }
    }

    // ---------- Queue management ----------

    fun setQueue(tracks: List<PlayerTrack>, startIndex: Int, startPositionMs: Long) {
        queue.value = tracks
        queueIndex.value = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        startCurrentTrack(startPositionMs)
    }

    fun play() {
        if (!_playing.value && currentStreamActive()) {
            _playing.value = true
        } else {
            startCurrentTrack(pausedMs())
        }
    }

    fun pause() {
        _playing.value = false
    }

    private fun pausedMs(): Long = _positionMs.value

    private fun currentStreamActive(): Boolean = decodeJob?.isActive == true

    fun seekTo(ms: Long) {
        val track = currentTrack() ?: return
        val dur = _durationMs.value
        val target = if (dur in 1..999999999) ms.coerceIn(0, dur) else ms.coerceAtLeast(0)
        startCurrentTrack(target)
    }

    fun seekNext() {
        val list = queue.value
        if (list.isEmpty()) return
        var nextIdx = queueIndex.value + 1
        if (nextIdx >= list.size) {
            nextIdx = when (_repeatMode.value) {
                PlayerRepeat.ALL -> 0
                else -> list.size - 1
            }
        }
        queueIndex.value = nextIdx
        startCurrentTrack(0L)
    }

    fun seekPrevious() {
        val list = queue.value
        if (list.isEmpty()) return
        var idx = queueIndex.value - 1
        if (idx < 0) idx = when (_repeatMode.value) {
            PlayerRepeat.ALL -> list.size - 1
            else -> 0
        }
        queueIndex.value = idx
        startCurrentTrack(0L)
    }

    fun setRepeat(mode: PlayerRepeat) {
        _repeatMode.value = mode
    }

    fun setShuffle(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        val list = queue.value
        if (list.size > 1) {
            val currentId = currentTrackId.value
            if (enabled) {
                queue.value = list.shuffled().let { shuffled ->
                    if (currentId != null) shuffled.sortedBy { it.id == currentId }.reversed().sortedByDescending { it.id == currentId } else shuffled
                }
            }
        }
    }

    fun currentTrack(): PlayerTrack? = queue.value.getOrNull(queueIndex.value)

    fun updateCurrentTrackMetadata(track: PlayerTrack) {
        val cur = currentTrack() ?: return
        if (cur.id == track.id) {
            queue.value = queue.value.toMutableList().also { it[queueIndex.value] = track }
        }
    }

    fun release() {
        stopInternal()
    }

    // ---------- Decode & Play ----------

    private fun stopInternal() {
        generation++
        decodeJob?.cancel()
        decodeJob = null
        sourceLine?.flush()
        sourceLine?.stop()
        sourceLine?.close()
        sourceLine = null
        _playing.value = false
    }

    /** Yeni bir oynatma başlatalım: generation ile eski decode yi sonlandırır */
    private fun startCurrentTrack(positionMs: Long) {
        val track = currentTrack() ?: return
        val rawPath = track.uri.removePrefix("file://")
        val f = File(rawPath)
        if (!f.exists()) {
            println("[DesktopAudioEngine] Dosya yok: $rawPath")
            advanceOrEnd()
            return
        }

        stopInternal()
        _currentTrackId.value = track.id
        _durationMs.value = ffprobeDurationMs(f) ?: track.durationMs
        playedFrames = positionMs * SAMPLE_RATE / 1000
        _positionMs.value = positionMs.coerceAtLeast(0)

        val gen = ++generation
        _playing.value = true

        decodeJob = scope.launch {
            try {
                val pb = ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "quiet",
                    "-ss", String.format("%.3f", positionMs / 1000.0),
                    "-i", f.absolutePath,
                    "-f", "s16le",
                    "-ar", SAMPLE_RATE.toString(),
                    "-ac", CHANNELS.toString(),
                    "-c:a", "pcm_s16le",
                    "pipe:1"
                ).redirectErrorStream(true)
                val proc = pb.start()

                val fmt = AudioFormat(SAMPLE_RATE.toFloat(), 16, CHANNELS, true, false)
                val line = AudioSystem.getSourceDataLine(fmt)
                line.open(fmt, FRAME_SIZE * CHANNELS * 8)
                sourceLine = line
                line.start()

                val buf = ByteArray(8192)
                val stream: InputStream = proc.inputStream
                var posMs = positionMs
                var bytesUntilTick = FRAME_SIZE * (SAMPLE_RATE / 40) // ~25 ms tick

                var idxInFrames: Long = (posMs * SAMPLE_RATE / 1000)

                while (gen == generation && _playing.value) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    // 16-bit stereo çiftler
                    var i = 0
                    while (i < n) {
                        if (i + 4 > n) break // yarım frame
                        val left = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
                        val right = ((buf[i + 2].toInt() and 0xFF) or (buf[i + 3].toInt() shl 8)).toShort().toInt()
                        var l = left.toDouble() / 32768.0
                        var r = right.toDouble() / 32768.0

                        // EQ DSP (bantlar sırayla)
                        for (filter in filters) {
                            val out = filter.apply(l, r)
                            l = out.first
                            r = out.second
                        }

                        // 8D: sinusoidal pan (0.15 Hz hafif çevirme)
                        if (eightDEnabled) {
                            val t = idxInFrames / SAMPLE_RATE.toDouble()
                            val pan = 0.5 + 0.5 * sin(2.0 * PI * 0.15 * t)
                            val lOut = l * (1.0 - pan * 0.8) + r * 0.1
                            val rOut = r * (pan * 0.8 + 0.2) + l * 0.1
                            l = lOut
                            r = rOut
                        }

                        val cl = (l.coerceIn(-1.0, 1.0) * 32767.0).toInt()
                        val cr = (r.coerceIn(-1.0, 1.0) * 32767.0).toInt()
                        buf[i] = (cl and 0xFF).toByte()
                        buf[i + 1] = ((cl shr 8) and 0xFF).toByte()
                        buf[i + 2] = (cr and 0xFF).toByte()
                        buf[i + 3] = ((cr shr 8) and 0xFF).toByte()
                        i += 4
                        idxInFrames++
                    }

                    line.write(buf, 0, n)
                    progressBytes(n.toLong())
                    bytesUntilTick -= n
                    if (bytesUntilTick <= 0) {
                        bytesUntilTick = FRAME_SIZE * (SAMPLE_RATE / 40)
                        posMs = idxInFrames * 1000L / SAMPLE_RATE
                        _positionMs.value = posMs
                    }
                }

                line.drain()
                proc.waitFor()
                if (gen == generation) {
                    onComplete()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[DesktopAudioEngine] Decode hatası: $e")
                if (gen == generation) onComplete()
            }
        }
    }

    private fun progressBytes(bytes: Long) {
        // 4 byte/frame
        playedFrames += bytes / 4
    }

    private fun onComplete() {
        val list = queue.value
        if (list.isEmpty()) {
            _playing.value = false
            return
        }
        _trackEnded.value = _trackEnded.value + 1
        if (_repeatMode.value == PlayerRepeat.ONE) {
            startCurrentTrack(0L)
            return
        }
        if (queueIndex.value >= list.size - 1) {
            if (_repeatMode.value == PlayerRepeat.ALL) {
                queueIndex.value = 0
                startCurrentTrack(0L)
            } else {
                _playing.value = false
                _positionMs.value = 0L
            }
        } else {
            queueIndex.value += 1
            startCurrentTrack(0L)
        }
    }

    private fun advanceOrEnd() {
        val list = queue.value
        if (queueIndex.value < list.size - 1) {
            queueIndex.value += 1
            startCurrentTrack(0L)
        } else {
            _playing.value = false
        }
    }

    fun ffprobeDurationMs(file: File): Long? = try {
        val proc = ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.absolutePath
        ).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        (out.toDoubleOrNull()?.times(1000))?.toLong()
    } catch (e: Exception) {
        null
    }
}
