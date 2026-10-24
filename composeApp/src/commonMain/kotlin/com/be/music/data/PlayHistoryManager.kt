@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.data

import com.be.music.platform.FS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryEntry(
    val songId: Long,
    val songTitle: String,
    val artistName: String,
    val albumName: String,
    val duration: Long,
    val path: String,
    val uriString: String,
    val playedAt: Long
)

@Serializable
data class HistoryDay(
    val date: String,
    val entries: List<HistoryEntry>
)

class PlayHistoryManager {
    private val file: String = FS.join(FS.appDataDir, "play_history.json")

    private val _history = MutableStateFlow<List<HistoryDay>>(emptyList())
    val history: StateFlow<List<HistoryDay>> = _history.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    private fun today(): String {
        val now = Clock.System.now()
        val d = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val mm = d.monthNumber.toString().padStart(2, '0')
        val dd = d.dayOfMonth.toString().padStart(2, '0')
        return "$dd-$mm-${d.year}"
    }

    private fun load() {
        try {
            val jsonStr = FS.readText(file)
            if (!jsonStr.isNullOrBlank()) {
                _history.value = json.decodeFromString<List<HistoryDay>>(jsonStr)
            }
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    private fun save() {
        try {
            FS.writeText(file, json.encodeToString(_history.value))
        } catch (_: Exception) {}
    }

    suspend fun addEntry(song: Song) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val cooldownMs = 30_000L

        val day = today()
        val current = _history.value.toMutableList()
        val todayIndex = current.indexOfFirst { it.date == day }
        if (todayIndex >= 0) {
            val todayEntries = current[todayIndex].entries
            val lastPlay = todayEntries.firstOrNull { it.songId == song.id }
            if (lastPlay != null && (now - lastPlay.playedAt) < cooldownMs) {
                return@withContext
            }
        }

        val entry = HistoryEntry(
            songId = song.id,
            songTitle = song.title,
            artistName = song.artistName,
            albumName = song.albumName,
            duration = song.duration,
            path = song.path,
            uriString = song.uriString,
            playedAt = now
        )
        if (todayIndex >= 0) {
            val d = current[todayIndex]
            current[todayIndex] = d.copy(entries = listOf(entry) + d.entries)
        } else {
            current.add(0, HistoryDay(date = day, entries = listOf(entry)))
        }
        _history.update { current }
        save()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.update { emptyList() }
        save()
    }
}
