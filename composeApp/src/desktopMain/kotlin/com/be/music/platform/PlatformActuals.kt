package com.be.music.platform

import com.be.music.PlatformDirs
import java.io.File

actual object FS {
    actual val appDataDir: String get() = PlatformDirs.appDataDir.absolutePath

    actual val defaultMusicDir: String get() = PlatformDirs.defaultMusicDir.absolutePath
    actual val playlistExportDir: String get() = File(PlatformDirs.defaultMusicDir, "Playlist").absolutePath
    actual val musicExportDir: String get() = File(PlatformDirs.defaultMusicDir, "BE-Music").absolutePath

    private fun file(path: String): File = File(path)

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
    actual fun delete(path: String): Boolean = try { file(path).delete() } catch (e: Exception) { false }
    actual fun deleteRecursive(path: String): Boolean = try {
        file(path).deleteRecursively()
    } catch (e: Exception) { false }
    actual fun move(from: String, to: String): Boolean = try {
        file(from).renameTo(file(to))
    } catch (e: Exception) { false }
    actual fun copyFile(from: String, to: String): Boolean = try {
        file(from).copyTo(file(to), overwrite = true)
        true
    } catch (e: Exception) { false }
    actual fun lastModifiedMs(path: String): Long = try { file(path).lastModified() } catch (e: Exception) { 0L }
    actual fun setLastModified(path: String, ms: Long): Boolean = try {
        file(path).setLastModified(ms)
    } catch (e: Exception) { false }
    actual fun fileSize(path: String): Long = try { file(path).length() } catch (e: Exception) { 0L }

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
    actual fun extension(path: String): String = path.substringAfterLast('.', "")
    actual fun parentDir(path: String): String? {
        val idx = path.lastIndexOf('/')
        return if (idx > 0) path.substring(0, idx) else null
    }
    actual fun join(dir: String, child: String): String =
        if (dir.endsWith("/")) dir + child else "$dir/$child"
}

actual fun showToast(message: String, long: Boolean) {
    com.be.music.desktop.DesktopToast.show(message)
}
