package com.be.music.platform

import androidx.compose.runtime.Composable

@Composable
actual fun StoragePermissionGate(onPermissionGranted: () -> Unit) {
    onPermissionGranted()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
actual suspend fun ensureDownloadPermissions(): Boolean = true
