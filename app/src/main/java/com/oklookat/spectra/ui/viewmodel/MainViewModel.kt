package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.data.repository.ResourceRepository
import com.oklookat.spectra.data.repository.SettingsRepository
import com.oklookat.spectra.model.AppUpdate
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.service.XrayVpnService
import com.oklookat.spectra.util.AppUpdateWorker
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.P2PClient
import com.oklookat.spectra.util.P2PServer
import com.oklookat.spectra.util.ResourceUpdateWorker
import com.oklookat.spectra.util.TvUtils
import com.oklookat.spectra.util.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val profileRepository = ProfileRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val resourceRepository = ResourceRepository(application)
    private val updateManager = UpdateManager(application)
    private val p2pClient = P2PClient()
    private var p2pServer: P2PServer? = null
    
    private val UPDATE_URL = BuildConfig.UPDATE_URL
    private var resourceDownloadJob: Job? = null

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
        setupAppUpdates()
        setupResourceUpdates()
        
        // Automatic update check on app launch if enabled
        if (UPDATE_URL.isNotBlank()) {
            checkForUpdatesManually()
        }
    }

    private fun loadInitialState() {
        uiState = uiState.copy(
            groups = profileRepository.getGroups(),
            profiles = profileRepository.getProfiles(),
            resources = resourceRepository.getResources(),
            selectedProfileId = profileRepository.getSelectedProfileId(),
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

    private fun setupAppUpdates() {
        AppUpdateWorker.setupPeriodicWork(getApplication(), UPDATE_URL)
    }

    private fun setupResourceUpdates() {
        ResourceUpdateWorker.setupPeriodicWork(getApplication())
    }

    fun checkForUpdatesManually() {
        if (UPDATE_URL.isBlank()) return
        
        viewModelScope.launch {
            val update = updateManager.checkForUpdates(UPDATE_URL)
            uiState = uiState.copy(availableUpdate = update)
        }
    }

    fun setAvailableUpdate(update: AppUpdate?) {
        uiState = uiState.copy(availableUpdate = update)
    }

    fun downloadAndInstallUpdate() {
        val update = uiState.availableUpdate ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isDownloadingUpdate = true, updateDownloadProgress = 0f)
            val success = updateManager.downloadAndInstallApk(update) { progress ->
                uiState = uiState.copy(updateDownloadProgress = progress)
            }
            uiState = uiState.copy(isDownloadingUpdate = false)
            if (!success) {
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed_msg))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopP2PServer()
        resourceDownloadJob?.cancel()
    }

    fun updateVpnStatus() {
        uiState = uiState.copy(isVpnEnabled = XrayVpnService.isServiceRunning)
    }

    fun setScreen(screen: Screen) {
        uiState = uiState.copy(currentScreen = screen)
    }

    fun selectProfile(id: String?) {
        uiState = uiState.copy(selectedProfileId = id)
        profileRepository.setSelectedProfileId(id)
    }

    fun getSelectedProfileContent(): String? {
        val profile = uiState.profiles.find { it.id == uiState.selectedProfileId } ?: return null
        return profileRepository.getProfileContent(profile)
    }

    // --- Resource Operations ---

    fun applyResourcePreset(type: ResourcePresetType) {
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            uiState = uiState.copy(
                isDownloadingResource = true, 
                resourceDownloadProgress = 0f,
                resourceDownloadProgress2 = 0f,
                currentDownloadingResourceName = null,
                currentDownloadingResourceName2 = null
            )
            LogManager.addLog("[Resource] Applying preset: $type")
            
            try {
                when (type) {
                    ResourcePresetType.SYSTEM -> {
                        resourceRepository.deleteResource("geoip.dat")
                        resourceRepository.deleteResource("geosite.dat")
                        LogManager.addLog("[Resource] Preset System: Custom geo-files deleted")
                    }
                    ResourcePresetType.RUNETFREEDOM -> {
                        val geoipUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/raw/refs/heads/release/geoip.dat"
                        val geositeUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/raw/refs/heads/release/geosite.dat"
                        downloadTwoFilesParallel(geoipUrl, geositeUrl)
                    }
                    ResourcePresetType.LOYAL_SOLDIER -> {
                        val geoipUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/refs/heads/release/geoip.dat"
                        val geositeUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/refs/heads/release/geosite.dat"
                        downloadTwoFilesParallel(geoipUrl, geositeUrl)
                    }
                }
                uiState = uiState.copy(resources = resourceRepository.getResources())
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.all_resources_reloaded))
            } catch (e: Exception) {
                val error = e.message ?: "Error"
                LogManager.addLog("[Resource] Preset error: $error")
                _events.emit(MainUiEvent.ShowToast(message = error))
            } finally {
                uiState = uiState.copy(
                    isDownloadingResource = false,
                    currentDownloadingResourceName = null,
                    currentDownloadingResourceName2 = null
                )
            }
        }
    }

    private suspend fun downloadTwoFilesParallel(geoipUrl: String, geositeUrl: String) = withContext(Dispatchers.IO) {
        uiState = uiState.copy(currentDownloadingResourceName = "geoip.dat", currentDownloadingResourceName2 = "geosite.dat")
        
        val d1 = async {
            resourceRepository.addOrUpdateResource("geoip.dat", geoipUrl, true, 24) { progress ->
                uiState = uiState.copy(resourceDownloadProgress = progress)
            }
        }
        
        val d2 = async {
            resourceRepository.addOrUpdateResource("geosite.dat", geositeUrl, true, 24) { progress ->
                uiState = uiState.copy(resourceDownloadProgress2 = progress)
            }
        }
        
        val r1 = d1.await()
        val r2 = d2.await()
        
        if (r1.isFailure) throw r1.exceptionOrNull() ?: Exception("Failed to download geoip.dat")
        if (r2.isFailure) throw r2.exceptionOrNull() ?: Exception("Failed to download geosite.dat")
    }

    fun addRemoteResource(name: String, url: String, autoUpdate: Boolean, interval: Int) {
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            uiState = uiState.copy(isDownloadingResource = true, resourceDownloadProgress = 0f, currentDownloadingResourceName = name)
            val result = resourceRepository.addOrUpdateResource(name, url, autoUpdate, interval) { progress ->
                uiState = uiState.copy(resourceDownloadProgress = progress)
            }
            uiState = uiState.copy(isDownloadingResource = false, currentDownloadingResourceName = null)
            if (result.isSuccess) {
                uiState = uiState.copy(resources = resourceRepository.getResources())
                LogManager.addLog("[Resource] Added: $name from $url")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.resource_added))
                setupResourceUpdates()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                if (error != "Job was cancelled") {
                    LogManager.addLog("[Resource] Error adding $name: $error")
                    _events.emit(MainUiEvent.ShowToast(message = error))
                }
            }
        }
    }

    fun cancelResourceDownload() {
        resourceDownloadJob?.cancel()
        uiState = uiState.copy(isDownloadingResource = false, currentDownloadingResourceName = null, currentDownloadingResourceName2 = null)
    }

    fun addLocalResource(name: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val result = resourceRepository.addOrUpdateResource(name, null, false, 0, bytes)
                    if (result.isSuccess) {
                        uiState = uiState.copy(resources = resourceRepository.getResources())
                        LogManager.addLog("[Resource] Added local: $name")
                        _events.emit(MainUiEvent.ShowToast(messageResId = R.string.resource_added))
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        LogManager.addLog("[Resource] Error adding local $name: $error")
                        _events.emit(MainUiEvent.ShowToast(message = error))
                    }
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                LogManager.addLog("[Resource] Exception adding local $name: $error")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.failed_to_add_resource))
            }
        }
    }

    fun deleteResource(name: String) {
        viewModelScope.launch {
            resourceRepository.deleteResource(name)
            LogManager.addLog("[Resource] Deleted: $name")
            uiState = uiState.copy(resources = resourceRepository.getResources())
            setupResourceUpdates()
        }
    }

    fun updateResource(resource: Resource) {
        val url = resource.url ?: return
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            uiState = uiState.copy(isDownloadingResource = true, resourceDownloadProgress = 0f, currentDownloadingResourceName = resource.name)
            val result = resourceRepository.addOrUpdateResource(
                resource.name, 
                url, 
                resource.autoUpdateEnabled, 
                resource.autoUpdateIntervalHours
            ) { progress ->
                uiState = uiState.copy(resourceDownloadProgress = progress)
            }
            uiState = uiState.copy(isDownloadingResource = false, currentDownloadingResourceName = null)
            if (result.isSuccess) {
                uiState = uiState.copy(resources = resourceRepository.getResources())
                LogManager.addLog("[Resource] Updated: ${resource.name}")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.resource_updated))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                if (error != "Job was cancelled") {
                    LogManager.addLog("[Resource] Update error for ${resource.name}: $error")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.failed_to_update_resource))
                }
            }
        }
    }

    fun reloadAllResources() {
        viewModelScope.launch {
            val resources = uiState.resources.filter { it.url != null }
            if (resources.isEmpty()) return@launch
            
            uiState = uiState.copy(isDownloadingResource = true, resourceDownloadProgress = 0f)
            var successCount = 0
            for (res in resources) {
                uiState = uiState.copy(currentDownloadingResourceName = res.name)
                val result = resourceRepository.addOrUpdateResource(res.name, res.url, res.autoUpdateEnabled, res.autoUpdateIntervalHours) { progress ->
                    uiState = uiState.copy(resourceDownloadProgress = progress)
                }
                if (result.isSuccess) {
                    successCount++
                } else {
                    LogManager.addLog("[Resource] Batch update error for ${res.name}: ${result.exceptionOrNull()?.message}")
                }
            }
            uiState = uiState.copy(isDownloadingResource = false, resources = resourceRepository.getResources(), currentDownloadingResourceName = null)
            
            if (successCount == resources.size) {
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.all_resources_reloaded))
            } else {
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.failed_to_reload_resources))
            }
        }
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
                    val groups = profileRepository.getGroups().toMutableList()
                    groups.add(Group(name = name))
                    profileRepository.saveGroups(groups)
                    uiState = uiState.copy(groups = profileRepository.getGroups())
                    onComplete()
                } else {
                    val result = profileRepository.addRemoteGroup(name, url, autoUpdate, interval)
                    if (result.isSuccess) {
                        uiState = uiState.copy(
                            groups = profileRepository.getGroups(),
                            profiles = profileRepository.getProfiles()
                        )
                        _events.emit(MainUiEvent.ShowToast(messageResId = R.string.group_added, formatArgs = listOf(name)))
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
                val groups = profileRepository.getGroups().toMutableList()
                val index = groups.indexOfFirst { it.id == existing.id }
                if (index != -1) {
                    groups[index] = updated
                    profileRepository.saveGroups(groups)
                    uiState = uiState.copy(groups = profileRepository.getGroups())
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
                profileRepository.updateGroupProfiles(updatedGroup)
                val groups = profileRepository.getGroups().toMutableList()
                val index = groups.indexOfFirst { it.id == existingGroup.id }
                if (index != -1) {
                    groups[index] = updatedGroup
                    profileRepository.saveGroups(groups)
                }
                uiState = uiState.copy(
                    groups = profileRepository.getGroups(),
                    profiles = profileRepository.getProfiles()
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
            profileRepository.deleteGroup(groupId)
            uiState = uiState.copy(
                groups = profileRepository.getGroups(),
                profiles = profileRepository.getProfiles(),
                selectedProfileId = profileRepository.getSelectedProfileId()
            )
        }
    }

    fun refreshGroup(group: Group) {
        viewModelScope.launch {
            try {
                profileRepository.updateGroupProfiles(group)
                uiState = uiState.copy(profiles = profileRepository.getProfiles())
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
                val result = profileRepository.addRemoteProfile(name, url, autoUpdate, interval, groupId)
                if (result.isSuccess) {
                    uiState = uiState.copy(profiles = profileRepository.getProfiles())
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_added, formatArgs = listOf(name)))
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
                    profileRepository.saveLocalProfile(name, content, groupId)
                }
            } else {
                val needsRestart = updateProfileInternal(existing.copy(name = name), content)
                if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
            }
            uiState = uiState.copy(profiles = profileRepository.getProfiles())
            onComplete()
        }
    }

    fun replaceRemoteProfile(existingProfile: Profile, url: String, autoUpdate: Boolean, interval: Int) {
        viewModelScope.launch {
            try {
                val fileName = existingProfile.fileName ?: "${existingProfile.id}.json"
                val wasRunning = XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == existingProfile.id
                val contentChanged = profileRepository.downloadProfile(url, fileName)
                
                val updatedProfile = existingProfile.copy(
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval,
                    lastUpdated = System.currentTimeMillis(),
                    fileName = fileName
                )
                
                withContext(Dispatchers.IO) {
                    profileRepository.updateProfile(updatedProfile)
                }
                uiState = uiState.copy(profiles = profileRepository.getProfiles())
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
                    val changed = profileRepository.downloadProfile(url, fileName)
                    if (changed) {
                        withContext(Dispatchers.IO) {
                            profileRepository.updateProfile(profile.copy(lastUpdated = System.currentTimeMillis()))
                        }
                        if (XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id) needsRestart = true
                    }
                    success++
                } catch (e: Exception) { 
                    LogManager.addLog("[App] Failed to refresh profile '${profile.name}': ${e.message}")
                    failed++ 
                }
            }
            
            if (success > 0) uiState = uiState.copy(profiles = profileRepository.getProfiles())
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profiles_update_result, formatArgs = listOf(success, failed)))
            if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    private suspend fun updateProfileInternal(profile: Profile, content: String? = null): Boolean = withContext(Dispatchers.IO) {
        var contentChanged = false
        if (content != null) {
            contentChanged = profileRepository.updateLocalProfileContent(profile, content)
        }
        profileRepository.updateProfile(profile)
        
        withContext(Dispatchers.Main) {
            uiState = uiState.copy(profiles = profileRepository.getProfiles())
        }
        
        return@withContext contentChanged && XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id
    }

    fun deleteProfiles(ids: Set<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                profileRepository.deleteProfiles(ids)
            }
            uiState = uiState.copy(
                profiles = profileRepository.getProfiles(),
                selectedProfileId = profileRepository.getSelectedProfileId()
            )
        }
    }

    fun getProfileContent(profile: Profile): String = profileRepository.getProfileContent(profile) ?: ""

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
                            profileRepository.addRemoteProfile(p.name, p.url, p.autoUpdateEnabled, p.autoUpdateIntervalMinutes)
                        } else if (payload.profileContent != null) {
                            withContext(Dispatchers.IO) {
                                profileRepository.saveLocalProfile(p.name, payload.profileContent)
                            }
                        }
                    }
                } else if (payload.group != null) {
                    val g = payload.group
                    val groups = profileRepository.getGroups().toMutableList()
                    val targetGroupId = if (groups.any { it.name == g.name }) {
                        groups.find { it.name == g.name }?.id ?: g.id
                    } else {
                        val newG = g.copy(id = java.util.UUID.randomUUID().toString())
                        groups.add(newG)
                        profileRepository.saveGroups(groups)
                        newG.id
                    }
                    
                    payload.groupProfiles?.forEach { (profile, content) ->
                        if (profile.isRemote && profile.url != null) {
                             profileRepository.addRemoteProfile(profile.name, profile.url, profile.autoUpdateEnabled, profile.autoUpdateIntervalMinutes, targetGroupId)
                        } else if (content != null) {
                            profileRepository.saveLocalProfile(profile.name, content, targetGroupId)
                        }
                    }
                }
                uiState = uiState.copy(groups = profileRepository.getGroups(), profiles = profileRepository.getProfiles())
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
                        profileRepository.getProfileContent(profile)
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
                            profileRepository.getProfileContent(profile)
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

enum class ResourcePresetType {
    SYSTEM, RUNETFREEDOM, LOYAL_SOLDIER
}
