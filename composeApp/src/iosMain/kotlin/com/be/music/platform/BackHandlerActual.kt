package com.be.music.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    // iOS: gesture navigasyonları ayrıca halledilir
}
