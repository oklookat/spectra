package com.oklookat.spectra.ui.viewmodel

import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.service.XrayVpnService

data class HomeUiState(
    val isVpnEnabled: Boolean = XrayVpnService.isServiceRunning,
    val profiles: List<Profile> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedProfileId: String? = null
)
