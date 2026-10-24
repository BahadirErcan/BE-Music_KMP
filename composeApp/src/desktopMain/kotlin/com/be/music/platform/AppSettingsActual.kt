package com.be.music.platform

actual fun openAppSettings() {
    // Masaüstünde uygulama ayarı yok
}

actual fun onLanguageChanged() {
    showToast("Dil değişikliği için uygulamayı yeniden başlatın")
}
