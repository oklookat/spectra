package com.oklookat.spectra.domain.usecase.settings

import android.app.Application
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import com.oklookat.spectra.util.TvUtils
import javax.inject.Inject

class CheckDeepLinkStatusUseCase @Inject constructor(
    private val application: Application
) {
    operator fun invoke(): Boolean {
        if (TvUtils.isTv(application)) return true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = application.getSystemService(DomainVerificationManager::class.java) ?: return true
            val userState = manager.getDomainVerificationUserState(application.packageName) ?: return true
            return userState.hostToStateMap["spectra.local"] == DomainVerificationUserState.DOMAIN_STATE_SELECTED
        }
        return true
    }
}
