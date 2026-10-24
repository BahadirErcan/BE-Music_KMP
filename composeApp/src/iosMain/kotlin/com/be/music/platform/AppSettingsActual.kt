package com.be.music.platform

actual fun openAppSettings() {}

actual fun onLanguageChanged() {
    // iOS: uygulama yeniden başlatması gerekli (kullanıcıyi bilgilendir)
    showToast("Restart the app to apply language")
}
