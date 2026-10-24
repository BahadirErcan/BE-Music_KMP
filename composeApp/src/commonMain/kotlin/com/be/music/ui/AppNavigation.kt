package com.be.music.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.be.music.premium.PremiumScreen
import kotlinx.serialization.Serializable

// KMP güvenli tipli rotalar (JetBrains navigation-compose)
@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object EqualizerRoute

@Serializable
data object PremiumRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: Int)

@Serializable
data class SongPickerRoute(val playlistId: Int)

@Serializable
data object YoutubeDownloadRoute

@Serializable
data object LyricsSearchResultsRoute

@Serializable
data object HistoryRoute

@Composable
fun AppNavigation(viewModel: MusicViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            MainMusicScreen(viewModel = viewModel, navController = navController)
        }

        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                navController = navController
            )
        }

        composable<EqualizerRoute> {
            EqualizerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<PremiumRoute> {
            PremiumScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<PlaylistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlaylistDetailRoute>()
            PlaylistDetailScreen(
                viewModel = viewModel,
                playlistId = route.playlistId,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SongPickerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SongPickerRoute>()
            SongPickerScreen(
                viewModel = viewModel,
                playlistId = route.playlistId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<YoutubeDownloadRoute> {
            YoutubeDownloadScreen(
                musicViewModel = viewModel,
                onBack = {
                    // İndirme bittiğinde müzik listesini yenile
                    viewModel.scanMusic(force = true)
                    navController.popBackStack()
                },
                onNavigateToPremium = {
                    navController.navigate(PremiumRoute)
                }
            )
        }

        composable<LyricsSearchResultsRoute> {
            LyricsSearchResultsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onVideoSelected = {
                    // Video seçildikten sonra geri dön
                    viewModel.scanMusic(force = true)
                    navController.popBackStack()
                }
            )
        }

        composable<HistoryRoute> {
            HistoryScreen(
                viewModel = viewModel,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
