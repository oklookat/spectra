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
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.model.PendingGroup
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
    val groups: List<Group> = emptyList(),
    val profiles: List<Profile> = emptyList(),
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
            groups = profileManager.getGroups(),
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

    // --- Group Operations ---

    fun saveGroup(
        existing: Group?,
        name: String,
        url: String?,
        autoUpdate: Boolean,
        interval: Int,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (existing == null) {
                if (url.isNullOrBlank()) {
                    val groups = profileManager.getGroups().toMutableList()
                    groups.add(Group(name = name))
                    profileManager.saveGroups(groups)
                    uiState = uiState.copy(groups = profileManager.getGroups())
                    onComplete()
                } else {
                    val result = profileManager.addRemoteGroup(name, url, autoUpdate, interval)
                    if (result.isSuccess) {
                        uiState = uiState.copy(
                            groups = profileManager.getGroups(),
                            profiles = profileManager.getProfiles()
                        )
                        onComplete()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        LogManager.addLog("[App] Error adding remote group: $error")
                        _events.emit(MainUiEvent.ShowToast(message = error))
                    }
                }
            } else {
                val updated = existing.copy(
                    name = name,
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval
                )
                val groups = profileManager.getGroups().toMutableList()
                val index = groups.indexOfFirst { it.id == existing.id }
                if (index != -1) {
                    groups[index] = updated
                    profileManager.saveGroups(groups)
                    uiState = uiState.copy(groups = profileManager.getGroups())
                }
                onComplete()
            }
        }
    }

    fun replaceRemoteGroup(existingGroup: Group, url: String, autoUpdate: Boolean, interval: Int) {
        viewModelScope.launch {
            try {
                val updatedGroup = existingGroup.copy(
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval,
                    lastUpdated = System.currentTimeMillis()
                )
                profileManager.updateGroupProfiles(updatedGroup)
                val groups = profileManager.getGroups().toMutableList()
                val index = groups.indexOfFirst { it.id == existingGroup.id }
                if (index != -1) {
                    groups[index] = updatedGroup
                    profileManager.saveGroups(groups)
                }
                uiState = uiState.copy(
                    groups = profileManager.getGroups(),
                    profiles = profileManager.getProfiles()
                )
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_updated))
            } catch (e: Exception) {
                LogManager.addLog("[App] Error replacing group: ${e.message}")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed))
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            profileManager.deleteGroup(groupId)
            uiState = uiState.copy(
                groups = profileManager.getGroups(),
                profiles = profileManager.getProfiles(),
                selectedProfileId = profileManager.getSelectedProfileId()
            )
        }
    }

    fun refreshGroup(group: Group) {
        viewModelScope.launch {
            try {
                profileManager.updateGroupProfiles(group)
                uiState = uiState.copy(profiles = profileManager.getProfiles())
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_updated))
            } catch (e: Exception) {
                LogManager.addLog("[App] Error refreshing group: ${e.message}")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed))
            }
        }
    }

    // --- Profile Operations ---

    fun saveRemoteProfile(
        existing: Profile?,
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int,
        groupId: String = Group.DEFAULT_GROUP_ID,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (existing == null) {
                val result = profileManager.addRemoteProfile(name, url, autoUpdate, interval, groupId)
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
        groupId: String = Group.DEFAULT_GROUP_ID,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (existing == null) {
                withContext(Dispatchers.IO) {
                    profileManager.saveLocalProfile(name, content, groupId)
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
            val toRefresh = uiState.profiles.filter { it.isRemote && (ids == null || it.id in ids) && !it.isImported }
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

    fun setDeepLinkGroup(group: PendingGroup?) {
        uiState = uiState.copy(deepLinkGroup = group)
    }

    fun setPendingGroupToReplace(group: PendingGroup?) {
        uiState = uiState.copy(pendingGroupToReplace = group)
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
                val existing = if (payload.profile != null) uiState.profiles.find { it.name == payload.profile.name } else null
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
                if (payload.profile != null) {
                    val p = payload.profile
                    val existing = uiState.profiles.find { it.name == p.name }
                    if (existing != null) {
                        if (p.isRemote && p.url != null) {
                            replaceRemoteProfile(existing, p.url, p.autoUpdateEnabled, p.autoUpdateIntervalMinutes)
                        } else if (payload.profileContent != null) {
                            val needsRestart = updateProfileInternal(existing, payload.profileContent)
                            if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
                        }
                    } else {
                        if (p.isRemote && p.url != null) {
                            profileManager.addRemoteProfile(p.name, p.url, p.autoUpdateEnabled, p.autoUpdateIntervalMinutes)
                        } else if (payload.profileContent != null) {
                            withContext(Dispatchers.IO) {
                                profileManager.saveLocalProfile(p.name, payload.profileContent)
                            }
                        }
                    }
                } else if (payload.group != null) {
                    val g = payload.group
                    val groups = profileManager.getGroups().toMutableList()
                    val targetGroupId = if (groups.any { it.name == g.name }) {
                        groups.find { it.name == g.name }?.id ?: g.id
                    } else {
                        val newG = g.copy(id = java.util.UUID.randomUUID().toString())
                        groups.add(newG)
                        profileManager.saveGroups(groups)
                        newG.id
                    }
                    
                    payload.groupProfiles?.forEach { (profile, content) ->
                        if (profile.isRemote && profile.url != null) {
                             profileManager.addRemoteProfile(profile.name, profile.url, profile.autoUpdateEnabled, profile.autoUpdateIntervalMinutes, targetGroupId)
                        } else if (content != null) {
                            profileManager.saveLocalProfile(profile.name, content, targetGroupId)
                        }
                    }
                }
                uiState = uiState.copy(groups = profileManager.getGroups(), profiles = profileManager.getProfiles())
                LogManager.addLog("[App] P2P payload accepted")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf("Profile/Group")))
            } catch (e: Exception) {
                LogManager.addLog("[App] Error accepting P2P payload: ${e.message}")
            }
            uiState = uiState.copy(p2pPayloadToAccept = null, showP2PReplaceDialog = false)
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
                    token = token,
                    profile = profile,
                    profileContent = content
                )
                val result = p2pClient.sendPayload(targetUrl, payload)
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

    fun sendGroupP2P(group: Group, targetUrl: String, token: String) {
         viewModelScope.launch {
            try {
                val profiles = uiState.profiles.filter { it.groupId == group.id }
                val groupProfiles = profiles.map { profile ->
                    val content = if (!profile.isRemote) {
                        withContext(Dispatchers.IO) {
                            profileManager.getProfileContent(profile)
                        }
                    } else null
                    profile to content
                }
                
                val payload = P2PPayload(
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    token = token,
                    group = group,
                    groupProfiles = groupProfiles
                )
                val result = p2pClient.sendPayload(targetUrl, payload)
                if (result.isSuccess) {
                    LogManager.addLog("[App] Group '${group.name}' sent via P2P")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_sent_success))
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    LogManager.addLog("[App] P2P Send error: $error")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_send_failed, formatArgs = listOf(error)))
                }
            } catch (e: Exception) {
                LogManager.addLog("[App] Error sending P2P group: ${e.message}")
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
