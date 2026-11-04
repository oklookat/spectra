package com.oklookat.spectra.domain.usecase.settings

import com.oklookat.spectra.data.repository.SettingsRepository
import javax.inject.Inject

class SetSelectedProfileIdUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(id: String?) = settingsRepository.setSelectedProfileId(id)
}
