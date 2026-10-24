package com.be.music.platform

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSString
import platform.Foundation.NSStringFromString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileExistsAtPath
import platform.Foundation.timeIntervalSince1970

private fun docsDir(): String {
    val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return url?.path ?: ""
}

actual object FS {
    actual val appDataDir: String get() = docsDir()
    actual val defaultMusicDir: String get() = docsDir()
    actual val playlistExportDir: String get() = docsDir() + "/Playlist"
    actual val musicExportDir: String get() = docsDir() + "/BE-Music"

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun isDirectory(path: String): Boolean {
        return memScoped {
            val isDir = alloc<BooleanVar>()
            NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
            isDir.value
        }
    }

    actual fun readText(path: String): String? = try {
        val data = NSData.dataWithContentsOfFile(path)
        data?.let { d -> NSString.create(data = d, encoding = NSUTF8StringEncoding) as? String }
    } catch (e: Exception) {
        null
    }

    actual fun writeText(path: String, content: String): Boolean = try {
        val ns = NSStringFromString(content)
        ns.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    } catch (e: Exception) {
        false
    }

    actual fun appendText(path: String, content: String): Boolean = try {
        val old = readText(path) ?: ""
        writeText(path, old + content)
    } catch (e: Exception) {
        false
    }

    actual fun delete(path: String): Boolean = try {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    } catch (e: Exception) {
        false
    }

    actual fun deleteRecursive(path: String): Boolean = delete(path)

    actual fun move(from: String, to: String): Boolean = try {
        NSFileManager.defaultManager.moveItemAtPath(from, to, null)
    } catch (e: Exception) {
        false
    }

    actual fun copyFile(from: String, to: String): Boolean = try {
        NSFileManager.defaultManager.copyItemAtPath(from, to, null)
    } catch (e: Exception) {
        false
    }

    actual fun lastModifiedMs(path: String): Long = try {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
        val date = attrs[NSFileModificationDate] as? NSDate
        (date?.timeIntervalSince1970?.times(1000.0))?.toLong() ?: 0L
    } catch (e: Exception) {
        0L
    }

    actual fun setLastModified(path: String, ms: Long): Boolean = false

    actual fun fileSize(path: String): Long = try {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
        val size = attrs[NSFileSize] as? platform.Foundation.NSNumber
        size?.unsignedLongLongValue?.toLong() ?: 0L
    } catch (e: Exception) {
        0L
    }

    actual fun listFiles(dir: String): List<String> = children(dir).filter { exists(it) && !isDirectory(it) }
    actual fun listDirs(dir: String): List<String> = children(dir).filter { isDirectory(it) }

    private fun children(dir: String): List<String> {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dir, null) as? List<*> ?: return emptyList()
        return contents.mapNotNull { it as? String }.map { joinPaths(dir, it) }
    }

    private fun joinPaths(dir: String, child: String): String =
        if (dir.endsWith("/")) dir + child else dir + "/" + child

    actual fun makeDirs(dir: String): Boolean = try {
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    } catch (e: Exception) {
        false
    }

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
        if (dir.endsWith("/")) dir + child else dir + "/" + child
}

actual fun showToast(message: String, long: Boolean) {
    // iOS'ta pencere içi overlay (AppRoot dinler) + günlük
    println("[Toast] $message")
    ToastBus.show(message, long)
}
