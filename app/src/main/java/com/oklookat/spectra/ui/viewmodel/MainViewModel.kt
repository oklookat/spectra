package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.domain.usecase.profile.AddRemoteProfileUseCase
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

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val saveGroupUseCase: SaveGroupUseCase,
    private val saveLocalProfileUseCase: SaveLocalProfileUseCase,
    private val addRemoteProfileUseCase: AddRemoteProfileUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val prepareVpnConfigUseCase: PrepareVpnConfigUseCase,
    private val checkDeepLinkStatusUseCase: CheckDeepLinkStatusUseCase
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
                        vpnMtu = settings.vpnMtu
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
                    if (existing == null) {
                        if (p.isRemote && p.url != null) {
                            addRemoteProfileUseCase(p.name, p.url, p.autoUpdateEnabled, p.autoUpdateIntervalMinutes)
                        } else if (payload.profileContent != null) {
                            saveLocalProfileUseCase(p.name, payload.profileContent)
                        }
                    }
                } else if (payload.group != null) {
                    val g = payload.group
                    val newG = g.copy(id = java.util.UUID.randomUUID().toString())
                    saveGroupUseCase(newG)
                    val targetGroupId = newG.id
                    
                    payload.groupProfiles?.forEach { (profile, content) ->
                        if (profile.isRemote && profile.url != null) {
                             addRemoteProfileUseCase(profile.name, profile.url, profile.autoUpdateEnabled, profile.autoUpdateIntervalMinutes, targetGroupId)
                        } else if (content != null) {
                            saveLocalProfileUseCase(profile.name, content, targetGroupId)
                        }
                    }
                }
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

    override fun onCleared() {
        super.onCleared()
        stopP2PServer()
    }
}
