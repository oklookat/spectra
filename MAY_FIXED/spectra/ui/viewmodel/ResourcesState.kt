package com.oklookat.spectra.ui.viewmodel

import com.oklookat.spectra.model.Resource

data class ResourcesUiState(
    val resources: List<Resource> = emptyList(),
    val downloadingResources: Map<String, Float> = emptyMap() // name -> progress (0f..1f)
) {
    val isDownloadingResource: Boolean get() = downloadingResources.isNotEmpty()
}
