package com.oklookat.spectra.model

data class PendingGroup(
    val name: String,
    val url: String,
    val autoUpdate: Boolean = false,
    val interval: Int = 15
)
