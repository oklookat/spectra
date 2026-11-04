package com.oklookat.spectra.domain.usecase.settings

import com.oklookat.spectra.data.repository.SettingsRepository
import javax.inject.Inject

class SetIpv6EnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) = settingsRepository.setIpv6Enabled(enabled)
}
