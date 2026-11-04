package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Group
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGroupsUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(): Flow<List<Group>> = profileRepository.getGroupsFlow()
}
