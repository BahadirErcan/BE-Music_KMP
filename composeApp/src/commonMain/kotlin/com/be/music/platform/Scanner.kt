package com.be.music.platform

/** Platform taramasından gelen, DB'ye yazılmadan önceki şarkı bilgi paketi. */
data class ScannedSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    /** saniye cinsinden (Android MediaStore DATE_MODIFIED veya dosya mtime) */
    val dateModifiedSec: Long,
    /** Android'de content URI, masaüstü/iOS'ta null (path kullanılır) */
    val uriString: String? = null
)

/**
 * Yerel müzik dosyalarını platforma özgü şekilde tarar.
 * - Android: MediaStore
 * - Masaüstü: ayarlardaki kütüphane dizinlerini gezerek (ffmpeg ile süre)
 * - iOS: uygulamanın Documents altı
 */
expect object MusicScanner {
    suspend fun scanSongs(): List<ScannedSong>
    /** Android: dış dosyadaki eski Music/pl klasörleri; diğer platformlar: boş */
    suspend fun legacyPlaylistDirs(): List<String>
}

/** Şarkı dosyasıyla platforma özgü işlemler (metadata yazma, adlandırma, sistemden çıkarma). */
expect object SongIO {
    suspend fun writeMetadata(path: String, title: String, artist: String, album: String)
    /** Dosyayı MediaStore/Finder gibi sistem kataloglarınından da çıkarır (Android only; diğerleri no-op) */
    suspend fun removeFromSystemCatalog(path: String)
    /** Dosya adını title'a göre ayarlar; başarılıysa yeni yolu döndürür, başarısızsa orijinalini */
    suspend fun renameForTitle(path: String, safeTitle: String): String?
    /** Dosyayı sistem kataloğuna kaydeder; Android MediaStore URI listesi, masaüstü/iOS: ScannedSong listesi */
    suspend fun registerDownloaded(paths: List<String>): List<ScannedSong>
    /** İndirilen dosyaların albüm metadata'sını düzeltir (jaudiotagger; iOS: no-op) */
    suspend fun fixAlbumMetadata(path: String, playlistName: String?)
}

/** Dosya seçimleri (Android SAF / masaüstü dialog / iOS document picker). */
expect object FilePicker {
    /** Tek bir JSON/veri dosyası seçtikten sonra okunabilir bir yol döndürür, iptal ise null */
    suspend fun pickJsonFile(): String?
    /** Dizin seçimi (desktop kütüphane dizin ayarı, iOS: documents) */
    suspend fun pickMediaDirectory(): String?
}

/** Harici URL'yi sistem tarayıcısında açar. */
expect fun openExternalUrl(url: String)

