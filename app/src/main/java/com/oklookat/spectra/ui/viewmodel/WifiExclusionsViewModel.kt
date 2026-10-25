package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.R
import com.oklookat.spectra.data.repository.WifiExclusionRepository
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiExclusionsUiState(
    val isWifiExclusionEnabled: Boolean = false,
    val excludedWifiSsids: Set<String> = emptySet(),
    val currentWifiSsid: String? = null,
    val hasLocationPermission: Boolean = false,
    val bypassDnsPrimary: String = WifiExclusionRepository.DEFAULT_PRIMARY_DNS,
    val bypassDnsSecondary: String = WifiExclusionRepository.DEFAULT_SECONDARY_DNS,
    val customBypassConfigJson: String = ""
)

@HiltViewModel
class WifiExclusionsViewModel @Inject constructor(
    application: Application,
    private val wifiExclusionRepository: WifiExclusionRepository
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(WifiExclusionsUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        observeData()
    }

    private fun observeData() {
        combine(
            wifiExclusionRepository.isWifiExclusionEnabledFlow,
            wifiExclusionRepository.excludedSsidsFlow,
            wifiExclusionRepository.bypassDnsPrimaryFlow,
            wifiExclusionRepository.bypassDnsSecondaryFlow,
            wifiExclusionRepository.customBypassConfigJsonFlow
        ) { enabled, ssids, primaryDns, secondaryDns, customJson ->
            val hasLocPerm = wifiExclusionRepository.hasLocationPermission()
            val currentSsid = wifiExclusionRepository.getCurrentWifiSsid()

            uiState = uiState.copy(
                isWifiExclusionEnabled = enabled,
                excludedWifiSsids = ssids,
                currentWifiSsid = currentSsid,
                hasLocationPermission = hasLocPerm,
                bypassDnsPrimary = primaryDns,
                bypassDnsSecondary = secondaryDns,
                customBypassConfigJson = customJson
            )
        }.launchIn(viewModelScope)
    }

    fun refreshWifiStatus() {
        val hasLocPerm = wifiExclusionRepository.hasLocationPermission()
        val currentSsid = wifiExclusionRepository.getCurrentWifiSsid()
        uiState = uiState.copy(
            hasLocationPermission = hasLocPerm,
            currentWifiSsid = currentSsid
        )
    }

    fun toggleWifiExclusion(enabled: Boolean) {
        viewModelScope.launch {
            wifiExclusionRepository.setWifiExclusionEnabled(enabled)
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun addWifiSsid(ssid: String) {
        viewModelScope.launch {
            wifiExclusionRepository.addWifiSsid(ssid)
            refreshWifiStatus()
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.wifi_added, formatArgs = listOf(ssid)))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun removeWifiSsid(ssid: String) {
        viewModelScope.launch {
            wifiExclusionRepository.removeWifiSsid(ssid)
            refreshWifiStatus()
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.wifi_removed, formatArgs = listOf(ssid)))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun saveBypassDns(primary: String, secondary: String) {
        viewModelScope.launch {
            wifiExclusionRepository.saveBypassDns(primary, secondary)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun saveCustomBypassConfigJson(json: String) {
        viewModelScope.launch {
            wifiExclusionRepository.saveCustomBypassConfigJson(json)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun resetBypassConfigToDefault() {
        viewModelScope.launch {
            wifiExclusionRepository.resetBypassConfigToDefault()
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.reset_to_all_vpn))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun getActiveBypassConfigJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(wifiExclusionRepository.getActiveBypassConfigJson())
        }
    }
}
