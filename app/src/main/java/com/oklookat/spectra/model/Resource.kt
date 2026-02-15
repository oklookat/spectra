package com.oklookat.spectra.model

data class Resource(
    val name: String,
    val size: Long = 0,
    val url: String? = null,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalHours: Int = 1,
    val lastUpdated: Long = 0,
    val isDefault: Boolean = false,
    val etag: String? = null
)
