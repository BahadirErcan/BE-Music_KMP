@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.premium

import com.be.music.platform.SimpleHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.be.music.platform.Platform
import com.be.music.platform.isDesktop
import kotlinx.coroutines.CoroutineScope
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

class PremiumManager(
    private val timeProvider: TimeProvider
) {
    companion object {
        @Volatile
        var isPremiumStatic: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(PremiumState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    /** Masaüstünde premium sistemi kaldırıldı: her şey açık, reklam yok. */
    private val premiumBypass: Boolean = Platform.isDesktop

    init {
        scope.launch {
            PremiumStorage.premiumState.collect { savedState ->
                _state.value = savedState
                isPremiumStatic = premiumBypass || (savedState.isPremium && !savedState.isExpired)
            }
        }
        scope.launch {
            syncTime()
        }
    }

    val isPremium: Boolean
        get() {
            if (premiumBypass) return true
            val s = _state.value
            return s.isPremium && !s.isExpired
        }

    val rewardCount: Int get() = _state.value.rewardedAdCount

    suspend fun syncTime(): Long = try {
        val onlineTime = timeProvider.getUtcTimestamp()
        if (onlineTime > 0L) {
            val current = _state.value
            val trustedTimestamp = maxOf(onlineTime, current.lastTrustedOnlineTimestamp)
            PremiumStorage.updateLastTrustedTimestamp(trustedTimestamp)
            onlineTime
        } else {
            -1L
        }
    } catch (e: Exception) {
        -1L
    }

    suspend fun onRewardedAdCompleted() {
        if (premiumBypass) return
        val onlineTime = syncTime()
        val trustedTime = if (onlineTime > 0L) onlineTime else Clock.System.now().toEpochMilliseconds()

        val current = _state.value
        if (current.isPremium && !current.isExpired) return

        val newCount = current.rewardedAdCount + 1

        if (newCount >= PremiumState.REWARD_THRESHOLD) {
            val expirationTime = trustedTime + (PremiumState.PREMIUM_DURATION_DAYS * 24 * 60 * 60 * 1000)
            PremiumStorage.activatePremium(expirationTime, trustedTime)
        } else {
            val updated = current.copy(
                rewardedAdCount = newCount,
                lastTrustedOnlineTimestamp = trustedTime
            )
            PremiumStorage.setPremiumState(updated)
        }
    }

    suspend fun checkAndRefreshPremium() {
        val current = PremiumStorage.getPremiumState()
        if (current.isPremium) {
            val onlineTime = syncTime()
            val trustedTime = if (onlineTime > 0L) onlineTime else Clock.System.now().toEpochMilliseconds()
            val updated = current.copy(lastTrustedOnlineTimestamp = trustedTime)
            PremiumStorage.setPremiumState(updated)
            if (updated.isExpired) {
                PremiumStorage.clearPremium()
            }
        }
    }

    fun isQualityPremium(quality: String): Boolean {
        val normalized = quality.trim().lowercase()
        return normalized == "1080p" || normalized == "highest" || normalized == "enyüksek"
    }

    val keepAliveEnabled: Flow<Boolean>
        get() = PremiumStorage.keepAliveEnabled

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        PremiumStorage.setKeepAliveEnabled(enabled)
    }
}

/** Online saat sağlayıcı (TÜM platformlar — Ktor). */
class TimeProvider {
    suspend fun getUtcTimestamp(): Long = try {
        val response = SimpleHttp.get("https://worldtimeapi.org/api/ip", 5000)
        if (response.isNullOrBlank()) -1L
        else response.substringAfter("\"unixtime\":")
            .substringBefore(',')
            .trim()
            .toLongOrNull()?.times(1000L) ?: -1L
    } catch (e: Exception) {
        -1L
    }
}
