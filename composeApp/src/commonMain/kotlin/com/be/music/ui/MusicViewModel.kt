@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.be.music.ui
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.be.music.res.Res
import com.be.music.res.create_8d_failed
import com.be.music.res.done
import com.be.music.res.create_8d_result
import com.be.music.res.create_8d_running
import com.be.music.res.lyrics_error
import com.be.music.res.lyrics_not_found
import com.be.music.res.lyrics_not_found_for_song
import com.be.music.res.subtitle_check_error
import com.be.music.res.subtitle_download_error
import com.be.music.res.subtitle_save_error
import com.be.music.res.target_song_not_found
import com.be.music.res.video_info_error

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.be.music.data.FilterSettings
import com.be.music.data.Album
import com.be.music.data.Artist
import com.be.music.data.AppPreferences
import com.be.music.data.Playlist
import com.be.music.data.MusicRepository
import com.be.music.data.Song
import com.be.music.premium.PremiumManager
import com.be.music.platform.FS
import com.be.music.platform.FilePicker
import com.be.music.player.PlayerRepeat
import com.be.music.player.PlayerTrack
import com.be.music.player.QueueAwarePlayerBridge
import com.be.music.player.RestorablePlayerBridge
import com.be.music.platform.ToastBus
import com.be.music.platform.artworkUriForSong
import com.be.music.player.createPlayerBridge
import com.be.music.platform.openExternalUrl
import com.be.music.player.EightDProcessor
import com.be.music.player.PlayerBridge
import com.be.music.youtube.LyricLine
import com.be.music.youtube.SearchToken
import com.be.music.youtube.VttParser
import com.be.music.youtube.YoutubeSearchEngine
import com.be.music.youtube.YtDlp
import com.be.music.platform.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class SortOrder { NAME, DATE, DURATION }
enum class ThemeStyle { LIGHT, DARK, SYSTEM }

