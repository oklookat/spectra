package com.oklookat.spectra.domain.usecase.settings

import com.oklookat.spectra.data.repository.SettingsData
import com.oklookat.spectra.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<SettingsData> = settingsRepository.settingsFlow
}
