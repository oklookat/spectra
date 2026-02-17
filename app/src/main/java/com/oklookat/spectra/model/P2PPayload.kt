package com.oklookat.spectra.model

data class P2PPayload(
    val deviceName: String = "",
    val token: String = "",
    
    // For single profile
    val profile: Profile? = null,
    val profileContent: String? = null,
    
    // For group
    val group: Group? = null,
    val groupProfiles: List<Pair<Profile, String?>>? = null // Profile and its content
)
