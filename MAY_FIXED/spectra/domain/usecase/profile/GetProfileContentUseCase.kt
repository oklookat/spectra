package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Profile
import javax.inject.Inject

class GetProfileContentUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(profile: Profile): String? = profileRepository.getProfileContent(profile)
}
