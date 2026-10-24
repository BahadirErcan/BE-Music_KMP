package com.be.music.platform

import com.be.music.platform.FS

actual fun shareExportedFile(path: String, shareTitle: String, onFailed: (String?) -> Unit) {
    // Masaüstünde dosya diske yazıldı; kullanıcıya yolunu gösteriyoruz
    showToast("$shareTitle: $path")
}
