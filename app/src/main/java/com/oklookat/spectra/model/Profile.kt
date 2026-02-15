package com.oklookat.spectra.model

import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = Group.DEFAULT_GROUP_ID,
    val name: String,
    val url: String? = null, // Original URL if it was a single remote profile, or content if it's a proxy link
    val isRemote: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalMinutes: Int = 15,
    val lastUpdated: Long = 0,
    val fileName: String? = null,
    val isImported: Boolean = false // If true, it belongs to a remote group and cannot be edited
)
