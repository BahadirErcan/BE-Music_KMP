package com.be.music.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    // Desktop: pencere kapatma/yön okları ile yönetilir; özel davranış yok
}
