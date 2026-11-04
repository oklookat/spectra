package com.oklookat.spectra.model

data class P2PPayload(
    val deviceName: String,
    val name: String,
    val content: String? = null,
    val url: String? = null,
    val isRemote: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateIntervalMinutes: Int = 15,
    val token: String
)
