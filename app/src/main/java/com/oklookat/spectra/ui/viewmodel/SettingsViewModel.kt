package com.oklookat.spectra.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.domain.usecase.settings.GetSettingsUseCase
import com.oklookat.spectra.domain.usecase.settings.SetIpv6EnabledUseCase
import com.oklookat.spectra.domain.usecase.settings.SetVpnSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val setIpv6EnabledUseCase: SetIpv6EnabledUseCase,
    private val setVpnSettingsUseCase: SetVpnSettingsUseCase
) : ViewModel() {

    var uiState by mutableStateOf(SettingsState())
        private set

    init {
        getSettingsUseCase()
            .onEach { settings ->
                uiState = SettingsState(
                    isIpv6Enabled = settings.isIpv6Enabled,
                    vpnAddress = settings.vpnAddress,
                    vpnDns = settings.vpnDns,
                    vpnAddressIpv6 = settings.vpnAddressIpv6,
                    vpnDnsIpv6 = settings.vpnDnsIpv6,
                    vpnMtu = settings.vpnMtu
                )
            }
            .launchIn(viewModelScope)
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
}
