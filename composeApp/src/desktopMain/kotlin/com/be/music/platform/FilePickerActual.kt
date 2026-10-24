package com.be.music.platform

actual object FilePicker {
    actual suspend fun pickJsonFile(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Playlist JSON seç"
            chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            chooser.isAcceptAllFileFilterUsed = false
            chooser.addChoosableFileFilter(object : javax.swing.filechooser.FileFilter() {
                override fun accept(f: java.io.File): Boolean =
                    f.isDirectory || f.name.endsWith(".json")
                override fun getDescription(): String = "Playlist JSON (*.json)"
            })
            val result = chooser.showOpenDialog(null)
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                // Masaüstünde SAF yok: dosyayı kopyalamak gerekmez, orijinal yolu dön
                chooser.selectedFile.absolutePath
            } else null
        } catch (e: Exception) {
            println("[FilePicker] Hata: $e")
            null
        }
    }

    actual suspend fun pickMediaDirectory(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val chooser = javax.swing.JFileChooser()
            chooser.dialogTitle = "Müzik klasörü seç"
            chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            val result = chooser.showOpenDialog(null)
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else null
        } catch (e: Exception) {
            println("[FilePicker] Dizin seçme hatası: $e")
            null
        }
    }
}
