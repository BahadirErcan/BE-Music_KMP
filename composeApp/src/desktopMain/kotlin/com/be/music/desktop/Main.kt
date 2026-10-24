package com.be.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.be.music.data.AppPreferences
import com.be.music.desktop.audio.DesktopAudioEngine
import com.be.music.di.appModule
import com.be.music.premium.migrateLegacyPrefsIfNeeded
import com.be.music.ui.AppNavigation
import com.be.music.ui.MusicViewModel
import com.be.music.youtube.YoutubeSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import java.util.Locale

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun main() {
    // Koin DI
    startKoin { modules(appModule) }

    appScope.launch {
        // Kayıtlı dili JVM'e uygula (compose resources bunu takip eder)
        try {
            val lang = AppPreferences.getLanguage()
            if (lang != "SYSTEM") {
                Locale.setDefault(Locale(lang))
            }
        } catch (_: Exception) {}

        try { YoutubeSearchEngine.init() } catch (e: Exception) { println("NewPipe init: $e") }
        try { migrateLegacyPrefsIfNeeded() } catch (e: Exception) { println("Prefs migration: $e") }
    }

    DesktopAudioEngine.init()

    application {
        Window(
            onCloseRequest = {
                DesktopAudioEngine.release()
                exitApplication()
            },
            title = "BE-Music",
            state = rememberWindowState(),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val viewModel: MusicViewModel = koinViewModel()
                Box {
                    AppNavigation(viewModel = viewModel)
                    ToastOverlay(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/** Masaüstü toast gösterimi (android Toast yerine pencere içi snackbar). */
@Composable
fun ToastOverlay(modifier: Modifier = Modifier) {
    val current = DesktopToast.currentMessage
    LaunchedEffect(current) {
        if (current != null) {
            delay(4000)
            DesktopToast.clear()
        }
    }
    if (current != null) {
        Box(
            modifier = modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = current,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
