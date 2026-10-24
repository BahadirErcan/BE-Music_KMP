package com.be.music.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.be.music.BeMusicApplication
import java.io.File

actual fun shareExportedFile(path: String, shareTitle: String, onFailed: (String?) -> Unit) {
    try {
        val context: Context = BeMusicApplication.app()
        val file = File(path)
        if (!file.exists()) {
            onFailed(null)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, shareTitle))
    } catch (e: Exception) {
        onFailed(e.message)
    }
}
