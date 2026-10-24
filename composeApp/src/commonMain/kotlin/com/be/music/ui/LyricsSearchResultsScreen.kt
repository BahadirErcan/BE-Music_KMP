package com.be.music.ui
import org.jetbrains.compose.resources.stringResource
import com.be.music.res.Res
import com.be.music.res.back
import com.be.music.res.cancel
import com.be.music.res.cancel_download
import com.be.music.res.checking_subtitles
import com.be.music.res.downloading_subtitles
import com.be.music.res.no_results_found
import com.be.music.res.no_subtitles_available
import com.be.music.res.select_auto_subtitle_lang
import com.be.music.res.select_manual_subtitle
import com.be.music.res.select_subtitle
import com.be.music.res.select_video_for_lyrics
import com.be.music.res.subtitle_type_auto
import com.be.music.res.subtitle_type_manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.be.music.youtube.YoutubeVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchResultsScreen(
    onBack: () -> Unit,
    onVideoSelected: (videoId: String) -> Unit,
    viewModel: MusicViewModel
) {
    val lyricsSearchResults by viewModel.lyricsSearchResults.collectAsStateWithLifecycle()
    val lyricsSearchLoading by viewModel.lyricsSearchLoading.collectAsStateWithLifecycle()
    val lyricsSearchError by viewModel.lyricsSearchError.collectAsStateWithLifecycle()
    
    val checkingSubtitles by viewModel.checkingSubtitles.collectAsStateWithLifecycle()
    val availableSubtitles by viewModel.availableSubtitles.collectAsStateWithLifecycle()
    val subtitlesError by viewModel.subtitlesError.collectAsStateWithLifecycle()

    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.select_video_for_lyrics)) },
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
                    .padding(innerPadding)
        ) {
            if (lyricsSearchLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (lyricsSearchError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lyricsSearchError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = onBack) {
                            Text(stringResource(Res.string.back))
                        }
                    }
                }
            } else if (lyricsSearchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(Res.string.no_results_found))
                        Button(onClick = onBack) {
                            Text(stringResource(Res.string.back))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(lyricsSearchResults) { video ->
                        SearchResultItem(
                            video = video,
                            isDownloading = isDownloading,
                            onSelect = {
                                selectedVideoId = video.id
                                viewModel.checkSubtitlesForVideo(video.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedVideoId != null) {
        AlertDialog(
            onDismissRequest = { 
                if (!isDownloading && !checkingSubtitles) {
                    selectedVideoId = null 
                }
            },
            title = { 
                Text(
                    if (availableSubtitles.isNotEmpty() && availableSubtitles.first().isAutomatic) {
                        stringResource(Res.string.select_auto_subtitle_lang)
                    } else if (availableSubtitles.isNotEmpty()) {
                        stringResource(Res.string.select_manual_subtitle)
                    } else {
                        stringResource(Res.string.select_subtitle)
                    }
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (checkingSubtitles || isDownloading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (isDownloading) stringResource(Res.string.downloading_subtitles) else stringResource(Res.string.checking_subtitles),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (subtitlesError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                subtitlesError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (availableSubtitles.isEmpty()) {
                        Text(stringResource(Res.string.no_subtitles_available), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableSubtitles) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isDownloading = true
                                            viewModel.downloadSelectedSubtitle(selectedVideoId!!, option) { success ->
                                                isDownloading = false
                                                if (success) {
                                                    val videoId = selectedVideoId
                                                    selectedVideoId = null
                                                    onVideoSelected(videoId ?: "")
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(option.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                if (option.isAutomatic) stringResource(Res.string.subtitle_type_auto) else stringResource(Res.string.subtitle_type_manual),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (option.isAutomatic)
                                                MaterialTheme.colorScheme.surfaceVariant
                                            else
                                                MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = if (option.isAutomatic)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (isDownloading || checkingSubtitles) {
                    TextButton(onClick = {
                        viewModel.cancelLyricsDownload()
                        isDownloading = false
                        selectedVideoId = null
                    }) {
                        Text(stringResource(Res.string.cancel_download))
                    }
                } else {
                    TextButton(onClick = { selectedVideoId = null }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun SearchResultItem(
    video: YoutubeVideo,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDownloading) { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.durationSeconds > 0) {
                        Text(
                            text = "• ${formatDurationSeconds(video.durationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
