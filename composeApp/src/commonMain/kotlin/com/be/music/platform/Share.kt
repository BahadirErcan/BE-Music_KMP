package com.be.music.platform

/**
 * Dosya paylaşımı:
 * - Android: FileProvider + share chooser
 * - Masaüstü: dosya zaten diske yazıldı; yolu bildirim üstünden göster
 * - iOS: paylaşım sayfası (placeholder)
 */
expect fun shareExportedFile(path: String, shareTitle: String, onFailed: (String?) -> Unit)
