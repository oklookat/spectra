package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.R
import com.oklookat.spectra.data.repository.AppFilterRepository
import com.oklookat.spectra.model.AppRoutingPreset
import com.oklookat.spectra.model.InstalledAppInfo
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppFilterUiState(
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val presets: List<AppRoutingPreset> = emptyList(),
    val selectedPresetId: String = AppRoutingPreset.PRESET_ALL_VPN,
    val selectedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoadingApps: Boolean = false,
    val isFetchingRuRules: Boolean = false
) {
    val selectedPreset: AppRoutingPreset?
        get() = presets.find { it.id == selectedPresetId }

    val filteredApps: List<InstalledAppInfo>
        get() {
            val query = searchQuery.trim().lowercase()
            return installedApps.filter { app ->
                (showSystemApps || !app.isSystemApp) &&
                        (query.isBlank() || app.appName.lowercase().contains(query) || app.packageName.lowercase().contains(query))
            }
        }
}

@HiltViewModel
class AppFilterViewModel @Inject constructor(
    application: Application,
    private val appFilterRepository: AppFilterRepository
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(AppFilterUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        loadInstalledApps()
        observeData()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingApps = true)
            val apps = withContext(Dispatchers.IO) {
                appFilterRepository.getInstalledApps()
            }
            uiState = uiState.copy(installedApps = apps, isLoadingApps = false)
        }
    }

    private fun observeData() {
        combine(
            appFilterRepository.presetsFlow,
            appFilterRepository.selectedPresetIdFlow,
            appFilterRepository.showSystemAppsFlow
        ) { presets, selectedId, showSystem ->
            val selectedPreset = presets.find { it.id == selectedId } ?: presets.firstOrNull()
            uiState = uiState.copy(
                presets = presets,
                selectedPresetId = selectedId,
                selectedPackages = selectedPreset?.packages ?: emptySet(),
                showSystemApps = showSystem
            )
        }.launchIn(viewModelScope)
    }

    fun selectPreset(presetId: String) {
        viewModelScope.launch {
            appFilterRepository.selectPreset(presetId)
            val preset = uiState.presets.find { it.id == presetId }
            if (presetId == AppRoutingPreset.PRESET_EXCLUDE_RU && (preset?.packages.isNullOrEmpty())) {
                fetchRussianAppRules()
            }
        }
    }

    fun togglePackage(packageName: String) {
        val newSelection = if (uiState.selectedPackages.contains(packageName)) {
            uiState.selectedPackages - packageName
        } else {
            uiState.selectedPackages + packageName
        }
        uiState = uiState.copy(selectedPackages = newSelection)
        saveCurrentPresetPackages(newSelection)
    }

    fun resetToAllVpn() {
        viewModelScope.launch {
            appFilterRepository.selectPreset(AppRoutingPreset.PRESET_ALL_VPN)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.reset_to_all_vpn))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun invertSelection() {
        val visiblePackages = uiState.filteredApps.map { it.packageName }.toSet()
        val currentSelection = uiState.selectedPackages.toMutableSet()

        visiblePackages.forEach { pkg ->
            if (currentSelection.contains(pkg)) {
                currentSelection.remove(pkg)
            } else {
                currentSelection.add(pkg)
            }
        }

        uiState = uiState.copy(selectedPackages = currentSelection)
        saveCurrentPresetPackages(currentSelection)
    }

    fun selectAllVisible() {
        val visiblePackages = uiState.filteredApps.map { it.packageName }.toSet()
        val newSelection = uiState.selectedPackages + visiblePackages
        uiState = uiState.copy(selectedPackages = newSelection)
        saveCurrentPresetPackages(newSelection)
    }

    fun deselectAllVisible() {
        val visiblePackages = uiState.filteredApps.map { it.packageName }.toSet()
        val newSelection = uiState.selectedPackages - visiblePackages
        uiState = uiState.copy(selectedPackages = newSelection)
        saveCurrentPresetPackages(newSelection)
    }

    private fun saveCurrentPresetPackages(packages: Set<String>) {
        viewModelScope.launch {
            appFilterRepository.updatePresetPackages(uiState.selectedPresetId, packages)
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun createCustomPreset(name: String) {
        viewModelScope.launch {
            val newId = appFilterRepository.createCustomPreset(name, uiState.selectedPackages)
            appFilterRepository.selectPreset(newId)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_created, formatArgs = listOf(name)))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun updateCustomPresetName(name: String) {
        viewModelScope.launch {
            appFilterRepository.updateCustomPresetName(uiState.selectedPresetId, name)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
        }
    }

    fun addRegexRule(regex: String) {
        viewModelScope.launch {
            appFilterRepository.addRegexRuleToPreset(uiState.selectedPresetId, regex)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun removeRegexRule(regex: String) {
        viewModelScope.launch {
            appFilterRepository.removeRegexRuleFromPreset(uiState.selectedPresetId, regex)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            appFilterRepository.deleteCustomPreset(presetId)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_deleted))
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    fun fetchRussianAppRules() {
        viewModelScope.launch {
            uiState = uiState.copy(isFetchingRuRules = true)
            val result = appFilterRepository.fetchAndApplyRussianAppRules()
            uiState = uiState.copy(isFetchingRuRules = false)

            if (result.isSuccess) {
                val matchedCount = result.getOrNull()?.size ?: 0
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.ru_rules_applied, formatArgs = listOf(matchedCount)))
                if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _events.emit(MainUiEvent.ShowToast(message = error))
            }
        }
    }

    fun setSearchQuery(query: String) {
        uiState = uiState.copy(searchQuery = query)
    }

    fun toggleShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            appFilterRepository.setShowSystemApps(show)
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return appFilterRepository.getAppIcon(packageName)
    }
}
