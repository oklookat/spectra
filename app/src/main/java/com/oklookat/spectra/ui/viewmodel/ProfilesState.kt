package com.oklookat.spectra.ui.viewmodel

import androidx.annotation.StringRes
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Profile

enum class ProfileSort(@StringRes val labelResId: Int) {
    AS_IS(R.string.sort_as_is),
    BY_NAME_ASC(R.string.sort_by_name_asc),
    BY_NAME_DESC(R.string.sort_by_name_desc),
    BY_PING_ASC(R.string.sort_by_ping_asc),
    BY_PING_DESC(R.string.sort_by_ping_desc)
}

data class ProfilesUiState(
    val groups: List<Group> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val sortOrder: ProfileSort = ProfileSort.AS_IS,
    val isRefreshing: Boolean = false,
    
    // Deep Link / Import state
    val deepLinkProfile: PendingProfile? = null,
    val pendingProfileToReplace: PendingProfile? = null,
    val deepLinkGroup: PendingGroup? = null,
    val pendingGroupToReplace: PendingGroup? = null,
    val showDeepLinkVerifyDialog: Boolean = false,
    val isDeepLinkVerified: Boolean = true
)
