package com.oklookat.spectra.model

import com.google.gson.annotations.SerializedName

data class AppUpdate(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("apkUrl") val apkUrl: String? = null,
    @SerializedName("apkUrls") val apkUrls: Map<String, String>? = null,
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("minVersionCode") val minVersionCode: Int? = null
)