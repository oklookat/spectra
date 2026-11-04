package com.oklookat.spectra.model

data class ProfileTransferItem(
    val profile: Profile,
    val content: String? = null
)

data class P2PPayload(
    val deviceName: String = "",
    val token: String = "",
    
    // For single profile
    val profileName: String = "",
    val profileContent: String? = null,
    val profileUrl: String? = null,
    val isRemote: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalMinutes: Int = 15,

    // For group
    val groupName: String? = null,
    val groupProfiles: List<ProfileTransferItem>? = null
)
