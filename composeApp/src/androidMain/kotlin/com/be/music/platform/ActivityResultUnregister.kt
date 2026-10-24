package com.be.music.platform

import androidx.activity.result.ActivityResultRegistry

/** ActivityResultRegistry'nin internal unregister metodu (public API yok) — yansımayla. */
internal fun ActivityResultRegistry.unregisterQuietly(key: String) {
    try {
        val m = this::class.java.methods + this::class.java.declaredMethods
        m.firstOrNull { it.name == "unregister" && it.parameterCount == 1 }?.let {
            it.isAccessible = true
            it.invoke(this, key)
        }
    } catch (_: Throwable) {
    }
}
