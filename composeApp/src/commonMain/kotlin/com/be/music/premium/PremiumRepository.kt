package com.be.music.premium

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.be.music.platform.datastorePath
import kotlinx.coroutines.CoroutineScope
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tüm platformlarda ortak premium deposu.
 * Android'de mevcut premium durumunu korumak için aynı dosya adı kullanılır
 * (premium_prefs.preferences_pb); ilk açılışta SharedPreferences'tan bir kez taşınır.
 */
object PremiumStorage {
    private val scope = CoroutineScope(SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { datastorePath("premium_prefs.preferences_pb") }
        )
    }

    fun get(): DataStore<Preferences> = dataStore

    private object Keys {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val PREMIUM_EXPIRATION = longPreferencesKey("premium_expiration")
        val REWARDED_AD_COUNT = intPreferencesKey("rewarded_ad_count")
        val LAST_TRUSTED_TIMESTAMP = longPreferencesKey("last_trusted_timestamp")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color")
    }

    val premiumState: Flow<PremiumState> = get().data.map { prefs ->
        PremiumState(
            isPremium = prefs[Keys.IS_PREMIUM] ?: false,
            premiumExpirationTimestamp = prefs[Keys.PREMIUM_EXPIRATION] ?: 0L,
            rewardedAdCount = prefs[Keys.REWARDED_AD_COUNT] ?: 0,
            lastTrustedOnlineTimestamp = prefs[Keys.LAST_TRUSTED_TIMESTAMP] ?: 0L
        )
    }

    suspend fun getPremiumState(): PremiumState = premiumState.first()

    suspend fun setPremiumState(state: PremiumState) {
        get().edit { prefs ->
            prefs[Keys.IS_PREMIUM] = state.isPremium
            prefs[Keys.PREMIUM_EXPIRATION] = state.premiumExpirationTimestamp
            prefs[Keys.REWARDED_AD_COUNT] = state.rewardedAdCount
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = state.lastTrustedOnlineTimestamp
        }
    }

    suspend fun incrementRewardedAdCount(): Int {
        get().edit { prefs ->
            prefs[Keys.REWARDED_AD_COUNT] = (prefs[Keys.REWARDED_AD_COUNT] ?: 0) + 1
        }
        return getPremiumState().rewardedAdCount
    }

    suspend fun resetRewardedAdCount() {
        get().edit { prefs -> prefs[Keys.REWARDED_AD_COUNT] = 0 }
    }

    suspend fun activatePremium(expirationTimestamp: Long, trustedTimestamp: Long) {
        get().edit { prefs ->
            prefs[Keys.IS_PREMIUM] = true
            prefs[Keys.PREMIUM_EXPIRATION] = expirationTimestamp
            prefs[Keys.REWARDED_AD_COUNT] = 0
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = trustedTimestamp
        }
    }

    suspend fun updateLastTrustedTimestamp(timestamp: Long) {
        get().edit { prefs -> prefs[Keys.LAST_TRUSTED_TIMESTAMP] = timestamp }
    }

    suspend fun clearPremium() {
        get().edit { prefs ->
            prefs[Keys.IS_PREMIUM] = false
            prefs[Keys.PREMIUM_EXPIRATION] = 0L
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = 0L
        }
    }

    val keepAliveEnabled: Flow<Boolean> = get().data.map { prefs ->
        prefs[Keys.KEEP_ALIVE_ENABLED] ?: false
    }

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        get().edit { prefs -> prefs[Keys.KEEP_ALIVE_ENABLED] = enabled }
    }

    suspend fun setCustomThemeColor(argb: Int) {
        cachedCustomThemeColor = argb
        get().edit { prefs -> prefs[Keys.CUSTOM_THEME_COLOR] = argb }
    }

    /** Android'in activity recreate senkronuna lazım — cache */
    @Volatile
    var cachedCustomThemeColor: Int? = null

    suspend fun clearCustomThemeColor() {
        cachedCustomThemeColor = null
        get().edit { prefs -> prefs.remove(Keys.CUSTOM_THEME_COLOR) }
    }

    val customThemeColor: Flow<Int?> = get().data.map { prefs ->
        prefs[Keys.CUSTOM_THEME_COLOR].also { if (it != null) cachedCustomThemeColor = it }
    }
}

/** Android SharedPreferences -> DataStore bir kerelik taşıma (platform özel). */
expect suspend fun migrateLegacyPrefsIfNeeded()
