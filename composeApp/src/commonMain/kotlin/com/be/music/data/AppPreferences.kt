package com.be.music.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.be.music.platform.FS
import com.be.music.platform.datastorePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * App-lifetime tercihleri (eski SharedPreferences içeriğinin karşılığı):
 * - app_language (app_prefs)
 * - last_song_id / last_position (playback_prefs)
 * - custom icon enable (MainActivityCustomIcon)
 */
object AppPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var store: androidx.datastore.core.DataStore<Preferences>? = null

    fun get(): androidx.datastore.core.DataStore<Preferences> {
        val current = store
        if (current != null) return current
        synchronized(this) {
            val c = store
            if (c != null) return c
            val created = PreferenceDataStoreFactory.createWithPath(
                produceFile = { datastorePath("be_music_prefs.preferences_pb") }
            )
            store = created
            return created
        }
    }

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LAST_SONG_ID = longPreferencesKey("last_song_id")
        val LAST_POSITION = longPreferencesKey("last_position")
        val CUSTOM_ICON_ENABLED = stringPreferencesKey("custom_icon_enabled")
        val DESKTOP_MUSIC_DIRS = stringPreferencesKey("desktop_music_dirs")
        val SERVER_URL = stringPreferencesKey("server_url")
    }

    suspend fun getLanguage(): String = get().data.first()[Keys.APP_LANGUAGE] ?: "SYSTEM"

    fun languageFlow() = get().data.map { prefs -> prefs[Keys.APP_LANGUAGE] ?: "SYSTEM" }

    suspend fun setLanguage(langCode: String) {
        get().edit { prefs -> prefs[Keys.APP_LANGUAGE] = langCode }
    }

    suspend fun getLastSongId(): Long = get().data.first()[Keys.LAST_SONG_ID] ?: -1L
    suspend fun getLastPosition(): Long = get().data.first()[Keys.LAST_POSITION] ?: 0L
    suspend fun savePlaybackState(songId: Long?, positionMs: Long) {
        if (songId == null || songId <= 0L) return
        get().edit { prefs ->
            prefs[Keys.LAST_SONG_ID] = songId
            prefs[Keys.LAST_POSITION] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun desktopScanDirs(): List<String> {
        val raw = get().data.first()[Keys.DESKTOP_MUSIC_DIRS] ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }
    suspend fun addDesktopScanDir(dir: String) {
        val dirs = (desktopScanDirs() + dir).distinct()
        get().edit { prefs -> prefs[Keys.DESKTOP_MUSIC_DIRS] = dirs.joinToString("\n") }
    }
    suspend fun removeDesktopScanDir(dir: String) {
        val dirs = desktopScanDirs() - dir
        get().edit { prefs -> prefs[Keys.DESKTOP_MUSIC_DIRS] = dirs.joinToString("\n") }
    }

    /** iOS indirme sunucusu adresi (7/24 sunucu; iOS'ta yerel yt-dlp yok). */
    suspend fun getServerUrl(): String =
        get().data.first()[Keys.SERVER_URL] ?: ""

    suspend fun saveServerUrl(url: String) {
        get().edit { prefs -> prefs[Keys.SERVER_URL] = url.trim() }
    }
}
