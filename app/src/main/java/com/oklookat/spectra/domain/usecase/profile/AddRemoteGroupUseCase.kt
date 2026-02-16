package com.oklookat.spectra.domain.usecase.profile

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.model.Group
import javax.inject.Inject

class AddRemoteGroupUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int
    ): Result<Group> = profileRepository.addRemoteGroup(name, url, autoUpdate, interval)
}
