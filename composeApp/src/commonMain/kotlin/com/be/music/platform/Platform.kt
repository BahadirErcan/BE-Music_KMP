package com.be.music.platform

enum class TargetPlatform { ANDROID, DESKTOP, IOS }

expect object Platform {
    val target: TargetPlatform
    /** "android", "windows", "linux", "macos", "ios" */
    val osFamily: String
    val appVersionCode: Int
    val appVersionName: String
}

val Platform.isDesktop: Boolean get() = target == TargetPlatform.DESKTOP
val Platform.isMobile: Boolean get() = target != TargetPlatform.DESKTOP
