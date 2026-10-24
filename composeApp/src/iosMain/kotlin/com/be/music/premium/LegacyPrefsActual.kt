package com.be.music.premium

/** Eski SharedPreferences yalnızca Android'de var; iOS'ta taşınacak bir şey yok. */
actual suspend fun migrateLegacyPrefsIfNeeded() {}
