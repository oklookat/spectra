package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import javax.inject.Inject

class SaveLocalProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(
        name: String, 
        content: String, 
        groupId: String = Group.DEFAULT_GROUP_ID
    ): Profile = profileRepository.saveLocalProfile(name, content, groupId)
}
