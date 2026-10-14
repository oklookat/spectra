package com.oklookat.spectra.model

import com.google.gson.annotations.SerializedName

enum class AppRoutingMode {
    BYPASS,     // Exclude selected apps from VPN
    ALLOW_ONLY  // Include only selected apps in VPN
}

data class AppRoutingPreset(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val mode: AppRoutingMode = AppRoutingMode.BYPASS,
    val packages: Set<String> = emptySet(),
    val remoteUrl: String? = null
) {
    companion object {
        const val PRESET_ALL_VPN = "preset_all_vpn"
        const val PRESET_EXCLUDE_RU = "preset_exclude_ru"

        fun defaultPresets(): List<AppRoutingPreset> = listOf(
            AppRoutingPreset(
                id = PRESET_ALL_VPN,
                name = "Все через VPN",
                isBuiltIn = true,
                mode = AppRoutingMode.BYPASS,
                packages = emptySet()
            ),
            AppRoutingPreset(
                id = PRESET_EXCLUDE_RU,
                name = "Кроме российских",
                isBuiltIn = true,
                mode = AppRoutingMode.BYPASS,
                packages = emptySet(),
                remoteUrl = "https://github.com/oklookat/v2ray-rules/raw/refs/heads/main/sing-box/package/category-ru.json"
            )
        )
    }
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)

internal data class PackageCategoryRule(
    val version: Int? = null,
    val rules: List<PackageRuleItem>? = null
)

internal data class PackageRuleItem(
    @SerializedName("package_name_regex")
    val packageNameRegex: List<String>? = null
)
