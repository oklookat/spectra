package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.R
import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.domain.usecase.profile.*
import com.oklookat.spectra.domain.usecase.settings.GetSettingsUseCase
import com.oklookat.spectra.domain.usecase.settings.SetSelectedProfileIdUseCase
import com.oklookat.spectra.model.*
import com.oklookat.spectra.service.XrayVpnService
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.TvUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    application: Application,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val getGroupsUseCase: GetGroupsUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val addRemoteGroupUseCase: AddRemoteGroupUseCase,
    private val saveGroupUseCase: SaveGroupUseCase,
    private val updateGroupProfilesUseCase: UpdateGroupProfilesUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val addRemoteProfileUseCase: AddRemoteProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val deleteProfilesUseCase: DeleteProfilesUseCase,
    private val saveLocalProfileUseCase: SaveLocalProfileUseCase,
    private val setSelectedProfileIdUseCase: SetSelectedProfileIdUseCase,
    private val profileRepository: ProfileRepository
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(ProfilesUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        observeData()
        checkDeepLinkStatus()
    }

    private fun observeData() {
        getGroupsUseCase()
            .onEach { groupsFromDb ->
                val defaultGroup = Group(id = Group.DEFAULT_GROUP_ID, name = "Default")
                val finalGroups = if (groupsFromDb.none { it.id == Group.DEFAULT_GROUP_ID }) {
                    listOf(defaultGroup) + groupsFromDb
                } else {
                    val mutable = groupsFromDb.toMutableList()
                    val idx = mutable.indexOfFirst { it.id == Group.DEFAULT_GROUP_ID }
                    if (idx > 0) {
                        val d = mutable.removeAt(idx)
                        mutable.add(0, d)
                    }
                    mutable
                }
                uiState = uiState.copy(groups = finalGroups)
            }
            .launchIn(viewModelScope)

        getProfilesUseCase()
            .onEach { profiles ->
                uiState = uiState.copy(profiles = profiles)
            }
            .launchIn(viewModelScope)

        getSettingsUseCase()
            .onEach { settings ->
                uiState = uiState.copy(selectedProfileId = settings.selectedProfileId)
            }
            .launchIn(viewModelScope)
    }

    fun selectProfile(id: String?) {
        viewModelScope.launch {
            setSelectedProfileIdUseCase(id)
        }
    }

    fun setSortOrder(sortOrder: ProfileSort) {
        uiState = uiState.copy(sortOrder = sortOrder)
    }

    // --- Unified Refresh ---

    fun refresh(groupId: String? = null) {
        viewModelScope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                if (groupId == null) {
                    refreshRemoteProfiles(null)
                    uiState.groups.filter { it.isRemote }.forEach { group ->
                        refreshGroupInternal(group)
                    }
                } else {
                    val group = uiState.groups.find { it.id == groupId } ?: return@launch
                    if (group.isRemote) {
                        refreshGroupInternal(group)
                    } else {
                        val profileIds = uiState.profiles
                            .filter { it.groupId == groupId && it.isRemote && !it.isImported }
                            .map { it.id }
                            .toSet()
                        if (profileIds.isNotEmpty()) {
                            refreshRemoteProfilesInternal(profileIds)
                        }
                    }
                }
            } finally {
                uiState = uiState.copy(isRefreshing = false)
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
            val isDefault = existing?.id == Group.DEFAULT_GROUP_ID
            val shouldCreateNew = existing == null || (isDefault && !url.isNullOrBlank())

            if (shouldCreateNew) {
                if (url.isNullOrBlank()) {
                    saveGroupUseCase(Group(name = name))
                } else {
                    val result = addRemoteGroupUseCase(name, url, autoUpdate, interval)
                    if (result.isSuccess) {
                        _events.emit(MainUiEvent.ShowToast(messageResId = R.string.group_added, formatArgs = listOf(name)))
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        LogManager.addLog("[App] Error adding remote group: $error")
                        _events.emit(MainUiEvent.ShowToast(message = error))
                    }
                }
            } else {
                val updated = existing!!.copy(
                    name = name,
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalMinutes = interval
                )
                saveGroupUseCase(updated)
            }
            onComplete()
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            deleteGroupUseCase(groupId)
        }
    }

    fun refreshGroup(group: Group) {
        viewModelScope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                refreshGroupInternal(group)
            } finally {
                uiState = uiState.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun refreshGroupInternal(group: Group) {
        try {
            updateGroupProfilesUseCase(group)
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_updated))
        } catch (e: Exception) {
            LogManager.addLog("[App] Error refreshing group: ${e.message}")
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.update_failed))
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
                val result = addRemoteProfileUseCase(name, url, autoUpdate, interval, groupId)
                if (result.isSuccess) {
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profile_added, formatArgs = listOf(name)))
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
            }
            onComplete()
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
                saveLocalProfileUseCase(name, content, groupId)
            } else {
                val needsRestart = updateProfileInternal(existing.copy(name = name), content)
                if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
            }
            onComplete()
        }
    }

    fun refreshRemoteProfiles(ids: Set<String>? = null) {
        viewModelScope.launch {
            uiState = uiState.copy(isRefreshing = true)
            try {
                refreshRemoteProfilesInternal(ids)
            } finally {
                uiState = uiState.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun refreshRemoteProfilesInternal(ids: Set<String>? = null) {
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
                    updateProfileUseCase(profile.copy(lastUpdated = System.currentTimeMillis()))
                    if (XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id) needsRestart = true
                }
                success++
            } catch (e: Exception) { 
                LogManager.addLog("[App] Failed to refresh profile '${profile.name}': ${e.message}")
                failed++ 
            }
        }
        
        if (ids == null) {
            _events.emit(MainUiEvent.ShowToast(messageResId = R.string.profiles_update_result, formatArgs = listOf(success, failed)))
        }
        if (needsRestart) _events.emit(MainUiEvent.RestartVpn)
    }

    private suspend fun updateProfileInternal(profile: Profile, content: String? = null): Boolean {
        var contentChanged = false
        if (content != null) {
            contentChanged = profileRepository.updateLocalProfileContent(profile, content)
        }
        updateProfileUseCase(profile)
        
        return contentChanged && XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id
    }

    fun deleteProfiles(ids: Set<String>) {
        viewModelScope.launch {
            deleteProfilesUseCase(ids)
        }
    }

    fun getProfileContent(profile: Profile): String = profileRepository.getProfileContent(profile) ?: ""

    // --- Ping ---

    fun measurePing(profile: Profile) {
        viewModelScope.launch {
            profileRepository.measurePing(profile)
        }
    }

    fun measureAllPings(groupId: String? = null) {
        viewModelScope.launch {
            val profiles = if (groupId != null) {
                uiState.profiles.filter { it.groupId == groupId }
            } else {
                uiState.profiles
            }
            profileRepository.measurePings(profiles)
        }
    }

    // --- Deep Link Helpers ---

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
}
