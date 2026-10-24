package com.be.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun documentsDir(): String {
    @OptIn(ExperimentalForeignApi::class)
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory).path ?: ""
}

actual fun datastorePath(name: String): Path {
    val dir = platform.Foundation.NSString.stringWithString(documentsDir() + "/datastore")
        .let { documentsDir() + "/datastore" } // sadece path string
    return "$dir/$name".toPath()
}
