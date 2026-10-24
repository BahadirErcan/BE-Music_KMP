package com.be.music.platform

import com.be.music.BeMusicApplication
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

actual fun datastorePath(name: String): Path {
    val dir = File(BeMusicApplication.app().filesDir, "datastore")
    return File(dir, name).toOkioPath()
}
