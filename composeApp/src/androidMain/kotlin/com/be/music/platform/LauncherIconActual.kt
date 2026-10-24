package com.be.music.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Android custom launcher icon: PNG kaydetme + pinned shortcut (API 26+). */
actual object LauncherIcon {
    private const val TAG = "LauncherIcon"
    private const val ICON_FILENAME = "custom_launcher_icon.png"
    private const val ICON_SIZE = 192
    private const val SHORTCUT_ID = "custom_be_music_icon"

    private val appContext get() = com.be.music.BeMusicApplication.app()

    actual val supported: Boolean = true

    actual fun ensureEnabled() {
        val context = appContext
        val componentName = ComponentName(context, "com.be.music.MainActivity")
        val state = context.packageManager.getComponentEnabledSetting(componentName)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            try {
                context.packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "Launcher cannot be enabled: ${e.message}")
            }
        }
    }

    actual fun currentIconPath(): String? {
        val file = File(appContext.filesDir, ICON_FILENAME)
        return if (file.exists()) file.absolutePath else null
    }

    actual suspend fun setCustom(bytes: ByteArray): LauncherIconResult = withContext(Dispatchers.IO) {
        try {
            if (bytes.size > 10 * 1024 * 1024) return@withContext LauncherIconResult.TOO_LARGE
            val bitmap = decodeAndResize(bytes) ?: return@withContext LauncherIconResult.DECODE_FAILED
            val file = File(appContext.filesDir, ICON_FILENAME)
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()
            val applied = createPinnedShortcut(file)
            if (applied) LauncherIconResult.APPLIED else LauncherIconResult.SAVED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom icon: ${e.message}")
            LauncherIconResult.GENERIC_ERROR
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun decodeAndResize(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            Bitmap.createScaledBitmap(original, ICON_SIZE, ICON_SIZE, true)
        } catch (e: Exception) {
            Log.e(TAG, "decodeAndResize: ${e.message}")
            null
        }
    }

    private suspend fun createPinnedShortcut(file: File): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val context = appContext
            val shortcutManager = context.getSystemService(Activity.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!shortcutManager.isRequestPinShortcutSupported) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: run {
                cont.resume(false); return@suspendCancellableCoroutine
            }
            val icon = android.graphics.drawable.Icon.createWithBitmap(bitmap)
            val intentClass = try {
                Class.forName("com.be.music.MainActivity")
            } catch (e: Exception) {
                cont.resume(false); return@suspendCancellableCoroutine
            }
            val shortcut = android.content.pm.ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel("BE Music")
                .setLongLabel("BE Music")
                .setIcon(icon)
                .setIntent(
                    Intent(context, intentClass).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .build()
            val success = runCatching { shortcutManager.requestPinShortcut(shortcut, null) }.fold(
                onSuccess = { true },
                onFailure = { false }
            )
            bitmap.recycle()
            cont.resume(success)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pinned shortcut: ${e.message}")
            cont.resume(false)
        }
    }

    actual suspend fun restoreDefault(): Boolean = withContext(Dispatchers.IO) {
        val context = appContext
        try {
            val shortcutManager = context.getSystemService(Activity.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            try { shortcutManager.disableShortcuts(listOf(SHORTCUT_ID)) } catch (_: Exception) {}
            try { shortcutManager.removeAllDynamicShortcuts() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove shortcut: ${e.message}")
        }
        val file = File(appContext.filesDir, ICON_FILENAME)
        if (file.exists()) file.delete()
        true
    }
}

actual suspend fun pickIconImage(): ByteArray? {
    val activity = com.be.music.ActivityHolder.current ?: return null
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val key = "be_music_icon_pick_${System.currentTimeMillis()}"
        val launcher = activity.activityResultRegistry.register(
            key,
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            activity.activityResultRegistry.unregisterQuietly(key)
            val bytes = if (uri != null) {
                try {
                    activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    Log.e("pickIconImage", "read failed: ${e.message}")
                    null
                }
            } else null
            cont.resume(bytes)
        }
        cont.invokeOnCancellation { activity.activityResultRegistry.unregisterQuietly(key) }
        launcher.launch("image/*")
    }
}
