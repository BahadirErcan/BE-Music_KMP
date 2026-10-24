package com.be.music.youtube

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.be.music.platform.datastorePath
import kotlinx.coroutines.flow.first

/**
 * İndirilen parça sayacı (eski RewardedAdManager prefs → DataStore).
 * 10 indirmede bir rewarded ad gösterilir (Premium ise atlanır).
 */
object DownloadCounter {
    private object Keys {
        val DOWNLOAD_COUNT = intPreferencesKey("download_count")
    }
    const val REWARD_THRESHOLD = 10

    @Volatile
    private var store: androidx.datastore.core.DataStore<Preferences>? = null

    private fun get(): androidx.datastore.core.DataStore<Preferences> {
        val current = store
        if (current != null) return current
        synchronized(this) {
            val c = store
            if (c != null) return c
            val created = PreferenceDataStoreFactory.createWithPath(
                produceFile = { datastorePath("ad_download_prefs.preferences_pb") }
            )
            store = created
            return created
        }
    }

    suspend fun downloadCount(): Int = get().data.first()[Keys.DOWNLOAD_COUNT] ?: 0

    suspend fun incrementDownloadCount(): Int {
        val next = downloadCount() + 1
        if (next > REWARD_THRESHOLD) return REWARD_THRESHOLD
        get().edit { prefs -> prefs[Keys.DOWNLOAD_COUNT] = next }
        return next
    }

    suspend fun resetDownloadCount() {
        get().edit { prefs -> prefs[Keys.DOWNLOAD_COUNT] = 0 }
    }

    suspend fun shouldShowAd(isPremium: Boolean): Boolean {
        return downloadCount() >= REWARD_THRESHOLD && !isPremium
    }
}
