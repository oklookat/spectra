package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import javax.inject.Inject

class DeleteProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(ids: Set<String>) = profileRepository.deleteProfiles(ids)
}
