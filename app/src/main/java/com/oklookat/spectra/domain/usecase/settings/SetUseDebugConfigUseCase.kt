package com.oklookat.spectra.domain.usecase.settings

import com.oklookat.spectra.data.repository.SettingsRepository
import javax.inject.Inject

class SetUseDebugConfigUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) = settingsRepository.setUseDebugConfig(enabled)
}
