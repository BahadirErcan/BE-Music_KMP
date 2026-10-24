package com.be.music.premium

/**
 * Eski SharedPreferences yalnızca Android'de var; masaüstünde taşınacak bir şey yok.
 */
actual suspend fun migrateLegacyPrefsIfNeeded() = Unit
