package com.oklookat.spectra.ui.viewmodel

import androidx.annotation.StringRes
import com.oklookat.spectra.model.AppUpdate
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.service.XrayVpnService

data class SettingsState(
    val useDebugConfig: Boolean = false,
    val isIpv6Enabled: Boolean = false,
    val vpnAddress: String = "",
    val vpnDns: String = "",
    val vpnAddressIpv6: String = "",
    val vpnDnsIpv6: String = "",
    val vpnMtu: Int = 9000
)

data class MainUiState(
    val isVpnEnabled: Boolean = XrayVpnService.isServiceRunning,
    val currentScreen: Screen = Screen.Main,
    val groups: List<Group> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val resources: List<Resource> = emptyList(),
    val isDownloadingResource: Boolean = false,
    val resourceDownloadProgress: Float = 0f,
    val resourceDownloadProgress2: Float = 0f, // For second file in preset
    val currentDownloadingResourceName: String? = null,
    val currentDownloadingResourceName2: String? = null,
    val selectedProfileId: String? = null,
    val deepLinkProfile: PendingProfile? = null,
    val pendingProfileToReplace: PendingProfile? = null,
    val deepLinkGroup: PendingGroup? = null,
    val pendingGroupToReplace: PendingGroup? = null,
    val showDeepLinkVerifyDialog: Boolean = false,
    val isDeepLinkVerified: Boolean = true,
    val settings: SettingsState = SettingsState(),
    
    // P2P State
    val isP2PServerRunning: Boolean = false,
    val p2pServerUrl: String? = null,
    val p2pServerToken: String? = null,
    val p2pPayloadToAccept: P2PPayload? = null,
    val showP2PReplaceDialog: Boolean = false,

    // App Update
    val availableUpdate: AppUpdate? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f
)

sealed class MainUiEvent {
    data class ShowToast(
        val message: String? = null,
        @get:StringRes val messageResId: Int? = null,
        val formatArgs: List<Any> = emptyList(),
        val isLong: Boolean = false
    ) : MainUiEvent()
    data object RestartVpn : MainUiEvent()
    data object OpenDeepLinkSettings : MainUiEvent()
}
