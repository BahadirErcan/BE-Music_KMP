package com.be.music.platform

import androidx.compose.runtime.Composable

/**
 * Depolama izni kapısı:
 * - Android: medya izinleri + "tüm dosyalara erişim" akışı (izin verilince callback çağrılır)
 * - Masaüstü/iOS: izin gerekmez, hemen callback
 */
@Composable
expect fun StoragePermissionGate(onPermissionGranted: () -> Unit)

/** İndirme başlatılmadan önce depolama izinlerini sağlar (suspend). false = reddedildi. */
expect suspend fun ensureDownloadPermissions(): Boolean
