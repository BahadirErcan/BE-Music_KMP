package com.be.music.platform

import android.widget.Toast

import com.be.music.BeMusicApplication

actual object Platform {
    actual val target: TargetPlatform = TargetPlatform.ANDROID
    actual val osFamily: String = "android"
    actual val appVersionCode: Int = com.be.music.BuildConfig.VERSION_CODE
    actual val appVersionName: String = com.be.music.BuildConfig.VERSION_NAME
}

actual object FS {
    private val ctx: android.content.Context get() = BeMusicApplication.app()

    private fun file(path: String): java.io.File = java.io.File(path)

    actual val appDataDir: String get() = ctx.filesDir.absolutePath
    actual val defaultMusicDir: String get() = ""
    actual val playlistExportDir: String
        get() {
            val music = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            return java.io.File(music, "Playlist").absolutePath
        }
    actual val musicExportDir: String
        get() {
            val music = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            return java.io.File(music, "BE-Music").absolutePath
        }

    actual fun exists(path: String): Boolean = file(path).exists()
    actual fun isDirectory(path: String): Boolean = file(path).isDirectory
    actual fun readText(path: String): String? = try {
        file(path).takeIf { it.exists() }?.readText()
    } catch (e: Exception) { null }
    actual fun writeText(path: String, content: String): Boolean = try {
        val f = file(path)
        f.parentFile?.mkdirs()
        f.writeText(content)
        true
    } catch (e: Exception) { false }
    actual fun appendText(path: String, content: String): Boolean = try {
        file(path).appendText(content)
        true
    } catch (e: Exception) { false }
    actual fun delete(path: String): Boolean = try {
        file(path).delete()
    } catch (e: Exception) { false }
    actual fun deleteRecursive(path: String): Boolean = try {
        val f = file(path)
        f.deleteRecursively()
    } catch (e: Exception) { false }
    actual fun move(from: String, to: String): Boolean = try {
        file(from).renameTo(file(to))
    } catch (e: Exception) { false }
    actual fun copyFile(from: String, to: String): Boolean = try {
        file(from).copyTo(file(to), overwrite = true)
        true
    } catch (e: Exception) { false }
    actual fun lastModifiedMs(path: String): Long = file(path).lastModified()
    actual fun setLastModified(path: String, ms: Long): Boolean = try {
        file(path).setLastModified(ms)
    } catch (e: Exception) { false }
    actual fun fileSize(path: String): Long = try { file(path).length() } catch (e: Exception) { 0 }

    actual fun listFiles(dir: String): List<String> = try {
        file(dir).listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
    } catch (e: Exception) { emptyList() }
    actual fun listDirs(dir: String): List<String> = try {
        file(dir).listFiles()?.filter { it.isDirectory }?.map { it.absolutePath } ?: emptyList()
    } catch (e: Exception) { emptyList() }
    actual fun makeDirs(dir: String): Boolean = try { file(dir).mkdirs() } catch (e: Exception) { false }

    actual fun fileName(path: String): String = path.substringAfterLast('/')
    actual fun baseName(path: String): String {
        val name = fileName(path)
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }
    actual fun extension(path: String): String = try {
        path.substringAfterLast('.', "")
    } catch (e: Exception) { "" }
    actual fun parentDir(path: String): String? {
        val idx = path.lastIndexOf('/')
        return if (idx > 0) path.substring(0, idx) else null
    }
    actual fun join(dir: String, child: String): String =
        if (dir.endsWith("/")) dir + child else "$dir/$child"
}

actual fun showToast(message: String, long: Boolean) {
    val appCtx = BeMusicApplication.app()
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        try {
            Toast.makeText(appCtx, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
