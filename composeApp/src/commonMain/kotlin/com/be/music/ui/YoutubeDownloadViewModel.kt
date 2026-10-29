package com.be.music.ui
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.be.music.res.Res
import com.be.music.res.batch_list_empty
import com.be.music.res.batch_preparing
import com.be.music.res.download_progress_success
import com.be.music.res.download_progress_with_fail
import com.be.music.res.spotify_fetching_playlist
import com.be.music.res.unknown_error

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.be.music.premium.PremiumManager
import com.be.music.premium.PremiumState
import com.be.music.youtube.DownloadBus
import com.be.music.youtube.DownloadCounter
import com.be.music.youtube.DownloadLauncher
import com.be.music.youtube.SpotifyTracks
import com.be.music.youtube.YoutubeSearchEngine
import com.be.music.youtube.YoutubeVideo
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YoutubeDownloadViewModel(
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<YoutubeVideo>>(emptyList())
    val searchResults: StateFlow<List<YoutubeVideo>> = _searchResults

    private val _visibleItemsCount = MutableStateFlow(18)
    val visibleItemsCount: StateFlow<Int> = _visibleItemsCount

    private val _displayedSearchResults = combine(_searchResults, _visibleItemsCount) { results, count ->
        results.take(count)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val displayedSearchResults: StateFlow<List<YoutubeVideo>> = _displayedSearchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var lastSearchQuery: String? = null
    private var currentPageToken: com.be.music.youtube.SearchToken? = null
    private val _isFetchingNextPage = MutableStateFlow(false)
    val isFetchingNextPage: StateFlow<Boolean> = _isFetchingNextPage

    /** Composite key: "videoId_type" */
    private val _downloadingVideos = MutableStateFlow<Set<String>>(emptySet())
    val downloadingVideos: StateFlow<Set<String>> = _downloadingVideos

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress

    private val _spotifyDownloading = MutableStateFlow(false)
    val spotifyDownloading: StateFlow<Boolean> = _spotifyDownloading

    private val _spotifyError = MutableStateFlow<String?>(null)
    val spotifyError: StateFlow<String?> = _spotifyError

    private val _spotifyStatus = MutableStateFlow<String?>(null)
    val spotifyStatus: StateFlow<String?> = _spotifyStatus

    private val _batchDownloading = MutableStateFlow(false)
    val batchDownloading: StateFlow<Boolean> = _batchDownloading

    private val _batchError = MutableStateFlow<String?>(null)
    val batchError: StateFlow<String?> = _batchError

    private val _batchStatus = MutableStateFlow<String?>(null)
    val batchStatus: StateFlow<String?> = _batchStatus

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount

    private val _showAdEvent = MutableSharedFlow<Unit>()
    val showAdEvent: SharedFlow<Unit> = _showAdEvent

    private val _downloadCompleteEvent = MutableSharedFlow<Unit>()
    val downloadCompleteEvent: SharedFlow<Unit> = _downloadCompleteEvent

    val premiumState: StateFlow<PremiumState> = premiumManager.state

    init {
        viewModelScope.launch {
            _downloadCount.value = DownloadCounter.downloadCount()
        }
        viewModelScope.launch {
            DownloadBus.events.collect { event ->
                when (event) {
                    is DownloadBus.DownloadEvent.SingleProgress -> {
                        _downloadProgress.value = _downloadProgress.value + (event.key to event.progress)
                    }
                    is DownloadBus.DownloadEvent.SingleFinished -> {
                        _downloadingVideos.value = _downloadingVideos.value - event.key
                        _downloadProgress.value = _downloadProgress.value - event.key
                        if (event.success) {
                            val newCount = DownloadCounter.incrementDownloadCount()
                            _downloadCount.value = newCount
                            _downloadCompleteEvent.emit(Unit)
                            if (DownloadCounter.shouldShowAd(premiumManager.isPremium)) {
                                _showAdEvent.emit(Unit)
                            }
                        }
                    }
                    is DownloadBus.DownloadEvent.BatchProgress -> {
                        val text = "${event.downloaded}/${event.total} | ${event.currentTrack} %${event.progress}"
                        if (event.kind == "spotify") {
                            _spotifyStatus.value = text
                        } else {
                            _batchStatus.value = text
                        }
                    }
                    is DownloadBus.DownloadEvent.BatchFinished -> {
                        if (event.kind == "spotify") {
                            _spotifyDownloading.value = false
                            refreshDownloadCount()
                            if (event.success) {
                                _downloadCompleteEvent.emit(Unit)
                                if (DownloadCounter.shouldShowAd(premiumManager.isPremium)) {
                                    _showAdEvent.emit(Unit)
                                }
                            }
                            _spotifyStatus.value = if (event.failed > 0) {
                                getString(Res.string.download_progress_with_fail, event.downloaded, event.total, event.failed)
                            } else {
                                getString(Res.string.download_progress_success, event.downloaded, event.total)
                            }
                            if (!event.success) {
                                _spotifyError.value = event.error ?: getStringResource(Res.string.unknown_error)
                                _spotifyStatus.value = null
                            }
                        } else {
                            _batchDownloading.value = false
                            refreshDownloadCount()
                            if (event.success) {
                                _downloadCompleteEvent.emit(Unit)
                                if (DownloadCounter.shouldShowAd(premiumManager.isPremium)) {
                                    _showAdEvent.emit(Unit)
                                }
                            }
                            _batchStatus.value = if (event.failed > 0) {
                                getString(Res.string.download_progress_with_fail, event.downloaded, event.total, event.failed)
                            } else {
                                getString(Res.string.download_progress_success, event.downloaded, event.total)
                            }
                            if (!event.success) {
                                _batchError.value = event.error ?: getStringResource(Res.string.unknown_error)
                                _batchStatus.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshDownloadCount() {
        _downloadCount.value = DownloadCounter.downloadCount()
    }

    fun loadMoreResults() {
        val currentCount = _visibleItemsCount.value
        if (currentCount < _searchResults.value.size) {
            _visibleItemsCount.value = (currentCount + 18).coerceAtMost(_searchResults.value.size)
            return
        }
        val page = currentPageToken
        val query = lastSearchQuery
        if (page == null || query == null || _isFetchingNextPage.value || _isLoading.value) return
        _isFetchingNextPage.value = true
        viewModelScope.launch {
            try {
                val (items, nextPage) = withContext(IoDispatcher) {
                    YoutubeSearchEngine.searchPage(query, page)
                }
                currentPageToken = nextPage
                val existingIds = _searchResults.value.map { it.id }.toSet()
                val newItems = items.filter { it.id !in existingIds }
                if (newItems.isNotEmpty()) {
                    _searchResults.value = _searchResults.value + newItems
                    _visibleItemsCount.value = _visibleItemsCount.value + newItems.size
                }
            } catch (e: Exception) {
                println("[YoutubeDownloadVM] Sonraki sayfa hatası: $e")
            } finally {
                _isFetchingNextPage.value = false
            }
        }
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            lastSearchQuery = query
            currentPageToken = null
            try {
                val (items, nextPage) = withContext(IoDispatcher) {
                    YoutubeSearchEngine.searchPage(query, null)
                }
                _searchResults.value = items
                currentPageToken = nextPage
                _visibleItemsCount.value = 18
            } catch (e: Exception) {
                println("[YoutubeDownloadVM] Arama hatası: $e")
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startDownload(videoId: String, videoTitle: String, type: String, quality: String, downloadLyrics: Boolean) {
        val compositeKey = "${videoId}_${type}"
        _downloadingVideos.value = _downloadingVideos.value + compositeKey
        _downloadProgress.value = _downloadProgress.value + (compositeKey to 0)
        DownloadLauncher.single(videoId, videoTitle, type, quality, downloadLyrics)
    }

    fun isSpotifyUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.contains("open.spotify.com/") ||
            trimmed.contains("spotify.com/playlist/") ||
            trimmed.contains("spotify.com/track/") ||
            trimmed.contains("spotify.link/") ||
            trimmed.contains("spotify:playlist:") ||
            trimmed.contains("spotify:track:")
    }

    fun isSpotifyPlaylistUrl(url: String): Boolean = isSpotifyUrl(url)

    suspend fun resolveBatchInputLines(rawLines: List<String>): List<String> = withContext(IoDispatcher) {
        val resolvedLines = mutableListOf<String>()
        rawLines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach
            if (isSpotifyUrl(trimmed)) {
                resolvedLines.addAll(SpotifyTracks.parseUrl(trimmed).map { "${it.name} - ${it.artist}" })
            } else {
                resolvedLines.add(trimmed)
            }
        }
        resolvedLines.distinct()
    }

    fun startSpotifyPlaylistDownload(url: String, quality: String, downloadLyrics: Boolean) {
        _spotifyDownloading.value = true
        _spotifyError.value = null
        viewModelScope.launch {
            _spotifyStatus.value = getStringResource(Res.string.spotify_fetching_playlist)
        }
        DownloadLauncher.spotify(url, quality, downloadLyrics)
    }

    fun clearSpotifyError() { _spotifyError.value = null }
    fun clearSpotifyStatus() { _spotifyStatus.value = null }

    fun startBatchDownload(trackLines: List<String>, quality: String, downloadLyrics: Boolean, parallelLimit: Int = 3) {
        val validLines = trackLines.filter { it.isNotBlank() }
        if (validLines.isEmpty()) {
            viewModelScope.launch {
                _batchError.value = getStringResource(Res.string.batch_list_empty)
            }
            return
        }

        _batchDownloading.value = true
        _batchError.value = null
        viewModelScope.launch {
            _batchStatus.value = getStringResource(Res.string.batch_preparing)
        }
        DownloadLauncher.batch(validLines, quality, downloadLyrics, parallelLimit)
    }

    fun clearBatchError() { _batchError.value = null }
    fun clearBatchStatus() { _batchStatus.value = null }

    fun cancelDownload(compositeKey: String) {
        DownloadLauncher.cancelSingle(compositeKey)
        _downloadingVideos.value = _downloadingVideos.value - compositeKey
        _downloadProgress.value = _downloadProgress.value - compositeKey
    }

    fun cancelSpotifyDownload() {
        DownloadLauncher.cancelSpotify()
        _spotifyDownloading.value = false
        _spotifyStatus.value = null
    }

    fun cancelBatchDownload() {
        DownloadLauncher.cancelBatch()
        _batchDownloading.value = false
        _batchStatus.value = null
    }

    fun showRewardedAd() {
        com.be.music.ads.AdsProvider.showRewarded(
            onReward = {
                viewModelScope.launch {
                    premiumManager.onRewardedAdCompleted()
                    DownloadCounter.resetDownloadCount()
                    refreshDownloadCount()
                }
            },
            onDismiss = {}
        )
    }

    fun isQualityPremium(quality: String): Boolean {
        return premiumManager.isQualityPremium(quality) && !premiumManager.isPremium
    }
}
