package com.be.music.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Ortak toast/uyarı mesaj akışı — desktop ve iOS'ta uygulama içi overlay, Android'de gerçek Toast da gösterilir. */
object ToastBus {
    public val messages = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 16)
    fun show(message: String, long: Boolean = false) {
        messages.tryEmit(ToastEvent(message, long))
    }
}

data class ToastEvent(val message: String, val long: Boolean)

expect fun showToast(message: String, long: Boolean = false)
