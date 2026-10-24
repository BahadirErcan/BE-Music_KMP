package com.be.music.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.be.music.BeMusicApplication
import com.be.music.ActivityHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Android SAF köprüsü. Seçilen dosyayı uygulama dizinine kopyalayıp o yolu döndürür. */
actual object FilePicker {
    actual suspend fun pickJsonFile(): String? {
        val activity = ActivityHolder.current ?: return null
        val registry = activity.activityResultRegistry
        val key = "be_music_json_pick_${System.currentTimeMillis()}"
        return suspendCancellableCoroutine { cont ->
            val launcher: ActivityResultLauncher<Intent> = registry.register(key, ActivityResultContracts.StartActivityForResult()) { result ->
                registry.unregisterQuietly(key)
                val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
                if (uri != null) cont.resume(copyUriToAccessibleFile(uri)) else cont.resume(null)
            }
            cont.invokeOnCancellation { registry.unregisterQuietly(key) }
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                launcher.launch(intent)
            } catch (e: Exception) {
                registry.unregisterQuietly(key)
                println("[FilePicker] Hata: $e")
                cont.resume(null)
            }
        }
    }

    private fun copyUriToAccessibleFile(uri: Uri): String? {
        return try {
            val context = BeMusicApplication.app()
            val dir = File(context.filesDir, "imported")
            dir.mkdirs()
            val target = File(dir, "playlist_import_${System.currentTimeMillis()}.json")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            target.absolutePath
        } catch (e: Exception) {
            println("[FilePicker] Kopyalama hatası: $e")
            null
        }
    }

    actual suspend fun pickMediaDirectory(): String? {
        return null // Android'de kütüphane otomatik taranıyor
    }
}
