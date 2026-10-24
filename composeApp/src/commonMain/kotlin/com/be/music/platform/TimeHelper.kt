@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.platform

import kotlin.time.Clock

/** Platform-independent current time (commonMain). */
object TimeHelper {
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
