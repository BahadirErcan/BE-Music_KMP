@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.premium

import kotlin.time.Clock

data class PremiumState(
    val isPremium: Boolean = false,
    val premiumExpirationTimestamp: Long = 0L,
    val rewardedAdCount: Int = 50,
    val lastTrustedOnlineTimestamp: Long = 0L
) {
    private val now: Long get() = Clock.System.now().toEpochMilliseconds()

    val remainingDays: Int
        get() {
            if (!isPremium || premiumExpirationTimestamp <= 0L) return 0
            val t = lastTrustedOnlineTimestamp.coerceAtLeast(now)
            val diff = premiumExpirationTimestamp - t
            return if (diff > 0) (diff / (1000 * 60 * 60 * 24)).toInt() else 0
        }

    val isExpired: Boolean
        get() {
            if (!isPremium) return true
            val t = lastTrustedOnlineTimestamp.coerceAtLeast(now)
            return premiumExpirationTimestamp > 0L && premiumExpirationTimestamp < t
        }

    companion object {
        const val REWARD_THRESHOLD = 50
        const val PREMIUM_DURATION_DAYS = 30L
    }
}
