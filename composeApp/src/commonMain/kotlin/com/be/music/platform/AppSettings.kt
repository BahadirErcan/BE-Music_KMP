package com.be.music.platform

/** Uygulama ayarları ekranını açar (Android; diğer platformlar no-op). */
expect fun openAppSettings()

/** Dil değişikliğini uygula (Android: activity recreate; diğerleri no-op / yeniden başlatma ipucu). */
expect fun onLanguageChanged()
