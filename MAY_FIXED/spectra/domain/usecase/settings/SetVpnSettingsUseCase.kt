package com.oklookat.spectra.domain.usecase.settings

import com.oklookat.spectra.data.repository.SettingsRepository
import javax.inject.Inject

class SetVpnSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(address: String, dns: String, address6: String, dns6: String, mtu: Int) = 
        settingsRepository.setVpnSettings(address, dns, address6, dns6, mtu)
}
