package com.oklookat.spectra.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.domain.usecase.profile.GetProfileContentUseCase
import com.oklookat.spectra.domain.usecase.profile.GetProfilesUseCase
import com.oklookat.spectra.domain.usecase.settings.GetSettingsUseCase
import com.oklookat.spectra.domain.usecase.settings.SetSelectedProfileIdUseCase
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val setSelectedProfileIdUseCase: SetSelectedProfileIdUseCase,
    private val getProfileContentUseCase: GetProfileContentUseCase
) : ViewModel() {

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        // Observe VPN status
        XrayVpnService.isRunning
            .onEach { isRunning ->
                uiState = uiState.copy(isVpnEnabled = isRunning)
            }
            .launchIn(viewModelScope)

        // Combine profiles and settings
        combine(
            getProfilesUseCase(),
            getSettingsUseCase()
        ) { profiles, settings ->
            uiState = uiState.copy(
                profiles = profiles,
                selectedProfileId = settings.selectedProfileId,
                useDebugConfig = settings.useDebugConfig
            )
        }.launchIn(viewModelScope)
    }

    fun selectProfile(id: String?) {
        viewModelScope.launch {
            setSelectedProfileIdUseCase(id)
        }
    }

    fun getSelectedProfileContent(): String? {
        val profile = uiState.profiles.find { it.id == uiState.selectedProfileId } ?: return null
        return getProfileContentUseCase(profile)
    }

    fun updateVpnStatus() {
        uiState = uiState.copy(isVpnEnabled = XrayVpnService.isServiceRunning)
    }
}
