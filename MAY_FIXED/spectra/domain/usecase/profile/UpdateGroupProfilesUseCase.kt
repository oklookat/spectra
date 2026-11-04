package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Group
import javax.inject.Inject

class UpdateGroupProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(group: Group) = profileRepository.updateGroupProfiles(group)
}
