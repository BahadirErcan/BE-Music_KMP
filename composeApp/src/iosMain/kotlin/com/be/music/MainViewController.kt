package com.be.music

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.be.music.di.appModule
import com.be.music.premium.migrateLegacyPrefsIfNeeded
import com.be.music.ui.AppNavigation
import com.be.music.ui.MusicViewModel
import com.be.music.youtube.YoutubeSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** iOS App kök composablı — Xcode projesinden MainViewController() çağrılır. */
fun MainViewController(): UIViewController {
    startKoin { modules(appModule) }

    appScope.launch {
        try {
            YoutubeSearchEngine.init()
        } catch (e: Exception) {
            println("NewPipe init: $e")
        }
        try {
            migrateLegacyPrefsIfNeeded()
        } catch (e: Exception) {
            println("Prefs migration: $e")
        }
    }

    return ComposeUIViewController {
        Surface(modifier = Modifier.fillMaxSize()) {
            val viewModel: MusicViewModel = koinViewModel()
            Box {
                AppNavigation(viewModel = viewModel)
                ToastOverlay(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** iOS pencere içi toast (Android Toast yerine). */
@Composable
fun ToastOverlay(modifier: Modifier = Modifier) {
    val latest = rememberLatestToast()
    if (latest != null) {
        Box(
            modifier = modifier
                .padding(bottom = 40.dp)
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
                    text = latest,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun rememberLatestToast(): String? {
    val events = com.be.music.platform.ToastBus.messages
    val latest = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlin.String?>(null) }
    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            latest.value = event.message
            delay(4000)
            latest.value = null
        }
    }
    return latest.value
}
