package com.oklookat.spectra.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.data.repository.SettingsRepository
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val logLevel: String = "warning",
    val dnsLog: Boolean = false,
    val maskAddress: String = "",
    val isVpnEnabled: Boolean = false
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var uiState by mutableStateOf(LogsUiState())
        private set

    init {
        combine(
            settingsRepository.settingsFlow,
            XrayVpnService.isRunning
        ) { settings, isRunning ->
            uiState = uiState.copy(
                logLevel = settings.logLevel,
                dnsLog = settings.dnsLog,
                maskAddress = settings.maskAddress,
                isVpnEnabled = isRunning
            )
        }.launchIn(viewModelScope)
    }

    fun saveLogSettings(
        logLevel: String,
        dnsLog: Boolean,
        maskAddress: String,
        onComplete: (shouldRestartVpn: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            settingsRepository.setLogSettings(logLevel, dnsLog, maskAddress)
            onComplete(uiState.isVpnEnabled)
        }
    }
}
