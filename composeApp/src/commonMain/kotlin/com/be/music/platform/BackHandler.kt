package com.be.music.platform

import androidx.compose.runtime.Composable

/** Sistem geri tuşu davranışı: Android'de gerçek geri tuşu, desktop/iOS'ta boş. */
@Composable
expect fun BackHandlerCompat(enabled: Boolean = true, onBack: () -> Unit)
