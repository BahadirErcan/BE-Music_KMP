package com.be.music.platform

actual object Platform {
    actual val target: TargetPlatform = TargetPlatform.IOS
    actual val osFamily: String = "ios"
    actual val appVersionCode: Int = 10000
    actual val appVersionName: String = "1.0.0-kmp"
}
