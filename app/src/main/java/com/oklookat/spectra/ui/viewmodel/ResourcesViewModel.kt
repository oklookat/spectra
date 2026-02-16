package com.oklookat.spectra.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.R
import com.oklookat.spectra.domain.usecase.resource.AddOrUpdateResourceUseCase
import com.oklookat.spectra.domain.usecase.resource.DeleteResourceUseCase
import com.oklookat.spectra.domain.usecase.resource.GetResourcesUseCase
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.ResourceUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

enum class ResourcePresetType {
    SYSTEM, RUNETFREEDOM, LOYAL_SOLDIER
}

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    application: Application,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val addOrUpdateResourceUseCase: AddOrUpdateResourceUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(ResourcesUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    private var resourceDownloadJob: Job? = null

    init {
        observeResources()
    }

    private fun observeResources() {
        getResourcesUseCase()
            .onEach { resources ->
                uiState = uiState.copy(resources = resources)
            }
            .launchIn(viewModelScope)
    }

    fun applyResourcePreset(type: ResourcePresetType) {
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            uiState = uiState.copy(downloadingResources = emptyMap())
            LogManager.addLog("[Resource] Applying preset: $type")
            
            try {
                when (type) {
                    ResourcePresetType.SYSTEM -> {
                        deleteResourceUseCase("geoip.dat")
                        deleteResourceUseCase("geosite.dat")
                        LogManager.addLog("[Resource] Preset System: Custom geo-files deleted")
                        _events.emit(MainUiEvent.ShowToast(messageResId = R.string.all_resources_reloaded))
                    }
                    ResourcePresetType.RUNETFREEDOM -> {
                        val geoipUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/raw/refs/heads/release/geoip.dat"
                        val geositeUrl = "https://github.com/runetfreedom/russia-v2ray-rules-dat/raw/refs/heads/release/geosite.dat"
                        val anyDownloaded = downloadTwoFilesParallel(geoipUrl, geositeUrl)
                        val msg = if (anyDownloaded) R.string.all_resources_reloaded else R.string.all_resources_up_to_date
                        _events.emit(MainUiEvent.ShowToast(messageResId = msg))
                    }
                    ResourcePresetType.LOYAL_SOLDIER -> {
                        val geoipUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/refs/heads/release/geoip.dat"
                        val geositeUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/refs/heads/release/geosite.dat"
                        val anyDownloaded = downloadTwoFilesParallel(geoipUrl, geositeUrl)
                        val msg = if (anyDownloaded) R.string.all_resources_reloaded else R.string.all_resources_up_to_date
                        _events.emit(MainUiEvent.ShowToast(messageResId = msg))
                    }
                }
            } catch (e: Exception) {
                val error = e.message ?: "Error"
                LogManager.addLog("[Resource] Preset error: $error")
                if (e !is CancellationException) {
                    _events.emit(MainUiEvent.ShowToast(message = error))
                }
            } finally {
                uiState = uiState.copy(downloadingResources = emptyMap())
            }
        }
    }

    private suspend fun downloadTwoFilesParallel(geoipUrl: String, geositeUrl: String): Boolean = withContext(Dispatchers.IO) {
        val d1 = async {
            addOrUpdateResourceUseCase("geoip.dat", geoipUrl, true, 24) { progress ->
                updateDownloadProgress("geoip.dat", progress)
            }
        }
        
        val d2 = async {
            addOrUpdateResourceUseCase("geosite.dat", geositeUrl, true, 24) { progress ->
                updateDownloadProgress("geosite.dat", progress)
            }
        }
        
        val r1 = d1.await()
        val r2 = d2.await()
        
        if (r1.isFailure) throw r1.exceptionOrNull() ?: Exception("Failed to download geoip.dat")
        if (r2.isFailure) throw r2.exceptionOrNull() ?: Exception("Failed to download geosite.dat")
        
        return@withContext r1.getOrNull() == true || r2.getOrNull() == true
    }

    private fun updateDownloadProgress(name: String, progress: Float) {
        val current = uiState.downloadingResources.toMutableMap()
        current[name] = progress
        uiState = uiState.copy(downloadingResources = current)
    }

    fun addRemoteResource(name: String, url: String, autoUpdate: Boolean, interval: Int) {
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            updateDownloadProgress(name, 0f)
            val result = addOrUpdateResourceUseCase(name, url, autoUpdate, interval) { progress ->
                updateDownloadProgress(name, progress)
            }
            if (result.isSuccess) {
                val wasDownloaded = result.getOrNull() == true
                uiState = uiState.copy(
                    downloadingResources = uiState.downloadingResources - name
                )
                LogManager.addLog("[Resource] Added: $name from $url")
                val msg = if (wasDownloaded) R.string.resource_added else R.string.resource_up_to_date
                _events.emit(MainUiEvent.ShowToast(messageResId = msg))
                ResourceUpdateWorker.setupPeriodicWork(getApplication())
            } else {
                uiState = uiState.copy(downloadingResources = uiState.downloadingResources - name)
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
        uiState = uiState.copy(downloadingResources = emptyMap())
    }

    fun addLocalResource(name: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val result = addOrUpdateResourceUseCase(name, null, false, 0, bytes)
                    if (result.isSuccess) {
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
            deleteResourceUseCase(name)
            LogManager.addLog("[Resource] Deleted: $name")
            ResourceUpdateWorker.setupPeriodicWork(getApplication())
        }
    }

    fun updateResource(resource: Resource) {
        val url = resource.url ?: return
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            updateDownloadProgress(resource.name, 0f)
            val result = addOrUpdateResourceUseCase(
                resource.name, 
                url, 
                resource.autoUpdateEnabled, 
                resource.autoUpdateIntervalHours
            ) { progress ->
                updateDownloadProgress(resource.name, progress)
            }
            if (result.isSuccess) {
                val wasDownloaded = result.getOrNull() == true
                uiState = uiState.copy(
                    downloadingResources = uiState.downloadingResources - resource.name
                )
                LogManager.addLog("[Resource] Updated: ${resource.name}")
                val msg = if (wasDownloaded) R.string.resource_updated else R.string.resource_up_to_date
                _events.emit(MainUiEvent.ShowToast(messageResId = msg))
            } else {
                uiState = uiState.copy(downloadingResources = uiState.downloadingResources - resource.name)
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                if (error != "Job was cancelled") {
                    LogManager.addLog("[Resource] Update error for ${resource.name}: $error")
                    _events.emit(MainUiEvent.ShowToast(messageResId = R.string.failed_to_update_resource))
                }
            }
        }
    }

    fun reloadAllResources() {
        resourceDownloadJob?.cancel()
        resourceDownloadJob = viewModelScope.launch {
            val resources = uiState.resources.filter { it.url != null }
            if (resources.isEmpty()) return@launch
            
            uiState = uiState.copy(downloadingResources = emptyMap())
            
            try {
                coroutineScope {
                    val tasks = resources.map { res ->
                        async {
                            addOrUpdateResourceUseCase(res.name, res.url!!, res.autoUpdateEnabled, res.autoUpdateIntervalHours) { progress ->
                                updateDownloadProgress(res.name, progress)
                            }
                        }
                    }
                    val results = tasks.awaitAll()
                    val successCount = results.count { it.isSuccess }
                    val anyDownloaded = results.any { it.getOrNull() == true }
                    
                    if (successCount == resources.size) {
                        val msg = if (anyDownloaded) R.string.all_resources_reloaded else R.string.all_resources_up_to_date
                        _events.emit(MainUiEvent.ShowToast(messageResId = msg))
                    } else {
                        _events.emit(MainUiEvent.ShowToast(messageResId = R.string.failed_to_reload_resources))
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    LogManager.addLog("[Resource] Batch update error: ${e.message}")
                }
            } finally {
                uiState = uiState.copy(downloadingResources = emptyMap())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        resourceDownloadJob?.cancel()
    }
}
