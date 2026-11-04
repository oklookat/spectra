package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Profile
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(profile: Profile) = profileRepository.updateProfile(profile)
}
