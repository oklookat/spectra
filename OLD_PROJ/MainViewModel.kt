package com.oklookat.spectra

import android.app.Application
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.P2PClient
import com.oklookat.spectra.util.P2PServer
import com.oklookat.spectra.util.ProfileManager
import com.oklookat.spectra.util.SettingsRepository
import com.oklookat.spectra.util.TvUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val deepLinkProfile: PendingProfile? = null,
    val pendingProfileToReplace: PendingProfile? = null,
    val showDeepLinkVerifyDialog: Boolean = false,
    val isDeepLinkVerified: Boolean = true,
    val settings: SettingsState = SettingsState(),
    
    // P2P State
    val isP2PServerRunning: Boolean = false,
    val p2pServerUrl: String? = null,
    val p2pServerToken: String? = null,
    val p2pPayloadToAccept: P2PPayload? = null,
    val showP2PReplaceDialog: Boolean = false
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val profileManager = ProfileManager(application)
    private val settingsRepository = SettingsRepository(application)
    private val p2pClient = P2PClient()
    private var p2pServer: P2PServer? = null
    
    companion object {
        private var hasShownVerifyDialogThisSession = false
    }
    
    var uiState by mutableStateOf(MainUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        // Initial load
        loadInitialState()
        
        XrayVpnService.isRunning
            .onEach { isRunning ->
                uiState = uiState.copy(isVpnEnabled = isRunning)
            }
            .launchIn(viewModelScope)
        
        checkDeepLinkStatus()
    }

    private fun loadInitialState() {
        uiState = uiState.copy(
            profiles = profileManager.getProfiles(),
            selectedProfileId = profileManager.getSelectedProfileId(),
            settings = SettingsState(
                useDebugConfig = settingsRepository.useDebugConfig,
                isIpv6Enabled = settingsRepository.isIpv6Enabled,
                vpnAddress = settingsRepository.vpnAddress,
                vpnDns = settingsRepository.vpnDns,
                vpnAddressIpv6 = settingsRepository.vpnAddressIpv6,
                vpnDnsIpv6 = settingsRepository.vpnDnsIpv6,
                vpnMtu = settingsRepository.vpnMtu
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopP2PServer()
    }

    fun updateVpnStatus() {
        uiState = uiState.copy(isVpnEnabled = XrayVpnService.isServiceRunning)
    }

    fun setScreen(screen: Screen) {
        uiState = uiState.copy(currentScreen = screen)
    }

    fun selectProfile(id: String?) {
        uiState = uiState.copy(selectedProfileId = id)
        profileManager.setSelectedProfileId(id)
    }

    fun getSelectedProfileContent(): String? {
        val profile = uiState.profiles.find { it.id == uiState.selectedProfileId } ?: return null
        return profileManager.getProfileContent(profile)
    }

    fun addRemoteProfile(name: String, url: String, autoUpdate: Boolean, interval: Int, onResult: (Result<Profile>) -> Unit = {}) {
        viewModelScope.launch {
            val result = profileManager.addRemoteProfile(name, url, autoUpdate, interval)
            if (result.isSuccess) {
                uiState = uiState.copy(profiles = profileManager.getProfiles())
            } else {
                LogManager.addLog("[App] Failed to add remote profile '$name': ${result.exceptionOrNull()?.message}")
            }
            onResult(result)
        }
    }

    fun saveRemoteProfile(
        existing: Profile?,
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (existing == null) {
                val result = profileManager.addRemoteProfile(name, url, autoUpdate, interval)
                if (result.isSuccess) {
                    uiState = uiState.copy(profiles = profileManager.getProfiles())
                    onComplete()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    LogManager.addLog("[App] Error adding remote profile: $error")
                    _events.emit(MainUiEvent.ShowToast(message = error))
                }
            } else {
                val updated = existing.copy(
                    name = name,
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval
                )
                val needsRestart = updateProfileInternal(updated)
                if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
                onComplete()
            }
        }
    }

    fun saveLocalProfile(
        existing: Profile?,
        name: String,
        content: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (existing == null) {
                withContext(Dispatchers.IO) {
                    profileManager.saveLocalProfile(name, content)
                }
            } else {
                val needsRestart = updateProfileInternal(existing.copy(name = name), content)
                if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
            }
            uiState = uiState.copy(profiles = profileManager.getProfiles())
            onComplete()
        }
    }

    fun replaceRemoteProfile(existingProfile: Profile, url: String, autoUpdate: Boolean, interval: Int) {
        viewModelScope.launch {
            try {
                val fileName = existingProfile.fileName ?: "${existingProfile.id}.json"
                val wasRunning = XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == existingProfile.id
                val contentChanged = profileManager.downloadProfile(url, fileName)
                
                val updatedProfile = existingProfile.copy(
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval,
                    lastUpdated = System.currentTimeMillis(),
                    fileName = fileName
                )
                
                withContext(Dispatchers.IO) {
                    profileManager.updateProfile(updatedProfile)
                }
                uiState = uiState.copy(profiles = profileManager.getProfiles())
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_updated))
                
                if (wasRunning && contentChanged) _events.emit(MainUiEvent.RestartVpn)
            } catch (e: Exception) {
                LogManager.addLog("[App] Error replacing remote profile: ${e.message}")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed, isLong = true))
            }
        }
    }

    fun refreshRemoteProfiles(ids: Set<String>? = null) {
        viewModelScope.launch {
            val toRefresh = uiState.profiles.filter { it.isRemote && (ids == null || it.id in ids) }
            var success = 0
            var failed = 0
            var needsRestart = false

            for (profile in toRefresh) {
                try {
                    val url = profile.url ?: continue
                    val fileName = profile.fileName ?: continue
                    val changed = profileManager.downloadProfile(url, fileName)
                    if (changed) {
                        withContext(Dispatchers.IO) {
                            profileManager.updateProfile(profile.copy(lastUpdated = System.currentTimeMillis()))
                        }
                        if (XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id) needsRestart = true
                    }
                    success++
                } catch (e: Exception) { 
                    LogManager.addLog("[App] Failed to refresh profile '${profile.name}': ${e.message}")
                    failed++ 
                }
            }
            
            if (success > 0) uiState = uiState.copy(profiles = profileManager.getProfiles())
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profiles_update_result, formatArgs = listOf(success, failed)))
            if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    private suspend fun updateProfileInternal(profile: Profile, content: String? = null): Boolean = withContext(Dispatchers.IO) {
        var contentChanged = false
        if (content != null) {
            contentChanged = profileManager.updateLocalProfileContent(profile, content)
        }
        profileManager.updateProfile(profile)
        
        withContext(Dispatchers.Main) {
            uiState = uiState.copy(profiles = profileManager.getProfiles())
        }
        
        return@withContext contentChanged && XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id
    }

    fun deleteProfiles(ids: Set<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                profileManager.deleteProfiles(ids)
            }
            uiState = uiState.copy(
                profiles = profileManager.getProfiles(),
                selectedProfileId = profileManager.getSelectedProfileId()
            )
        }
    }

    fun getProfileContent(profile: Profile): String = profileManager.getProfileContent(profile) ?: ""

    fun setDeepLinkProfile(profile: PendingProfile?) {
        uiState = uiState.copy(deepLinkProfile = profile)
    }

    fun setPendingProfileToReplace(profile: PendingProfile?) {
        uiState = uiState.copy(pendingProfileToReplace = profile)
    }

    fun setShowDeepLinkVerifyDialog(show: Boolean) {
        uiState = uiState.copy(showDeepLinkVerifyDialog = show)
    }

    fun checkDeepLinkStatus() {
        if (TvUtils.isTv(getApplication())) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getApplication<Application>().getSystemService(DomainVerificationManager::class.java) ?: return
            val userState = manager.getDomainVerificationUserState(getApplication<Application>().packageName) ?: return
            val isSelected = userState.hostToStateMap["spectra.local"] == DomainVerificationUserState.DOMAIN_STATE_SELECTED
            
            uiState = uiState.copy(isDeepLinkVerified = isSelected)
        }
    }

    fun triggerDeepLinkVerifyDialogIfNeeded() {
        if (hasShownVerifyDialogThisSession) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!uiState.isDeepLinkVerified && uiState.currentScreen == Screen.Main) {
                hasShownVerifyDialogThisSession = true
                uiState = uiState.copy(showDeepLinkVerifyDialog = true)
            }
        }
    }

    fun openDeepLinkSettings() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.OpenDeepLinkSettings)
        }
    }

    // P2P Methods
    fun startP2PServer() {
        if (p2pServer != null) return
        val server = P2PServer(onPayloadReceived = { payload ->
            viewModelScope.launch(Dispatchers.Main) {
                val existing = uiState.profiles.find { it.name == payload.name }
                uiState = uiState.copy(
                    p2pPayloadToAccept = payload,
                    showP2PReplaceDialog = existing != null
                )
            }
        })
        try {
            server.start()
            p2pServer = server
            uiState = uiState.copy(isP2PServerRunning = true, p2pServerUrl = server.getShareUrl(), p2pServerToken = server.token)
            LogManager.addLog("[App] P2P Server started at ${server.getShareUrl()}")
        } catch (e: Exception) {
            val error = e.message ?: "Unknown error"
            LogManager.addLog("[App] P2P Server error: $error")
            viewModelScope.launch { _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_server_error, formatArgs = listOf(error))) }
        }
    }

    fun stopP2PServer() {
        p2pServer?.stop()
        p2pServer = null
        uiState = uiState.copy(isP2PServerRunning = false, p2pServerUrl = null, p2pServerToken = null, p2pPayloadToAccept = null, showP2PReplaceDialog = false)
        LogManager.addLog("[App] P2P Server stopped")
    }

    fun acceptP2PPayload() {
        val payload = uiState.p2pPayloadToAccept ?: return
        viewModelScope.launch {
            try {
                val existing = uiState.profiles.find { it.name == payload.name }
                if (existing != null) {
                    if (payload.isRemote && payload.url != null) {
                        replaceRemoteProfile(existing, payload.url, payload.autoUpdateEnabled, payload.autoUpdateIntervalMinutes)
                    } else if (payload.content != null) {
                        val needsRestart = updateProfileInternal(existing, payload.content)
                        if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
                    }
                } else {
                    if (payload.isRemote && payload.url != null) {
                        profileManager.addRemoteProfile(payload.name, payload.url, payload.autoUpdateEnabled, payload.autoUpdateIntervalMinutes)
                    } else if (payload.content != null) {
                        withContext(Dispatchers.IO) {
                            profileManager.saveLocalProfile(payload.name, payload.content)
                        }
                    }
                    uiState = uiState.copy(profiles = profileManager.getProfiles())
                }
                LogManager.addLog("[App] P2P profile accepted: ${payload.name}")
            } catch (e: Exception) {
                LogManager.addLog("[App] Error accepting P2P payload: ${e.message}")
            }
            uiState = uiState.copy(p2pPayloadToAccept = null, showP2PReplaceDialog = false)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf(payload.name)))
        }
    }

    fun rejectP2PPayload() {
        uiState = uiState.copy(p2pPayloadToAccept = null, showP2PReplaceDialog = false)
    }

    fun sendProfileP2P(profile: Profile, targetUrl: String, token: String) {
        viewModelScope.launch {
            try {
                val content = if (!profile.isRemote) {
                    withContext(Dispatchers.IO) {
                        profileManager.getProfileContent(profile)
                    }
                } else null
                
                val payload = P2PPayload(
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    name = profile.name,
                    content = content,
                    url = profile.url,
                    isRemote = profile.isRemote,
                    autoUpdateEnabled = profile.autoUpdateEnabled,
                    autoUpdateIntervalMinutes = profile.autoUpdateIntervalMinutes,
                    token = token
                )
                val result = p2pClient.sendProfile(targetUrl, payload)
                if (result.isSuccess) {
                    LogManager.addLog("[App] Profile '${profile.name}' sent via P2P")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_sent_success))
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    LogManager.addLog("[App] P2P Send error: $error")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_send_failed, formatArgs = listOf(error)))
                }
            } catch (e: Exception) {
                LogManager.addLog("[App] Error sending P2P profile: ${e.message}")
            }
        }
    }

    fun toggleDebugConfig(enabled: Boolean) {
        settingsRepository.useDebugConfig = enabled
        uiState = uiState.copy(settings = uiState.settings.copy(useDebugConfig = enabled))
    }

    fun toggleIpv6(enabled: Boolean) {
        settingsRepository.isIpv6Enabled = enabled
        uiState = uiState.copy(settings = uiState.settings.copy(isIpv6Enabled = enabled))
    }

    fun updateTunnelSettings(address: String, dns: String, address6: String, dns6: String, mtu: Int) {
        settingsRepository.vpnAddress = address
        settingsRepository.vpnDns = dns
        settingsRepository.vpnAddressIpv6 = address6
        settingsRepository.vpnDnsIpv6 = dns6
        settingsRepository.vpnMtu = mtu
        uiState = uiState.copy(settings = uiState.settings.copy(
            vpnAddress = address, 
            vpnDns = dns, 
            vpnAddressIpv6 = address6, 
            vpnDnsIpv6 = dns6,
            vpnMtu = mtu
        ))
    }
}
