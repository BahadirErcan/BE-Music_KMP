package com.be.music

import android.app.Application
import android.content.Context
import android.widget.Toast
import android.util.Log
import com.be.music.ads.AdsProvider
import com.be.music.di.appModule
import com.be.music.premium.migrateLegacyPrefsIfNeeded
import com.be.music.youtube.YoutubeSearchEngine
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Android Activity holder — içi boşluktaki reklam gösterimi, dosya seçici vb. için */
object ActivityHolder {
    @Volatile
    var current: androidx.activity.ComponentActivity? = null

    @Volatile
    var appContext: Context? = null
}

class BeMusicApplication : Application() {

    companion object {
        lateinit var instance: BeMusicApplication
            private set
        fun app(): Context = instance.applicationContext
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        ActivityHolder.appContext = this

        // Koin DI
        startKoin {
            androidContext(this@BeMusicApplication)
            modules(appModule)
        }

        // 1. Kütüphane Başlatma (yt-dlp / ffmpeg Android motoru)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            applicationScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@BeMusicApplication)
                    Log.d("BE-Music", "YoutubeDL başarıyla güncellendi.")
                } catch (e: Exception) {
                    Log.e("BE-Music", "Motor güncelleme hatası: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BE-Music", "Başlatma Hatası", e)
        }

        // 2. NewPipe Extractor Invalidate arama başlatma
        try {
            YoutubeSearchEngine.init()
        } catch (e: Exception) {
            Log.e("NewPipe", "Başlatma Hatası", e)
        }

        // 3. Unity Ads (Android)
        AdsProvider.initialize()

        // 4. Legacy SharedPreferences -> DataStore taşınması (bir kere)
        applicationScope.launch(Dispatchers.IO) {
            try {
                migrateLegacyPrefsIfNeeded()
            } catch (e: Exception) {
                Log.e("BE-Music", "Prefs taşıma hatası: ${e.message}")
            }
        }
    }
}
