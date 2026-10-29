@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.be.music.platform

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun documentsDir(): String {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return documentDirectory?.path ?: ""
}

actual fun datastorePath(name: String): Path {
    val dir = "${documentsDir()}/datastore"
    return "$dir/$name".toPath()
}
