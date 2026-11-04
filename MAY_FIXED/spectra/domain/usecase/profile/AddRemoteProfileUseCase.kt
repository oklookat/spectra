package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import javax.inject.Inject

class AddRemoteProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int,
        groupId: String = Group.DEFAULT_GROUP_ID
    ): Result<Profile> = profileRepository.addRemoteProfile(name, url, autoUpdate, interval, groupId)
}
