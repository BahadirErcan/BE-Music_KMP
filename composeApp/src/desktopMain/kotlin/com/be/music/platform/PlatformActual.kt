package com.be.music.platform

import com.be.music.PlatformDirs

actual object Platform {
    actual val target: TargetPlatform = TargetPlatform.DESKTOP
    actual val osFamily: String
        get() = when (PlatformDirs.osName) {
            "windows" -> "windows"
            "linux" -> "linux"
            "macos" -> "macos"
            else -> "desktop"
        }
    actual val appVersionCode: Int = 10000
    actual val appVersionName: String = "1.0.0-kmp"
}
