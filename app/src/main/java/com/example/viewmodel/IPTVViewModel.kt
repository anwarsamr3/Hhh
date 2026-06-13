package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IPTVRepository(application)
    val settingsManager = SettingsManager(application)

    // --- State Observables ---
    val playlists: StateFlow<List<IPTVPlaylist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPlaylist: StateFlow<IPTVPlaylist?> = repository.selectedPlaylist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTheme: StateFlow<String> = settingsManager.theme
    val currentLang: StateFlow<String> = settingsManager.lang
    val autoUpdate: StateFlow<Boolean> = settingsManager.autoUpdate

    // --- Tab and Filter States ---
    private val _currentTab = MutableStateFlow("HOME") // "HOME", "LIVE", "MOVIE", "SERIES", "FAVORITES", "SETTINGS"
    val currentTab: StateFlow<String> = _currentTab

    private val _selectedCategoryId = MutableStateFlow<String>("")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // --- Interactive Playback State ---
    private val _activeChannel = MutableStateFlow<IPTVChannel?>(null)
    val activeChannel: StateFlow<IPTVChannel?> = _activeChannel

    private val _activeCategoryChannels = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val activeCategoryChannels: StateFlow<List<IPTVChannel>> = _activeCategoryChannels

    // --- Status States ---
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    init {
        viewModelScope.launch {
            // Seed a high-quality free test playlist on first launch
            repository.seedMockPlaylistsIfEmpty()
        }
    }

    fun getFavoritesFlow(playlistId: Int): Flow<List<IPTVChannel>> {
        return repository.getFavoritesFlow(playlistId)
    }

    // --- Categories Flow ---
    val categoriesFlow: Flow<List<IPTVCategory>> = selectedPlaylist.flatMapLatest { playlist ->
        _currentTab.flatMapLatest { tab ->
            if (playlist == null || tab == "HOME" || tab == "FAVORITES" || tab == "SETTINGS") {
                flowOf(emptyList())
            } else {
                repository.getCategoriesFlow(playlist.id, tab)
            }
        }
    }

    // --- Channels Flow ---
    val channelsFlow: Flow<List<IPTVChannel>> = combine(
        selectedPlaylist,
        _currentTab,
        _selectedCategoryId,
        _searchQuery
    ) { playlist, tab, catId, query ->
        if (playlist == null) return@combine emptyList<IPTVChannel>()
        
        when {
            // Searched streams
            query.isNotEmpty() && tab != "HOME" && tab != "SETTINGS" -> {
                repository.searchChannelsFlow(playlist.id, tab, query).first()
            }
            // Favorites view
            tab == "FAVORITES" -> {
                repository.getFavoritesFlow(playlist.id).first()
            }
            // Categories filtering
            tab != "HOME" && tab != "SETTINGS" && catId.isNotEmpty() -> {
                val list = repository.getChannelsFlow(playlist.id, tab, catId).first()
                // Update active list for quick zapping in-player
                _activeCategoryChannels.value = list
                list
            }
            else -> emptyList()
        }
    }

    // --- Actions ---
    fun selectTab(tabName: String) {
        _currentTab.value = tabName
        _selectedCategoryId.value = "" // Reset category selection
        _searchQuery.value = "" // Reset search
    }

    fun selectCategory(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playChannel(channel: IPTVChannel, currentCategoryList: List<IPTVChannel>) {
        _activeChannel.value = channel
        _activeCategoryChannels.value = currentCategoryList.ifEmpty { listOf(channel) }
    }

    fun closePlayer() {
        _activeChannel.value = null
    }

    fun toggleFavorite(channel: IPTVChannel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, !channel.isFavorite)
            // If playing the channel currently, update its favorite status
            if (_activeChannel.value?.id == channel.id) {
                _activeChannel.value = _activeChannel.value?.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    suspend fun getActiveEPGForChannel(channelId: String): EPGProgram? {
        val playlist = selectedPlaylist.value ?: return null
        return repository.getActiveProgramForChannel(playlist.id, channelId)
    }

    fun selectPlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.selectPlaylist(playlistId)
            selectTab("HOME")
        }
    }

    fun refreshSelectedPlaylist() {
        val playlist = selectedPlaylist.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _loadError.value = null
            val result = repository.refreshPlaylist(playlist.id)
            if (result.isFailure) {
                _loadError.value = result.exceptionOrNull()?.localizedMessage ?: "فشل فحص وتحديث القنوات"
            }
            _isRefreshing.value = false
        }
    }

    fun addNewPlaylist(
        name: String,
        type: String,
        url: String = "",
        user: String = "",
        pass: String = "",
        server: String = ""
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _loadError.value = null
            val result = repository.addPlaylist(name, type, url, user, pass, server)
            if (result.isFailure) {
                _loadError.value = result.exceptionOrNull()?.localizedMessage ?: "فشل الاتصال وحفظ قائمة القنوات"
            } else {
                selectTab("HOME")
            }
            _isRefreshing.value = false
        }
    }

    fun deletePlaylist(playlist: IPTVPlaylist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            // Select first remaining playlist or reset
            val remaining = playlists.value.filter { it.id != playlist.id }
            if (remaining.isNotEmpty()) {
                selectPlaylist(remaining.first().id)
            }
        }
    }

    fun changeTheme(themeName: String) {
        settingsManager.setTheme(themeName)
    }

    fun changeLanguage(langName: String) {
        settingsManager.setLang(langName)
    }

    fun toggleAutoUpdate(enabled: Boolean) {
        settingsManager.setAutoUpdate(enabled)
    }

    fun clearError() {
        _loadError.value = null
    }
}
