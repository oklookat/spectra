package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import javax.inject.Inject

class DeleteGroupUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(groupId: String) = profileRepository.deleteGroup(groupId)
}
