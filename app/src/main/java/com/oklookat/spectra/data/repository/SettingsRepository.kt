package com.oklookat.spectra.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.oklookat.spectra.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val USE_DEBUG_CONFIG = booleanPreferencesKey("use_debug_config")
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val VPN_ADDRESS = stringPreferencesKey("vpn_address")
        val VPN_DNS = stringPreferencesKey("vpn_dns")
        val VPN_ADDRESS_IPV6 = stringPreferencesKey("vpn_address_ipv6")
        val VPN_DNS_IPV6 = stringPreferencesKey("vpn_dns_ipv6")
        val VPN_MTU = intPreferencesKey("vpn_mtu")
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        SettingsData(
            useDebugConfig = if (BuildConfig.DEBUG) preferences[PreferencesKeys.USE_DEBUG_CONFIG] ?: true else false,
            isIpv6Enabled = preferences[PreferencesKeys.ENABLE_IPV6] ?: false,
            vpnAddress = preferences[PreferencesKeys.VPN_ADDRESS] ?: "10.0.0.1",
            vpnDns = preferences[PreferencesKeys.VPN_DNS] ?: "10.0.0.2",
            vpnAddressIpv6 = preferences[PreferencesKeys.VPN_ADDRESS_IPV6] ?: "fd00::1",
            vpnDnsIpv6 = preferences[PreferencesKeys.VPN_DNS_IPV6] ?: "fd00::2",
            vpnMtu = preferences[PreferencesKeys.VPN_MTU] ?: 9000,
            selectedProfileId = preferences[PreferencesKeys.SELECTED_PROFILE_ID]
        )
    }

    suspend fun setUseDebugConfig(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_DEBUG_CONFIG] = enabled }
    }

    suspend fun setIpv6Enabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ENABLE_IPV6] = enabled }
    }

    suspend fun setVpnSettings(address: String, dns: String, address6: String, dns6: String, mtu: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.VPN_ADDRESS] = address
            it[PreferencesKeys.VPN_DNS] = dns
            it[PreferencesKeys.VPN_ADDRESS_IPV6] = address6
            it[PreferencesKeys.VPN_DNS_IPV6] = dns6
            it[PreferencesKeys.VPN_MTU] = mtu
        }
    }

    suspend fun setSelectedProfileId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(PreferencesKeys.SELECTED_PROFILE_ID)
            else it[PreferencesKeys.SELECTED_PROFILE_ID] = id
        }
    }
}

data class SettingsData(
    val useDebugConfig: Boolean,
    val isIpv6Enabled: Boolean,
    val vpnAddress: String,
    val vpnDns: String,
    val vpnAddressIpv6: String,
    val vpnDnsIpv6: String,
    val vpnMtu: Int,
    val selectedProfileId: String?
)
