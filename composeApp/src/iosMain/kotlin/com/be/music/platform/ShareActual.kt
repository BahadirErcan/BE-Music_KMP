package com.be.music.platform

actual fun shareExportedFile(path: String, shareTitle: String, onFailed: (String?) -> Unit) {
    // iOS: paylaşım sayfası yer tutucu (UIActivityViewController sonra bağlanacak)
    showToast("$shareTitle: $path")
}
