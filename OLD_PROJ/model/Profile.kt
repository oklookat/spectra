package com.oklookat.spectra.model

import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String? = null,
    val isRemote: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalMinutes: Int = 15,
    val lastUpdated: Long = 0,
    val fileName: String? = null
)
