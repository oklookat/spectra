package com.oklookat.spectra.domain.usecase.resource

import com.oklookat.spectra.data.repository.ResourceRepository
import com.oklookat.spectra.model.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetResourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository
) {
    operator fun invoke(): Flow<List<Resource>> = resourceRepository.getResourcesFlow()
}
