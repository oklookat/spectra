package com.oklookat.spectra.ui.viewmodel

import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile

data class ProfilesUiState(
    val groups: List<Group> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    
    // Deep Link / Import state
    val deepLinkProfile: PendingProfile? = null,
    val pendingProfileToReplace: PendingProfile? = null,
    val deepLinkGroup: PendingGroup? = null,
    val pendingGroupToReplace: PendingGroup? = null,
    val showDeepLinkVerifyDialog: Boolean = false,
    val isDeepLinkVerified: Boolean = true
)
