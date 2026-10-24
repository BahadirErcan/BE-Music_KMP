package com.be.music.ui
import org.jetbrains.compose.resources.stringResource
import com.be.music.platform.showToast
import com.be.music.platform.ensureDownloadPermissions
import com.be.music.res.Res
import com.be.music.res.ad_counter_label
import com.be.music.res.add_to_list
import com.be.music.res.back
import com.be.music.res.batch_input_hint
import com.be.music.res.batch_placeholder
import com.be.music.res.cancel
import com.be.music.res.cancel_download
import com.be.music.res.close
import com.be.music.res.download_list_button
import com.be.music.res.download_list_title
import com.be.music.res.download_screen_title
import com.be.music.res.download_started
import com.be.music.res.expand
import com.be.music.res.premium_active
import com.be.music.res.premium_button
import com.be.music.res.premium_quality_required
import com.be.music.res.premium_reward_progress
import com.be.music.res.search
import com.be.music.res.search_or_spotify_hint
import com.be.music.res.spotify_analyzing
import com.be.music.res.spotify_link_detected
import com.be.music.res.spotify_no_songs
import com.be.music.res.spotify_songs_added

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.be.music.premium.PremiumManager
import com.be.music.youtube.YoutubeVideo
import com.be.music.youtube.YoutubeVideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeDownloadScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit = {},
    musicViewModel: MusicViewModel,
    viewModel: YoutubeDownloadViewModel = koinViewModel()
) {
    val searchResults by viewModel.displayedSearchResults.collectAsStateWithLifecycle()
    val fullSearchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    // displayed == full olduğunda da scroll tetikleyicinin yeniden çalışması için boyutu izle
    val displayedResultsCount by remember { derivedStateOf { searchResults.size } }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isFetchingNextPage by viewModel.isFetchingNextPage.collectAsStateWithLifecycle()
    val downloadingVideos by viewModel.downloadingVideos.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val spotifyDownloading by viewModel.spotifyDownloading.collectAsStateWithLifecycle()
    val spotifyError by viewModel.spotifyError.collectAsStateWithLifecycle()
    val spotifyStatus by viewModel.spotifyStatus.collectAsStateWithLifecycle()
    val batchDownloading by viewModel.batchDownloading.collectAsStateWithLifecycle()
    val batchStatus by viewModel.batchStatus.collectAsStateWithLifecycle()
    val batchError by viewModel.batchError.collectAsStateWithLifecycle()
    val settings by musicViewModel.filterSettingsState.collectAsStateWithLifecycle()
    val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle()
    val premiumState by viewModel.premiumState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isBatchSectionExpanded by remember { mutableStateOf(false) }
    var batchInputText by remember { mutableStateOf("") }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var spotifyParseMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.showAdEvent.collect {
            viewModel.showRewardedAd()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.downloadCompleteEvent.collect {
            musicViewModel.scanMusic(force = true)
        }
    }

    LaunchedEffect(listState, displayedResultsCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                // Madde: Listenin sonuna gelindiğinde YouTube'un devammı
                // (sonraki sayfası) getirilir. displayed == full olsa bile
                // loadMoreResults() sonraki sayfayı çeker.
                if ((lastVisibleIndex >= searchResults.lastIndex && lastVisibleIndex >= 0 && searchResults.isNotEmpty())) {
                    viewModel.loadMoreResults()
                }
            }
    }

    val downloadStartedText = stringResource(Res.string.download_started)

    fun handleDownload(videoId: String, title: String, type: String) {
        if (viewModel.isQualityPremium(settings.preferredVideoQuality)) {
            showPremiumDialog = true
            return
        }

        scope.launch {
            if (com.be.music.platform.ensureDownloadPermissions()) {
                viewModel.startDownload(
                    videoId, title, type,
                    settings.preferredVideoQuality,
                    settings.downloadLyricsEnabled
                )
                showToast(downloadStartedText)
            }
        }
    }

    val spotifyAnalyzingText = stringResource(Res.string.spotify_analyzing)
    val noSongsText = stringResource(Res.string.spotify_no_songs)
    val songsAddedTemplate = stringResource(Res.string.spotify_songs_added, 999999)

    fun handleSearchOrSpotify() {
        val query = searchQuery.trim()
        if (query.isBlank()) return

        if (viewModel.isSpotifyUrl(query)) {
            spotifyParseMessage = spotifyAnalyzingText
            scope.launch {
                val resolvedLines = viewModel.resolveBatchInputLines(listOf(query))
                val existingLines = batchInputText.split("\n").filter { it.isNotBlank() }
                val combinedLines = (existingLines + resolvedLines).distinct()
                batchInputText = combinedLines.joinToString("\n")
                spotifyParseMessage = if (resolvedLines.isEmpty()) {
                    noSongsText
                } else {
                    songsAddedTemplate.replace("999999", resolvedLines.size.toString())
                }
            }
        } else {
            viewModel.searchYouTube(query)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.download_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(Res.string.search_or_spotify_hint)) },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                trailingIcon = {
                    IconButton(onClick = { handleSearchOrSpotify() }) {
                        if (viewModel.isSpotifyUrl(searchQuery.trim())) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = stringResource(Res.string.add_to_list))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { handleSearchOrSpotify() })
            )

            Text(
                text = if (premiumState.isPremium && !premiumState.isExpired) {
                    stringResource(Res.string.premium_active)
                } else {
                    stringResource(Res.string.ad_counter_label, downloadCount.toString())
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (premiumState.isPremium && !premiumState.isExpired) {
                    Color(0xFFFFC107)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 2.dp),
            )

            if (!premiumState.isPremium || premiumState.isExpired) {
                Text(
                    text = stringResource(Res.string.premium_reward_progress, premiumState.rewardedAdCount, com.be.music.premium.PremiumState.REWARD_THRESHOLD),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(top = 2.dp),
                )
            }

            if (viewModel.isSpotifyUrl(searchQuery.trim())) {
                Text(
                    text = spotifyParseMessage ?: stringResource(Res.string.spotify_link_detected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            if (isLoading || spotifyDownloading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            // Spotify Playlist Durum/Hata Kartları
            if (spotifyStatus != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = spotifyStatus ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearSpotifyStatus() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            if (spotifyError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = spotifyError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearSpotifyError() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Ayrı Manuel İndirilecekler Listesi Kartı
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.download_list_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        IconButton(onClick = { isBatchSectionExpanded = !isBatchSectionExpanded }) {
                            Icon(
                                imageVector = if (isBatchSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isBatchSectionExpanded) stringResource(Res.string.close) else stringResource(Res.string.expand),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = isBatchSectionExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.batch_input_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = batchInputText,
                                onValueChange = { batchInputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                placeholder = { Text(stringResource(Res.string.batch_placeholder)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (batchDownloading) {
                                    TextButton(onClick = { viewModel.cancelBatchDownload() }) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(Res.string.cancel_download))
                                    }
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val resolvedLines = viewModel.resolveBatchInputLines(batchInputText.split("\n"))
                                            batchInputText = resolvedLines.joinToString("\n")
                                            if (resolvedLines.isNotEmpty()) {
                                                viewModel.startBatchDownload(
                                                    resolvedLines,
                                                    settings.preferredVideoQuality,
                                                    settings.downloadLyricsEnabled,
                                                    settings.parallelDownloadCount
                                                )
                                            }
                                        }
                                    },
                                    enabled = !batchDownloading && batchInputText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(Res.string.download_list_button))
                                }
                            }

                            // Manuel Liste Durum/Hata Kartları
                            if (batchStatus != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = batchStatus ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.clearBatchStatus() }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (batchError != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = batchError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.clearBatchError() }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(searchResults, key = { it.id }) { video ->
                    YoutubeVideoItem(
                        video = video,
                        onDownloadMusic = { handleDownload(video.id, video.title, "m4a") },
                        onDownloadVideo = { handleDownload(video.id, video.title, "mp4") },
                        onCancelMusic = { viewModel.cancelDownload("${video.id}_m4a") },
                        onCancelVideo = { viewModel.cancelDownload("${video.id}_mp4") },
                        isMusicDownloading = downloadingVideos.contains("${video.id}_m4a"),
                        isVideoDownloading = downloadingVideos.contains("${video.id}_mp4"),
                        musicProgress = downloadProgress["${video.id}_m4a"] ?: 0,
                        videoProgress = downloadProgress["${video.id}_mp4"] ?: 0
                    )
                }
                // Madde: sonraki YouTube sayfası yüklenirken listenin altında göstergesi
                if (isFetchingNextPage) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.premium_quality_required))
                }
            },
            text = { Text(stringResource(Res.string.premium_quality_required)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPremiumDialog = false
                        onNavigateToPremium()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color(0xFF1A1A1A))
                ) {
                    Text(stringResource(Res.string.premium_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
