package com.be.music.platform

/** Custom launcher icon feature (yalnızca Android — pinned shortcut / icon swap). */
expect object LauncherIcon {
    val supported: Boolean
    fun ensureEnabled()
    fun currentIconPath(): String?
    suspend fun setCustom(bytes: ByteArray): LauncherIconResult
    suspend fun restoreDefault(): Boolean
}

enum class LauncherIconResult {
    APPLIED,
    SAVED,
    TOO_LARGE,
    DECODE_FAILED,
    GENERIC_ERROR
}

/** Resim seçimi (bytes döndürür). */
expect suspend fun pickIconImage(): ByteArray?
