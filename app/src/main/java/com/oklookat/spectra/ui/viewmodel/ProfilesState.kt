package com.oklookat.spectra.ui.viewmodel

import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile

enum class ProfileSort {
    AS_IS,
    BY_NAME_ASC,
    BY_NAME_DESC,
    BY_PING_ASC,
    BY_PING_DESC
}

data class ProfilesUiState(
    val groups: List<Group> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val sortOrder: ProfileSort = ProfileSort.AS_IS,
    
    // Deep Link / Import state
    val deepLinkProfile: PendingProfile? = null,
    val pendingProfileToReplace: PendingProfile? = null,
    val deepLinkGroup: PendingGroup? = null,
    val pendingGroupToReplace: PendingGroup? = null,
    val showDeepLinkVerifyDialog: Boolean = false,
    val isDeepLinkVerified: Boolean = true
)
