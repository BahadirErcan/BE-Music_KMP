package com.be.music.platform

/** iOS dosya seçimleri: document picker köprüsü kullanıcı Swift koduyla bağlanacak; şimdilik null. */
actual object FilePicker {
    actual suspend fun pickJsonFile(): String? = null
    actual suspend fun pickMediaDirectory(): String? = null
}
