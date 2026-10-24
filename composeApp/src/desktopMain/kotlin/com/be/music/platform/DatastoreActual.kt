package com.be.music.platform

import com.be.music.PlatformDirs
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun datastorePath(name: String): Path {
    val dir = java.io.File(PlatformDirs.appDataDir, "datastore")
    return java.io.File(dir, name).toOkioPath()
}
