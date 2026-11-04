package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.domain.usecase.profile.AddRemoteGroupUseCase
import com.oklookat.spectra.domain.usecase.profile.AddRemoteProfileUseCase
import com.oklookat.spectra.domain.usecase.profile.GetGroupsUseCase
import com.oklookat.spectra.domain.usecase.profile.GetProfilesUseCase
import com.oklookat.spectra.domain.usecase.profile.SaveGroupUseCase
import com.oklookat.spectra.domain.usecase.profile.SaveLocalProfileUseCase
import com.oklookat.spectra.domain.usecase.settings.CheckDeepLinkStatusUseCase
import com.oklookat.spectra.domain.usecase.settings.GetSettingsUseCase
import com.oklookat.spectra.domain.usecase.vpn.PrepareVpnConfigUseCase
import com.oklookat.spectra.model.*
import com.oklookat.spectra.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.oklookat.spectra.data.repository.AppFilterRepository
import kotlinx.coroutines.flow.first

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val getGroupsUseCase: GetGroupsUseCase,
    private val saveGroupUseCase: SaveGroupUseCase,
    private val saveLocalProfileUseCase: SaveLocalProfileUseCase,
    private val addRemoteProfileUseCase: AddRemoteProfileUseCase,
    private val addRemoteGroupUseCase: AddRemoteGroupUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val prepareVpnConfigUseCase: PrepareVpnConfigUseCase,
    private val checkDeepLinkStatusUseCase: CheckDeepLinkStatusUseCase,
    private val appFilterRepository: AppFilterRepository
) : AndroidViewModel(application) {
    private val updateManager = UpdateManager(application)
    private val p2pClient = P2PClient()
    private var p2pServer: P2PServer? = null
    
    private val UPDATE_URL = BuildConfig.UPDATE_URL

    var uiState by mutableStateOf(MainUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        LogManager.initWelcomeLogs(BuildConfig.VERSION_NAME)
        observeData()
        setupAppUpdates()
        
        if (UPDATE_URL.isNotBlank()) {
            checkForUpdatesManually()
        }
    }

    private fun observeData() {
        getProfilesUseCase()
            .onEach { profiles ->
                uiState = uiState.copy(profiles = profiles)
            }
            .launchIn(viewModelScope)

        getGroupsUseCase()
            .onEach { groups ->
                uiState = uiState.copy(groups = groups)
            }
            .launchIn(viewModelScope)

        getSettingsUseCase()
            .onEach { settings ->
                uiState = uiState.copy(
                    selectedProfileId = settings.selectedProfileId,
                    settings = SettingsState(
                        isIpv6Enabled = settings.isIpv6Enabled,
                        vpnAddress = settings.vpnAddress,
                        vpnDns = settings.vpnDns,
                        vpnAddressIpv6 = settings.vpnAddressIpv6,
                        vpnDnsIpv6 = settings.vpnDnsIpv6,
                        vpnMtu = settings.vpnMtu,
                        p2pShareMode = settings.p2pShareMode
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    private fun setupAppUpdates() {
        AppUpdateWorker.setupPeriodicWork(getApplication(), UPDATE_URL)
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
                LogManager.addLog("[App] Update download or installation failed")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed_msg))
            }
        }
    }

    fun setScreen(screen: Screen) {
        uiState = uiState.copy(currentScreen = screen)
    }

    suspend fun prepareVpnConfig(): String = try {
        prepareVpnConfigUseCase()
    } catch (e: Exception) {
        LogManager.addLog("[App] Failed to prepare VPN config: ${e.message}")
        ""
    }

    suspend fun getDisallowedPackages(): Set<String> = try {
        appFilterRepository.activeDisallowedPackagesFlow.first()
    } catch (_: Exception) {
        emptySet()
    }

    fun refreshBatteryOptimizationStatus() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
        }
    }

    fun refreshWifiStatus() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.preset_updated))
        }
    }

    fun requestBatteryOptimization() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.RequestBatteryOptimization)
        }
    }

    fun requestLocationPermission() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.RequestLocationPermission)
        }
    }

    fun checkDeepLinkStatus() {
        uiState = uiState.copy(isDeepLinkVerified = checkDeepLinkStatusUseCase())
    }

    fun setDeepLinkProfile(profile: PendingProfile?) {
        uiState = uiState.copy(deepLinkProfile = profile)
    }

    fun setDeepLinkGroup(group: PendingGroup?) {
        uiState = uiState.copy(deepLinkGroup = group)
    }

    fun openDeepLinkSettings() {
        viewModelScope.launch {
            _events.emit(MainUiEvent.OpenDeepLinkSettings)
        }
    }

    // P2P Methods
    fun startP2PServer() {
        if (p2pServer != null) {
            LogManager.addLog("[P2P] Server is already running")
            return
        }
        val server = P2PServer(
            context = getApplication(),
            onPayloadReceived = { payload ->
                LogManager.addLog("[P2P] onPayloadReceived called on MainViewModel for device: ${payload.deviceName}")
                viewModelScope.launch(Dispatchers.Main) {
                    val existing = if (payload.groupName != null) {
                        uiState.groups.find { it.name == payload.groupName }
                    } else if (payload.profileName.isNotBlank()) {
                        uiState.profiles.find { it.name == payload.profileName }
                    } else null
                    
                    LogManager.addLog("[P2P] Updating uiState with p2pPayloadToAccept, existing item match: ${existing != null}")
                    uiState = uiState.copy(
                        p2pPayloadToAccept = payload,
                        showP2PReplaceDialog = existing != null
                    )
                }
            }
        )
        try {
            server.start()
            p2pServer = server
            val shareUrl = server.getShareUrl()
            uiState = uiState.copy(isP2PServerRunning = true, p2pServerUrl = shareUrl, p2pServerToken = server.token)
            LogManager.addLog("[P2P] Server started successfully at $shareUrl with token ${server.token}")
        } catch (e: Exception) {
            val error = e.message ?: "Unknown error"
            LogManager.addLog("[P2P] Server start error: $error")
            viewModelScope.launch { _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_server_error, formatArgs = listOf(error))) }
        }
    }

    fun stopP2PServer() {
        p2pServer?.stop()
        p2pServer = null
        uiState = uiState.copy(isP2PServerRunning = false, p2pServerUrl = null, p2pServerToken = null, p2pPayloadToAccept = null, showP2PReplaceDialog = false)
        LogManager.addLog("[App] P2P Server stopped")
    }

    fun sendP2PPayload(url: String, token: String, payload: P2PPayload) {
        viewModelScope.launch {
            LogManager.addLog("[P2P] Starting sendP2PPayload to $url with token $token")
            val finalPayload = payload.copy(
                deviceName = Build.MODEL,
                token = token
            )
            val result = p2pClient.sendPayload(url, finalPayload)
            if (result.isSuccess) {
                LogManager.addLog("[P2P] sendP2PPayload succeeded")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_sent_success))
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                LogManager.addLog("[P2P] sendP2PPayload failed: $error")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_send_failed, formatArgs = listOf(error)))
            }
        }
    }

    fun acceptP2PPayload() {
        val payload = uiState.p2pPayloadToAccept ?: return
        viewModelScope.launch {
            try {
                var objectName = "Object"
                if (payload.groupName != null) {
                    objectName = payload.groupName
                    val existingGroup = uiState.groups.find { it.name == payload.groupName }
                    val targetGroup = existingGroup ?: Group(id = java.util.UUID.randomUUID().toString(), name = payload.groupName).also { saveGroupUseCase(it) }
                    payload.groupProfiles?.forEach { item ->
                        val p = item.profile
                        if (p.isRemote && p.url != null) {
                            addRemoteProfileUseCase(p.name, p.url, p.autoUpdateEnabled, p.autoUpdateIntervalMinutes, targetGroup.id)
                        } else if (item.content != null) {
                            saveLocalProfileUseCase(p.name, item.content, targetGroup.id)
                        }
                    }
                } else if (payload.profileName.isNotBlank()) {
                    objectName = payload.profileName
                    if (payload.isRemote && payload.profileUrl != null) {
                        addRemoteProfileUseCase(payload.profileName, payload.profileUrl, payload.autoUpdateEnabled, payload.autoUpdateIntervalMinutes)
                    } else if (payload.profileContent != null) {
                        saveLocalProfileUseCase(payload.profileName, payload.profileContent)
                    }
                }
                LogManager.addLog("[App] P2P payload accepted: $objectName")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf(objectName)))
            } catch (e: Exception) {
                LogManager.addLog("[App] Error accepting P2P payload: ${e.message}")
            }
            stopP2PServer()
        }
    }

    fun importFromQrString(rawValue: String, p2pPayloadToSend: P2PPayload? = null) {
        viewModelScope.launch {
            try {
                val trimmed = rawValue.trim()
                LogManager.addLog("[Import] Processing QR content: ${trimmed.take(60)}...")

                if (trimmed.startsWith("spectra://import")) {
                    val uri = android.net.Uri.parse(trimmed)
                    val name = uri.getQueryParameter("name") ?: "Imported"
                    val type = uri.getQueryParameter("type")
                    val compressedData = uri.getQueryParameter("data")
                    if (!compressedData.isNullOrBlank()) {
                        val decompressed = P2PCompressUtils.decompress(compressedData)
                        if (type == "group") {
                            val bundle = P2PCompressUtils.parseGroupBundle(decompressed)
                            if (bundle != null) {
                                val targetGroup = bundle.group.copy(id = java.util.UUID.randomUUID().toString())
                                saveGroupUseCase(targetGroup)
                                bundle.profiles.forEach { item ->
                                    val profile = item.profile
                                    if (profile.isRemote && profile.url != null) {
                                        addRemoteProfileUseCase(profile.name, profile.url, profile.autoUpdateEnabled, profile.autoUpdateIntervalMinutes, targetGroup.id)
                                    } else if (item.content != null) {
                                        saveLocalProfileUseCase(profile.name, item.content, targetGroup.id)
                                    }
                                }
                                LogManager.addLog("[Import] Group imported from QR: ${targetGroup.name}")
                                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf(targetGroup.name)))
                            }
                        } else {
                            saveLocalProfileUseCase(name, decompressed)
                            LogManager.addLog("[Import] Profile imported from QR: $name")
                            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf(name)))
                        }
                    }
                } else if (trimmed.startsWith("spectra://p2p")) {
                    val uri = android.net.Uri.parse(trimmed)
                    val url = uri.getQueryParameter("url")
                    val token = uri.getQueryParameter("token")
                    if (!url.isNullOrBlank() && !token.isNullOrBlank() && p2pPayloadToSend != null) {
                        sendP2PPayload(url, token, p2pPayloadToSend)
                    }
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    addRemoteProfileUseCase("Remote Profile", trimmed, false, 0)
                    LogManager.addLog("[Import] Remote profile imported: $trimmed")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf("Remote Profile")))
                } else if (ShareLinkParser.isShareLink(trimmed)) {
                    val node = ShareLinkParser.parseNode(trimmed)
                    val profileName = if (node.remarks.isNotBlank()) node.remarks else "Imported Profile"
                    saveLocalProfileUseCase(profileName, trimmed)
                    LogManager.addLog("[Import] Profile link imported: $profileName")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_processed, formatArgs = listOf(profileName)))
                } else {
                    LogManager.addLog("[Import] Unknown QR string format")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_send_failed, formatArgs = listOf("Unknown QR format")))
                }
            } catch (e: Exception) {
                LogManager.addLog("[Import] QR import error: ${e.message}")
                _events.emit(MainUiEvent.ShowToast(messageResId = R.string.p2p_send_failed, formatArgs = listOf(e.message ?: "Import error")))
            }
        }
    }

    fun rejectP2PPayload() {
        stopP2PServer()
    }

    override fun onCleared() {
        super.onCleared()
        stopP2PServer()
    }
}
