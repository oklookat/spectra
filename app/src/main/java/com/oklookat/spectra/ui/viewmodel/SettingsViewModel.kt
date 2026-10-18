package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.R
import com.oklookat.spectra.data.repository.WifiExclusionRepository
import com.oklookat.spectra.domain.usecase.settings.GetSettingsUseCase
import com.oklookat.spectra.domain.usecase.settings.SetIpv6EnabledUseCase
import com.oklookat.spectra.domain.usecase.settings.SetVpnSettingsUseCase
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.oklookat.spectra.data.repository.ProfileRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val setIpv6EnabledUseCase: SetIpv6EnabledUseCase,
    private val setVpnSettingsUseCase: SetVpnSettingsUseCase,
    private val wifiExclusionRepository: WifiExclusionRepository,
    private val profileRepository: ProfileRepository
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(SettingsState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        observeSettings()
        refreshBatteryOptimizationStatus()
        refreshWifiStatus()
    }

    private fun observeSettings() {
        combine(
            getSettingsUseCase(),
            wifiExclusionRepository.isWifiExclusionEnabledFlow,
            wifiExclusionRepository.excludedSsidsFlow
        ) { settings, isWifiExclusionEnabled, excludedSsids ->
            val hasLocPerm = wifiExclusionRepository.hasLocationPermission()
            val currentSsid = wifiExclusionRepository.getCurrentWifiSsid()
            val isIgnored = checkBatteryOptimization()

            uiState = uiState.copy(
                isIpv6Enabled = settings.isIpv6Enabled,
                vpnAddress = settings.vpnAddress,
                vpnDns = settings.vpnDns,
                vpnAddressIpv6 = settings.vpnAddressIpv6,
                vpnDnsIpv6 = settings.vpnDnsIpv6,
                vpnMtu = settings.vpnMtu,
                isWifiExclusionEnabled = isWifiExclusionEnabled,
                excludedWifiSsids = excludedSsids,
                currentWifiSsid = currentSsid,
                hasLocationPermission = hasLocPerm,
                isIgnoringBatteryOptimizations = isIgnored
            )
        }.launchIn(viewModelScope)
    }

    fun refreshBatteryOptimizationStatus() {
        val isIgnored = checkBatteryOptimization()
        uiState = uiState.copy(isIgnoringBatteryOptimizations = isIgnored)
    }

    fun refreshWifiStatus() {
        val hasLocPerm = wifiExclusionRepository.hasLocationPermission()
        val currentSsid = wifiExclusionRepository.getCurrentWifiSsid()
        uiState = uiState.copy(
            hasLocationPermission = hasLocPerm,
            currentWifiSsid = currentSsid
        )
    }

    private fun checkBatteryOptimization(): Boolean {
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: true
    }

    fun toggleIpv6(enabled: Boolean) {
        viewModelScope.launch {
            setIpv6EnabledUseCase(enabled)
        }
    }

    fun updateTunnelSettings(address: String, dns: String, address6: String, dns6: String, mtu: Int) {
        viewModelScope.launch {
            setVpnSettingsUseCase(address, dns, address6, dns6, mtu)
        }
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

    fun getActiveConfigJson(): String {
        val runningConfig = XrayVpnService.activeConfigJson
        if (!runningConfig.isNullOrBlank()) return runningConfig

        val selectedProfileId = profileRepository.getSelectedProfileId()
        if (selectedProfileId != null) {
            val profiles = kotlinx.coroutines.runBlocking { profileRepository.getProfiles() }
            val profile = profiles.find { it.id == selectedProfileId }
            if (profile != null) {
                val content = profileRepository.getProfileContent(profile)
                if (!content.isNullOrBlank()) {
                    return com.oklookat.spectra.model.XrayConfigBuild.prepareConfigForAndroid(content)
                }
            }
        }
        return ""
    }
}
