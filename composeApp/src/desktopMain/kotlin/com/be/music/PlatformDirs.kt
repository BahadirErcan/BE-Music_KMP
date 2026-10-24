package com.be.music

import okio.Path
import java.io.File

/**
 * Masaüstü dizinleri:
 * - appDataDir: kullanıcı veri dizini (~/.local/share/BE-Music / %APPDATA%/BE-Music / ~/Library/Application Support/BE-Music)
 * - defaultMusicDir: platformun müzik klasörü
 */
object PlatformDirs {
    val osName: String = run {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            else -> "linux"
        }
    }

    val userHome: File get() = File(System.getProperty("user.home"))

    val appDataDir: File = when (osName) {
        "windows" -> File(
            System.getenv("APPDATA") ?: File(userHome, "AppData").path,
            "BE-Music"
        )
        "macos" -> File(userHome, "Library/Application Support/BE-Music")
        else -> File(
            System.getenv("XDG_DATA_HOME") ?: File(userHome, ".local/share").path,
            "BE-Music"
        )
    }

    val defaultMusicDir: File = when (osName) {
        "windows" -> File(userHome, "Music")
        "macos" -> File(userHome, "Music")
        else -> File(userHome, "Music")
    }

    init {
        try { appDataDir.mkdirs() } catch (_: Exception) {}
    }
}
