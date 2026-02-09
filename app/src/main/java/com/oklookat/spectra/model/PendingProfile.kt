package com.oklookat.spectra.model

data class PendingProfile(
    val name: String,
    val url: String,
    val autoUpdate: Boolean,
    val interval: Int
)
