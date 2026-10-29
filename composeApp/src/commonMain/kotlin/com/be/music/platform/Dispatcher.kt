package com.be.music.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platformlar arası "IO/thread pool" dispatcher.
 * JVM (android + desktop): IoDispatcher
 * iOS (Kotlin/Native): Dispatchers.Default (IO native tarafindan internal'i)
 */
expect val IoDispatcher: CoroutineDispatcher
