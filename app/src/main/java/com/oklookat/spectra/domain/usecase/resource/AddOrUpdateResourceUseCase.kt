package com.oklookat.spectra.domain.usecase.resource

import com.oklookat.spectra.data.repository.ResourceRepository
import javax.inject.Inject

class AddOrUpdateResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository
) {
    suspend operator fun invoke(
        name: String,
        url: String?,
        autoUpdate: Boolean,
        interval: Int,
        fileBytes: ByteArray? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Boolean> = resourceRepository.addOrUpdateResource(name, url, autoUpdate, interval, fileBytes, onProgress)
}
