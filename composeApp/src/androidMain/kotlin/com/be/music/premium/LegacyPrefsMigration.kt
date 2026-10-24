package com.be.music.premium

import android.content.Context
import com.be.music.BeMusicApplication
import com.be.music.data.AppPreferences
import com.be.music.youtube.DownloadCounter

/**
 * Eski SharedPreferences verilerini (premium_durum sayıları hariç)
 * DataStore'a bir kerelik taşıma. Premium DataStore zaten aynı dosyadadır,
 * yalnızca keep_alive_enabled ve other anahtarlar taşınır.
 */
actual suspend fun migrateLegacyPrefsIfNeeded() {
    val ctx = BeMusicApplication.app()
    try {
        val keepPrefs = ctx.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val keepAlive = keepPrefs.getBoolean("keep_alive_enabled", false)
        PremiumStorage.setKeepAliveEnabled(keepAlive)

        val customColor = keepPrefs.getInt("custom_theme_color", Int.MAX_VALUE)
        if (customColor != Int.MAX_VALUE) {
            PremiumStorage.setCustomThemeColor(customColor)
        }

        val appPrefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedLang = appPrefs.getString("app_language", null)
        if (savedLang != null && AppPreferences.getLanguage() == "SYSTEM") {
            AppPreferences.setLanguage(savedLang)
        }

        val playback = ctx.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
        val lastSongId = playback.getLong("last_song_id", -1L)
        val lastPosition = playback.getLong("last_position", 0L)
        if (lastSongId > 0L) {
            AppPreferences.savePlaybackState(lastSongId, lastPosition)
        }

        val adPrefs = ctx.getSharedPreferences("ad_download_prefs", Context.MODE_PRIVATE)
        val count = adPrefs.getInt("download_count", -1)
        if (count >= 0) {
            // counter'ı ilk açılışta bir kez (>=0 zaten varsa taşı, sonra sıfırdan kullanılır)
            repeat(count) { DownloadCounter.incrementDownloadCount() }
        }
    } catch (e: Exception) {
        println("[LegacyPrefs] Taşıma hatası: $e")
    }
}
