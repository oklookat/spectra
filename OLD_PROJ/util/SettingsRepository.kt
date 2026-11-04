package com.oklookat.spectra.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.oklookat.spectra.BuildConfig

class SettingsRepository(context: Context) {
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    var useDebugConfig: Boolean
        get() {
            // Force false in non-debug builds for safety
            if (!BuildConfig.DEBUG) return false
            return settingsPrefs.getBoolean("use_debug_config", true)
        }
        set(value) = settingsPrefs.edit { putBoolean("use_debug_config", value) }

    var isIpv6Enabled: Boolean
        get() = settingsPrefs.getBoolean("enable_ipv6", false)
        set(value) = settingsPrefs.edit { putBoolean("enable_ipv6", value) }

    var vpnAddress: String
        get() = settingsPrefs.getString("vpn_address", "10.0.0.1") ?: "10.0.0.1"
        set(value) = settingsPrefs.edit { putString("vpn_address", value) }

    var vpnDns: String
        get() = settingsPrefs.getString("vpn_dns", "10.0.0.2") ?: "10.0.0.2"
        set(value) = settingsPrefs.edit { putString("vpn_dns", value) }

    var vpnAddressIpv6: String
        get() = settingsPrefs.getString("vpn_address_ipv6", "fd00::1") ?: "fd00::1"
        set(value) = settingsPrefs.edit { putString("vpn_address_ipv6", value) }

    var vpnDnsIpv6: String
        get() = settingsPrefs.getString("vpn_dns_ipv6", "fd00::2") ?: "fd00::2"
        set(value) = settingsPrefs.edit { putString("vpn_dns_ipv6", value) }

    var vpnMtu: Int
        get() = settingsPrefs.getInt("vpn_mtu", 9000)
        set(value) = settingsPrefs.edit { putInt("vpn_mtu", value) }
}
