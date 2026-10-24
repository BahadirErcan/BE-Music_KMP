package com.be.music.platform

/** Tüm platformlarda (Android, Windows/Linux masaüstü, iOS) çalışan minimal dosya sistemi soyutlaması. */
expect object FS {
    /** Uygulamanın özel veri dizini (filesDir / appData / documents) */
    val appDataDir: String

    /** Kullanıcının varsayılan müzik kütüphanesi dizini (desktop: ~/Music, iOS: Documents, Android: "") */
    val defaultMusicDir: String

    /** Çalma listesi JSON'larının dışa aktarıldığı dizin */
    val playlistExportDir: String

    /** İndirilen şarkıların kaydedildiği dizin (Android: public Music/BE-Music) */
    val musicExportDir: String

    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun readText(path: String): String?
    fun writeText(path: String, content: String): Boolean
    fun appendText(path: String, content: String): Boolean
    fun delete(path: String): Boolean
    fun deleteRecursive(path: String): Boolean
    fun move(from: String, to: String): Boolean
    fun copyFile(from: String, to: String): Boolean
    fun lastModifiedMs(path: String): Long
    fun setLastModified(path: String, ms: Long): Boolean
    fun fileSize(path: String): Long

    /** dir altındaki dosyaların tam yol listesi */
    fun listFiles(dir: String): List<String>
    /** dir altındaki dizinlerin tam yol listesi */
    fun listDirs(dir: String): List<String>
    fun makeDirs(dir: String): Boolean

    fun fileName(path: String): String
    fun baseName(path: String): String
    fun extension(path: String): String
    fun parentDir(path: String): String?
    fun join(dir: String, child: String): String
}

/** STL olmayan ortak yol yardımcıları (SDL kısıtlaması olmadan). */
fun withoutExtension(path: String): String {
    val dot = path.lastIndexOf('.')
    val slash = path.lastIndexOf('/')
    return if (dot > slash) path.substring(0, dot) else path
}
