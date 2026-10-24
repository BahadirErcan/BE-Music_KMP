package com.be.music.platform

import androidx.compose.runtime.Composable

@Composable
actual fun StoragePermissionGate(onPermissionGranted: () -> Unit) {
    onPermissionGranted()
}

actual suspend fun ensureDownloadPermissions(): Boolean = true
