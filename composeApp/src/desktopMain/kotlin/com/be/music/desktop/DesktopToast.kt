package com.be.music.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.swing.SwingUtilities

/** Masaüstü toast: pencere içinde ToastOverlay ile gösterilir. */
object DesktopToast {
    var currentMessage: String? by mutableStateOf(null)
        private set

    var visibleUntil: Long = 0
        private set

    fun show(message: String) {
        SwingUtilities.invokeLater {
            currentMessage = message
            visibleUntil = System.currentTimeMillis() + 4000
        }
    }

    fun clear() {
        currentMessage = null
    }
}
