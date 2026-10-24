package com.be.music.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.be.music.ads.AdsProvider
import com.be.music.platform.LauncherIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumViewModel(
    private val premiumManager: PremiumManager
) : ViewModel() {

    val premiumState: StateFlow<PremiumState> = premiumManager.state

    private val _iconUri = MutableStateFlow<String?>(null)
    val iconUri: StateFlow<String?> = _iconUri.asStateFlow()

    private val _iconResult = MutableStateFlow<com.be.music.platform.LauncherIconResult?>(null)
    val iconResult: StateFlow<com.be.music.platform.LauncherIconResult?> = _iconResult

    private val _customColor = MutableStateFlow<Int?>(null)
    val customColor: StateFlow<Int?> = _customColor.asStateFlow()

    private val _keepAliveEnabled = MutableStateFlow(false)
    val keepAliveEnabled: StateFlow<Boolean> = _keepAliveEnabled.asStateFlow()

    init {
        if (LauncherIcon.supported) {
            LauncherIcon.ensureEnabled()
            _iconUri.value = LauncherIcon.currentIconPath()
        }
        viewModelScope.launch {
            PremiumStorage.customThemeColor.collect { color ->
                _customColor.value = color
            }
        }
        viewModelScope.launch {
            premiumManager.keepAliveEnabled.collect { enabled ->
                _keepAliveEnabled.value = enabled
            }
        }
    }

    fun clearIconMessage() {
        _iconResult.value = null
    }

    fun watchRewardedAd() {
        AdsProvider.showRewarded(
            onReward = { viewModelScope.launch { premiumManager.onRewardedAdCompleted() } },
            onDismiss = {}
        )
    }

    fun refreshPremiumState() {
        viewModelScope.launch {
            premiumManager.checkAndRefreshPremium()
        }
    }

    fun setCustomIcon(bytes: ByteArray) {
        viewModelScope.launch {
            if (!LauncherIcon.supported) return@launch
            val result = LauncherIcon.setCustom(bytes)
            _iconResult.value = result
            _iconUri.value = LauncherIcon.currentIconPath()
        }
    }

    fun clearCustomIcon() {
        viewModelScope.launch {
            if (!LauncherIcon.supported) return@launch
            LauncherIcon.restoreDefault()
            _iconUri.value = null
            _iconResult.value = com.be.music.platform.LauncherIconResult.SAVED
        }
    }

    fun setCustomColor(color: Int) {
        _customColor.value = color
        viewModelScope.launch {
            PremiumStorage.setCustomThemeColor(color)
        }
    }

    fun clearCustomColor() {
        _customColor.value = null
        viewModelScope.launch {
            PremiumStorage.clearCustomThemeColor()
        }
    }

    fun toggleKeepAlive(enabled: Boolean) {
        viewModelScope.launch {
            premiumManager.setKeepAliveEnabled(enabled)
            _keepAliveEnabled.value = enabled
        }
    }
}
