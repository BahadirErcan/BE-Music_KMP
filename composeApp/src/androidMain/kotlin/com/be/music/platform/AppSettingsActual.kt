package com.be.music.platform

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.Context
import com.be.music.BeMusicApplication
import com.be.music.ActivityHolder

actual fun openAppSettings() {
    try {
        val context = BeMusicApplication.app()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        showToast(e.message ?: "error")
    }
}

actual fun onLanguageChanged() {
    // Song language DataStore'a yazıldı (AppPreferences); activity config için eski prefs'e de yaz
    try {
        val activity = ActivityHolder.current
        val ctx = ActivityHolder.appContext ?: activity?.applicationContext
        kotlinx.coroutines.runBlocking {
            val lang = com.be.music.data.AppPreferences.getLanguage()
            ctx?.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putString("app_language", lang)?.apply()
        }
        activity?.recreate()
    } catch (_: Exception) {}
}
