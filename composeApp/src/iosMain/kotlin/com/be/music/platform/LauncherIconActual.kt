package com.be.music.platform

actual object LauncherIcon {
    actual val supported: Boolean = false
    actual fun ensureEnabled() {}
    actual fun currentIconPath(): String? = null
    actual suspend fun setCustom(bytes: ByteArray): LauncherIconResult = LauncherIconResult.GENERIC_ERROR
    actual suspend fun restoreDefault(): Boolean = false
}

actual suspend fun pickIconImage(): ByteArray? = null
