package com.oklookat.spectra.domain.usecase.resource

import com.oklookat.spectra.data.repository.ResourceRepository
import javax.inject.Inject

class DeleteResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository
) {
    suspend operator fun invoke(name: String) = resourceRepository.deleteResource(name)
}