class MusicViewModel(
    val repository: MusicRepository
) : ViewModel() {

    private val sharingStarted = SharingStarted.WhileSubscribed(5000)

    val filterSettingsState: StateFlow<FilterSettings> = repository.filterSettings
        .stateIn(viewModelScope, sharingStarted, FilterSettings())

    val themeStyle: StateFlow<ThemeStyle> = repository.filterSettings
        .map { settings ->
            when (settings.themeMode) {
                "LIGHT" -> ThemeStyle.LIGHT
                "DARK" -> ThemeStyle.DARK
                else -> ThemeStyle.SYSTEM
            }
        }.stateIn(viewModelScope, sharingStarted, ThemeStyle.SYSTEM)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE)
    val sortOrder = _sortOrder.asStateFlow()

    private val _sortReverse = MutableStateFlow(false)
    val sortReverse = _sortReverse.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _navigationBarHeight = MutableStateFlow(0)
    val navigationBarHeight = _navigationBarHeight.asStateFlow()

    private val _isThreeButtonNav = MutableStateFlow(false)
    val isThreeButtonNav = _isThreeButtonNav.asStateFlow()

    fun updateNavigationBarHeight(height: Int) {
        if (_navigationBarHeight.value != height) {
            _navigationBarHeight.value = height
        }
    }

    fun updateIsThreeButtonNav(isThreeButton: Boolean) {
        if (_isThreeButtonNav.value != isThreeButton) {
            _isThreeButtonNav.value = isThreeButton
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val songsState: StateFlow<List<Song>> = combine(repository.filteredSongs, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        var filtered = if (query.isBlank()) list else list.filter { it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }

        filtered = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.DATE -> filtered.sortedByDescending { it.dateModified }
            SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
        }

        if (reverse) filtered = filtered.reversed()
        filtered
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val artistsState: StateFlow<List<Artist>> = combine(repository.allArtists, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val albumsState: StateFlow<List<Album>> = combine(repository.allAlbums, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    val playlistsState: StateFlow<List<Playlist>> = combine(repository.allPlaylists, searchQuery, sortOrder, sortReverse) { list, query, order, reverse ->
        val filtered = if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
        var sorted = when (order) {
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        if (reverse) sorted = sorted.reversed()
        sorted
    }.stateIn(viewModelScope, sharingStarted, emptyList())

    // ===== Oynatıcı state
    val bridge: PlayerBridge = createPlayerBridge()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    val isPlaying = bridge.playing

    val currentPosition = bridge.positionMs
    val duration = bridge.durationMs

    private val _nextSong = MutableStateFlow<Song?>(null)
    val nextSong = _nextSong.asStateFlow()

    val shuffleMode = bridge.shuffleEnabled
    val repeatMode = bridge.repeatMode.map { mode -> mode }.stateIn(viewModelScope, sharingStarted, PlayerRepeat.OFF)

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()
    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive = _sleepTimerActive.asStateFlow()

    // Lyric states
    private val _lyricSearchTargetSong = MutableStateFlow<Song?>(null)
    val lyricSearchTargetSong = _lyricSearchTargetSong.asStateFlow()

    private val _lyricsSearchResults = MutableStateFlow<List<com.be.music.youtube.YoutubeVideo>>(emptyList())
    val lyricsSearchResults = _lyricsSearchResults.asStateFlow()
    private val _lyricsSearchLoading = MutableStateFlow(false)
    val lyricsSearchLoading = _lyricsSearchLoading.asStateFlow()
    private val _lyricsSearchError = MutableStateFlow<String?>(null)
    val lyricsSearchError = _lyricsSearchError.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<SubtitleOption>>(emptyList())
    val availableSubtitles = _availableSubtitles.asStateFlow()
    private val _checkingSubtitles = MutableStateFlow(false)
    val checkingSubtitles = _checkingSubtitles.asStateFlow()
    private val _subtitlesError = MutableStateFlow<String?>(null)
    val subtitlesError = _subtitlesError.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    // 8D states
    private val _is8DProcessing = MutableStateFlow(false)
    val is8DProcessing = _is8DProcessing.asStateFlow()

    private val _eightDStatus = MutableStateFlow<String?>(null)
    val eightDStatus = _eightDStatus.asStateFlow()

    val playHistory: StateFlow<List<com.be.music.data.HistoryDay>> = repository.playHistoryManager.history

    fun clearPlayHistory() {
        viewModelScope.launch { repository.playHistoryManager.clearHistory() }
    }

    private var lastPlaybackSaveTime = 0L

    private fun maybeSavePlaybackState() {
        val song = _currentSong.value ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastPlaybackSaveTime >= 5000) {
            lastPlaybackSaveTime = now
            savePlaybackState(song.id, bridge.positionMs.value)
        }
    }

    private fun savePlaybackState(songId: Long, positionMs: Long) {
        viewModelScope.launch {
            AppPreferences.savePlaybackState(songId, positionMs.coerceAtLeast(0L))
        }
    }

    private var lastRestoreCheck = false

    private fun restoreLastPlayback() {
        viewModelScope.launch {
            if (lastRestoreCheck) return@launch
            lastRestoreCheck = true
            val songId = AppPreferences.getLastSongId()
            if (songId <= 0L) return@launch
            val song = repository.getSongsByIds(listOf(songId)).firstOrNull() ?: return@launch
            _currentSong.value = song
            val savedPos = AppPreferences.getLastPosition()
            // positionMs state'ini başlangıç konumuna alacak şekilde bridge'e sinyal yolla; bridge bu değeri actüel play sırasında kullanır
            (bridge as? RestorablePlayerBridge)?.restoreState(song.id, savedPos, song.duration)
        }
    }

    init {
        viewModelScope.launch {
            val settings = repository.filterSettings.first()
            _sortOrder.value = when (settings.songSortOrder) {
                "DATE" -> SortOrder.DATE
                "DURATION" -> SortOrder.DURATION
                else -> SortOrder.NAME
            }
            _sortReverse.value = settings.songSortReverse
        }
        restoreLastPlayback()

        viewModelScope.launch {
            bridge.currentTrackId.collect { id ->
                val song = id?.let { repository.getSongsByIds(listOf(it)).firstOrNull() }
                _currentSong.value = song ?: _currentSong.value
                if (song != null) {
                    loadLyricsForSong(song)
                    updateNextSong()
                    repository.playHistoryManager.addEntry(song)
                    savePlaybackState(song.id, 0L)
                }
            }
        }
        viewModelScope.launch {
            bridge.playing.onEach { playing ->
                if (!playing) {
                    // Madde 4: duraklatıldığında pozisyonu hemen kaydet
                    _currentSong.value?.let { savePlaybackState(it.id, bridge.positionMs.value) }
                    stopProgressTracker()
                } else {
                    startProgressTracker()
                }
            }.collect {}
        }
    }

    // Madde 14: Sonraki şarkıyı güncelle
    private fun updateNextSong() {
        val snapshot = (bridge as? QueueAwarePlayerBridge)?.queueIds?.value
        val idx = (bridge as? QueueAwarePlayerBridge)?.currentIndex?.value ?: return
        if (snapshot == null) {
            _nextSong.value = null
            return
        }
        if (idx < snapshot.size - 1) {
            val nextId = snapshot[idx + 1]
            viewModelScope.launch {
                _nextSong.value = repository.getSongsByIds(listOf(nextId)).firstOrNull()
            }
        } else {
            _nextSong.value = null
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                maybeSavePlaybackState()
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() { progressJob?.cancel() }

    fun playSong(song: Song, list: List<Song>) {
        val tracks = list.map { s ->
            PlayerTrack(
                id = s.id,
                uri = s.uriString,
                title = s.title,
                artist = s.artistName,
                album = s.albumName,
                artUri = artworkUriForSong(s.id, s.path),
                durationMs = s.duration
            )
        }
        val index = list.indexOf(song).coerceAtLeast(0)
        bridge.setQueue(tracks, index, 0L)
        // If playing a list (playlist) enable repeat-all so the playlist loops
        if (tracks.size > 1) {
            bridge.setRepeat(PlayerRepeat.ALL)
        }
    }

    fun playPause() {
        // Restore edilen şarkı için oturum boşsa — kaydedilen pozisyondan devam et
        val idleQueue = (bridge as? QueueAwarePlayerBridge)?.queueIds?.value.isNullOrEmpty()
        if (idleQueue && _currentSong.value != null) {
            val song = _currentSong.value!!
            playSong(song, listOf(song))
            viewModelScope.launch {
                val resumePosition = AppPreferences.getLastPosition().coerceIn(0L, song.duration)
                // engine'in yüklenmesini beklemeden seek gönderilebilir (kuyruk başlangıç pozisyonu olarak sarılır)
                delay(150)
                bridge.seekTo(resumePosition)
            }
            return
        }
        bridge.playPause()
    }

    fun next() = bridge.seekNext()
    fun previous() = bridge.seekPrevious()
    fun seekTo(pos: Long) = bridge.seekTo(pos)

    fun toggleShuffle() {
        bridge.setShuffle(!bridge.shuffleEnabled.value)
    }

    fun toggleRepeat() {
        val nextMode = when (bridge.repeatMode.value) {
            PlayerRepeat.OFF -> PlayerRepeat.ALL
            PlayerRepeat.ALL -> PlayerRepeat.ONE
            PlayerRepeat.ONE -> PlayerRepeat.OFF
        }
        bridge.setRepeat(nextMode)
    }

    fun setTab(index: Int) { _selectedTab.value = index }
    fun scanMusic(force: Boolean) = viewModelScope.launch { repository.scanLocalMusic(force) }

    // Playlist Management
    fun createPlaylist(name: String, songIds: List<Long>, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val playlist = repository.createPlaylist(name, songIds)
            onCreated(playlist.id)
        }
    }

    fun updatePlaylistSongs(playlist: Playlist, songIds: List<Long>) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val stamps = playlist.songAddedAt.filterKeys { it in songIds }.toMutableMap()
            songIds.filter { !stamps.containsKey(it) }.forEachIndexed { i, id -> stamps[id] = now + i }
            repository.updatePlaylist(playlist.copy(songIds = songIds, songAddedAt = stamps))
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val updatedIds = (playlist.songIds + songId).distinct()
            val stamps = playlist.songAddedAt.toMutableMap()
            if (!stamps.containsKey(songId)) stamps[songId] = now
            repository.updatePlaylist(playlist.copy(songIds = updatedIds, songAddedAt = stamps))
        }
    }

    fun addSelectedSongsToPlaylist(playlist: Playlist) {
        val ids = _selectedSongIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val updatedIds = (playlist.songIds + ids).distinct()
            val stamps = playlist.songAddedAt.toMutableMap()
            ids.filter { !stamps.containsKey(it) }.forEachIndexed { i, id -> stamps[id] = now + i }
            repository.updatePlaylist(playlist.copy(songIds = updatedIds, songAddedAt = stamps))
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Long) {
        viewModelScope.launch {
            val playlist = playlistsState.value.find { it.id == playlistId } ?: return@launch
            val updatedIds = playlist.songIds.filter { it != songId }
            val stamps = playlist.songAddedAt - songId
            repository.updatePlaylist(playlist.copy(songIds = updatedIds, songAddedAt = stamps))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun invertPlaylistSongs(playlist: Playlist) {
        viewModelScope.launch {
            val currentIds = playlist.songIds.toSet()
            val allSongIds = songsState.value.map { it.id }
            val invertedIds = allSongIds.filter { it !in currentIds }
            val now = Clock.System.now().toEpochMilliseconds()
            val stamps = invertedIds.withIndex().associate { (i, id) -> id to now + i }
            repository.updatePlaylist(playlist.copy(songIds = invertedIds, songAddedAt = stamps))
        }
    }

    fun importPlaylists() {
        viewModelScope.launch {
            repository.importPlaylistsFromJson()
            ToastBus.show(getStringResource(com.be.music.res.Res.string.done))
        }
    }

    fun importPlaylistFromUri(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val file = FilePicker.pickJsonFile()
            if (file == null) {
                onComplete(false, "")
                return@launch
            }
            val (success, message) = repository.importPlaylistFromFile(file)
            onComplete(success, message)
        }
    }

    fun exportPlaylist(playlist: Playlist, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val path = repository.exportPlaylistFilePath(playlist)
            onComplete(path != null, path)
        }
    }

    // Settings & Selection
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) _selectedSongIds.value = emptySet()
    }

    fun toggleSongSelection(songId: Long) {
        val current = _selectedSongIds.value.toMutableSet()
        if (current.contains(songId)) current.remove(songId) else current.add(songId)
        _selectedSongIds.value = current
    }

    fun clearSelection() {
        _selectedSongIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun toggleArtistSelection(artistSongs: List<Song>) {
        val ids = artistSongs.map { it.id }.toSet()
        val current = _selectedSongIds.value
        if (current.containsAll(ids)) {
            _selectedSongIds.value = current - ids
        } else {
            _selectedSongIds.value = current + ids
        }
    }

    fun toggleAlbumSelection(albumSongs: List<Song>) {
        val ids = albumSongs.map { it.id }.toSet()
        val current = _selectedSongIds.value
        if (current.containsAll(ids)) {
            _selectedSongIds.value = current - ids
        } else {
            _selectedSongIds.value = current + ids
        }
    }

    fun playArtistSongs(artistSongs: List<Song>) {
        if (artistSongs.isEmpty()) return
        bridge.setRepeat(PlayerRepeat.ALL)
        playSong(artistSongs.first(), artistSongs)
    }

    fun playAlbumSongs(albumSongs: List<Song>) {
        if (albumSongs.isEmpty()) return
        bridge.setRepeat(PlayerRepeat.ALL)
        playSong(albumSongs.first(), albumSongs)
    }

    fun setTheme(theme: ThemeStyle) {
        viewModelScope.launch {
            val current = filterSettingsState.value
            val newMode = when (theme) {
                ThemeStyle.LIGHT -> "LIGHT"
                ThemeStyle.DARK -> "DARK"
                ThemeStyle.SYSTEM -> "SYSTEM"
            }
            repository.saveFilterSettings(current.copy(themeMode = newMode))
        }
    }

    fun setLanguage(langCode: String) {
        viewModelScope.launch {
            val current = filterSettingsState.value
            repository.saveFilterSettings(current.copy(language = langCode))
            AppPreferences.setLanguage(langCode)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        viewModelScope.launch {
            val current = repository.filterSettings.first()
            repository.saveFilterSettings(
                current.copy(
                    songSortOrder = when (order) {
                        SortOrder.NAME -> "NAME"
                        SortOrder.DATE -> "DATE"
                        SortOrder.DURATION -> "DURATION"
                    }
                )
            )
        }
    }

    fun toggleSortReverse() {
        _sortReverse.value = !_sortReverse.value
        viewModelScope.launch {
            val current = repository.filterSettings.first()
            repository.saveFilterSettings(current.copy(songSortReverse = _sortReverse.value))
        }
    }

    fun updateFilterSettings(settings: FilterSettings) {
        viewModelScope.launch { repository.saveFilterSettings(settings) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerActive.value = false
            _sleepTimerRemaining.value = 0L
            return
        }

        _sleepTimerActive.value = true
        _sleepTimerRemaining.value = minutes * 60L

        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.value = _sleepTimerRemaining.value - 1
            }
            _sleepTimerActive.value = false
            bridge.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerActive.value = false
        _sleepTimerRemaining.value = 0L
    }

    fun searchLyricsForSong(song: Song) {
        _lyricSearchTargetSong.value = song
        viewModelScope.launch {
            _lyricsSearchLoading.value = true
            _lyricsSearchError.value = null
            _lyricsSearchResults.value = emptyList()
            try {
                val artist = if (song.artistName.contains("unknown", ignoreCase = true)) "" else "${song.artistName} "
                val query = "$artist${song.title}".trim()
                val results = withContext(IoDispatcher) {
                    YoutubeSearchEngine.searchPage(query, null).first
                }
                val top10 = results.take(10)
                _lyricsSearchResults.value = top10
                if (top10.isEmpty()) {
                    _lyricsSearchError.value = getStringResource(Res.string.lyrics_not_found)
                }
            } catch (e: Exception) {
                _lyricsSearchError.value = e.message ?: getStringResource(Res.string.lyrics_error)
            } finally {
                _lyricsSearchLoading.value = false
            }
        }
    }

    fun fetchLyricsForCurrentSong() {
        val current = _currentSong.value ?: return
        searchLyricsForSong(current)
    }

    fun checkSubtitlesForVideo(videoId: String) {
        viewModelScope.launch {
            _checkingSubtitles.value = true
            _subtitlesError.value = null
            _availableSubtitles.value = emptyList()
            try {
                val options = withContext(IoDispatcher) {
                    val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                    val jsonStr = YtDlp.dumpJson(videoUrl)
                        ?: throw Exception(getStringResource(Res.string.video_info_error))
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    val subtitlesJson = json["subtitles"]?.jsonObject
                    val autoCaptionsJson = json["automatic_captions"]?.jsonObject
                    val optionsList = mutableListOf<SubtitleOption>()

                    val manualSubtitles = subtitlesJson?.keys?.map { key ->
                        SubtitleOption(key, getLanguageName(key), isAutomatic = false)
                    } ?: emptyList()

                    val autoSubtitles = autoCaptionsJson?.keys?.map { key ->
                        SubtitleOption(key, getLanguageName(key), isAutomatic = true)
                    } ?: emptyList()

                    if (manualSubtitles.isNotEmpty()) {
                        manualSubtitles.sortedBy { it.name }
                    } else {
                        autoSubtitles.sortedBy { it.name }
                    }
                }
                _availableSubtitles.value = options
                if (options.isEmpty()) {
                    _subtitlesError.value = getStringResource(Res.string.lyrics_not_found_for_song)
                }
            } catch (e: Exception) {
                _subtitlesError.value = e.message ?: getStringResource(Res.string.subtitle_check_error)
            } finally {
                _checkingSubtitles.value = false
            }
        }
    }

    private fun getLanguageName(code: String): String {
        return when {
            code.startsWith("tr") -> "Türkçe"
            code.startsWith("en") -> "İngilizce"
            code.startsWith("de") -> "Almanca"
            code.startsWith("fr") -> "Fransızca"
            code.startsWith("es") -> "İspanyolca"
            code.startsWith("it") -> "İtalyanca"
            code.startsWith("ru") -> "Rusça"
            code.startsWith("ja") -> "Japonca"
            code.startsWith("ko") -> "Korece"
            code.startsWith("zh") -> "Çince"
            code.startsWith("az") -> "Azerbaycanca"
            else -> code.uppercase()
        }
    }

    fun downloadSelectedSubtitle(videoId: String, option: SubtitleOption, callback: (Boolean) -> Unit) {
        val targetSong = _lyricSearchTargetSong.value ?: _currentSong.value
        if (targetSong == null) {
            _subtitlesError.value = AppStrings.get(Res.string.target_song_not_found)
            callback(false)
            return
        }
        viewModelScope.launch {
            _lyricsSearchLoading.value = true
            _subtitlesError.value = null
            try {
                val success = withContext(IoDispatcher) {
                    val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                    val parentDir = FS.parentDir(targetSong.path) ?: FS.musicExportDir
                    val songBaseName = targetSong.path.substringAfterLast('/').substringBeforeLast('.')

                    val options = mutableListOf<Pair<String, String>>(
                        "--user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                        "--extractor-args" to "youtube:player-client=android,web",
                        "--skip-download" to ""
                    )
                    if (option.isAutomatic) options.add("--write-auto-sub" to "") else options.add("--write-subs" to "")
                    options.add("--sub-langs" to option.code)
                    options.add("--sub-format" to "vtt")
                    options.add("-o" to "$parentDir/%(title)s.%(ext)s")

                    YtDlp.run(options)

                    val initial = FS.listFiles(parentDir)
                    val wantedSuffix = ".$option.code.vtt"
                    val newFile = FS.listFiles(parentDir).firstOrNull {
                        FS.fileName(it).endsWith(wantedSuffix) && it !in initial
                    }
                    val downloadedSubFile = newFile ?: try {
                        FS.listFiles(parentDir).firstOrNull { FS.fileName(it).endsWith(wantedSuffix) }
                    } catch (e: Exception) { null }

                    val targetSubFile = FS.join(parentDir, "$songBaseName.vtt")
                    if (downloadedSubFile != null) {
                        if (FS.exists(targetSubFile)) FS.delete(targetSubFile)
                        if (FS.move(downloadedSubFile, targetSubFile)) {
                            repository.updateSongLyricsPath(targetSong, targetSubFile)
                            true
                        } else false
                    } else {
                        val directFile = FS.join(parentDir, "$songBaseName.vtt")
                        if (FS.exists(directFile)) {
                            repository.updateSongLyricsPath(targetSong, directFile)
                            true
                        } else {
                            val matched = FS.listFiles(parentDir).firstOrNull {
                                FS.fileName(it).startsWith(songBaseName) && FS.fileName(it).endsWith(".vtt") && FS.fileName(it) != "$songBaseName.vtt"
                            }
                            if (matched != null) {
                                if (FS.exists(targetSubFile)) FS.delete(targetSubFile)
                                if (FS.move(matched, targetSubFile)) {
                                    repository.updateSongLyricsPath(targetSong, targetSubFile)
                                    true
                                } else false
                            } else false
                        }
                    }
                }
                if (success) {
                    val updatedSong = repository.getSongsByIds(listOf(targetSong.id)).firstOrNull()
                    if (updatedSong != null) {
                        if (_currentSong.value?.id == targetSong.id) {
                            _currentSong.value = updatedSong
                        }
                        loadLyricsForSong(updatedSong)
                    }
                    callback(true)
                } else {
                    _subtitlesError.value = getStringResource(Res.string.subtitle_save_error)
                    callback(false)
                }
            } catch (e: Exception) {
                _subtitlesError.value = e.message ?: getStringResource(Res.string.subtitle_download_error)
                callback(false)
            } finally {
                _lyricsSearchLoading.value = false
            }
        }
    }

    fun cancelLyricsDownload() {
        _lyricsSearchLoading.value = false
        _checkingSubtitles.value = false
        _subtitlesError.value = null
        YtDlp.cancelAll()
    }

    private fun loadLyricsForSong(song: Song?) {
        if (song == null) {
            _currentLyrics.value = emptyList()
            return
        }
        viewModelScope.launch(IoDispatcher) {
            try {
                val path = song.lyricsPath ?: run {
                    val basePath = song.path.substringBeforeLast('.')
                    val vttFile = "$basePath.vtt"
                    if (FS.exists(vttFile)) vttFile else null
                }
                if (path != null) {
                    if (FS.exists(path)) {
                        val content = FS.readText(path)
                        if (!content.isNullOrBlank()) {
                            val parsed = VttParser.parse(content)
                            _currentLyrics.value = parsed
                            return@launch
                        }
                    }
                }
                _currentLyrics.value = emptyList()
            } catch (e: Exception) {
                println("[MusicViewModel] Lyrics okuma hatası: $e")
                _currentLyrics.value = emptyList()
            }
        }
    }

    fun create8DVersion(song: Song) {
        viewModelScope.launch {
            if (!EightDProcessor.supported) {
                _eightDStatus.value = getStringResource(Res.string.create_8d_failed)
                return@launch
            }
        }
        if (_is8DProcessing.value) return
        _is8DProcessing.value = true
        _eightDStatus.value = null
        viewModelScope.launch {
            try {
                val output = EightDProcessor.create8DVersion(
                    sourcePath = song.path,
                    songTitle = song.title,
                    songArtist = song.artistName
                ) { progress ->
                    _eightDStatus.value = AppStrings.get(Res.string.create_8d_running) + " %$progress"
                }
                if (output != null) {
                    _eightDStatus.value = getString(Res.string.create_8d_result, FS.fileName(output))
                    scanMusic(force = true)
                } else {
                    _eightDStatus.value = getStringResource(Res.string.create_8d_failed)
                }
            } catch (e: Exception) {
                println("[MusicViewModel] 8D oluşturulamadı: $e")
                _eightDStatus.value = getStringResource(Res.string.create_8d_failed) + ": ${e.message ?: ""}"
            } finally {
                _is8DProcessing.value = false
            }
        }
    }

    fun clearEightDStatus() {
        _eightDStatus.value = null
    }

    fun updateSongMetadata(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch {
            repository.updateSongMetadata(song, title, artist, album)
            val updatedSong = song.copy(title = title, artistName = artist, albumName = album)
            bridge.updateCurrentTrackMetadata(
                PlayerTrack(
                    id = updatedSong.id,
                    uri = updatedSong.uriString,
                    title = updatedSong.title,
                    artist = updatedSong.artistName,
                    album = updatedSong.albumName,
                    artUri = artworkUriForSong(updatedSong.id, updatedSong.path),
                    durationMs = updatedSong.duration
                )
            )
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updatedSong
                loadLyricsForSong(updatedSong)
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    fun deleteSelectedSongs() {
        val ids = _selectedSongIds.value
        viewModelScope.launch {
            val songs = songsState.value.filter { it.id in ids }
            songs.forEach { song ->
                repository.deleteSong(song)
            }
            clearSelection()
        }
    }
}

data class SubtitleOption(
    val code: String,
    val name: String,
    val isAutomatic: Boolean
)
