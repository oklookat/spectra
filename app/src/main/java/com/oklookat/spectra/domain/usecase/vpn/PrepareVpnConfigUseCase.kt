package com.oklookat.spectra.domain.usecase.vpn

import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PrepareVpnConfigUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): String {
        val settings = settingsRepository.settingsFlow.first()
        val profileId = settings.selectedProfileId ?: return ""
        val profile = profileRepository.getProfiles().find { it.id == profileId } ?: return ""
        
        return profileRepository.getProfileContent(profile) ?: ""
    }
}
