package com.oklookat.spectra.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.oklookat.spectra.model.CustomDnsConfig
import com.oklookat.spectra.model.CustomRoutingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object PreferencesKeys {
        val ENABLE_IPV6 = booleanPreferencesKey("enable_ipv6")
        val VPN_ADDRESS = stringPreferencesKey("vpn_address")
        val VPN_DNS = stringPreferencesKey("vpn_dns")
        val VPN_ADDRESS_IPV6 = stringPreferencesKey("vpn_address_ipv6")
        val VPN_DNS_IPV6 = stringPreferencesKey("vpn_dns_ipv6")
        val VPN_MTU = intPreferencesKey("vpn_mtu")
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val CUSTOM_DNS_JSON = stringPreferencesKey("custom_dns_json")
        val CUSTOM_ROUTING_JSON = stringPreferencesKey("custom_routing_json")
        val TUN_MODE_ENABLED = booleanPreferencesKey("tun_mode_enabled")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val DNS_LOG = booleanPreferencesKey("dns_log")
        val MASK_ADDRESS = stringPreferencesKey("mask_address")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val P2P_SHARE_MODE = stringPreferencesKey("p2p_share_mode")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        val dnsJson = preferences[PreferencesKeys.CUSTOM_DNS_JSON]
        val dnsConfig = if (!dnsJson.isNullOrBlank()) {
            try { gson.fromJson(dnsJson, CustomDnsConfig::class.java) } catch (_: Exception) { CustomDnsConfig.default() }
        } else {
            CustomDnsConfig.default()
        }

        val routingJson = preferences[PreferencesKeys.CUSTOM_ROUTING_JSON]
        val routingConfig = if (!routingJson.isNullOrBlank()) {
            try { gson.fromJson(routingJson, CustomRoutingConfig::class.java) } catch (_: Exception) { CustomRoutingConfig.default() }
        } else {
            CustomRoutingConfig.default()
        }

        SettingsData(
            isIpv6Enabled = preferences[PreferencesKeys.ENABLE_IPV6] ?: false,
            vpnAddress = preferences[PreferencesKeys.VPN_ADDRESS] ?: "10.0.0.1",
            vpnDns = preferences[PreferencesKeys.VPN_DNS] ?: "10.0.0.2",
            vpnAddressIpv6 = preferences[PreferencesKeys.VPN_ADDRESS_IPV6] ?: "fd00::1",
            vpnDnsIpv6 = preferences[PreferencesKeys.VPN_DNS_IPV6] ?: "fd00::2",
            vpnMtu = preferences[PreferencesKeys.VPN_MTU] ?: 9000,
            selectedProfileId = preferences[PreferencesKeys.SELECTED_PROFILE_ID],
            isTunModeEnabled = preferences[PreferencesKeys.TUN_MODE_ENABLED] ?: true,
            socksPort = preferences[PreferencesKeys.SOCKS_PORT] ?: 10808,
            logLevel = preferences[PreferencesKeys.LOG_LEVEL] ?: "warning",
            dnsLog = preferences[PreferencesKeys.DNS_LOG] ?: false,
            maskAddress = preferences[PreferencesKeys.MASK_ADDRESS] ?: "",
            isPrivacyModeEnabled = preferences[PreferencesKeys.PRIVACY_MODE] ?: false,
            p2pShareMode = preferences[PreferencesKeys.P2P_SHARE_MODE] ?: "direct_qr",
            customDnsConfig = dnsConfig,
            customRoutingConfig = routingConfig
        )
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

    suspend fun setCustomDnsConfig(config: CustomDnsConfig) {
        val json = gson.toJson(config)
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_DNS_JSON] = json }
    }

    suspend fun setCustomRoutingConfig(config: CustomRoutingConfig) {
        val json = gson.toJson(config)
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_ROUTING_JSON] = json }
    }

    suspend fun resetCustomRulesToDefault() {
        context.dataStore.edit {
            it.remove(PreferencesKeys.CUSTOM_DNS_JSON)
            it.remove(PreferencesKeys.CUSTOM_ROUTING_JSON)
        }
    }

    suspend fun setTunModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.TUN_MODE_ENABLED] = enabled }
    }

    suspend fun setSocksPort(port: Int) {
        context.dataStore.edit { it[PreferencesKeys.SOCKS_PORT] = port }
    }

    suspend fun setLogSettings(logLevel: String, dnsLog: Boolean, maskAddress: String) {
        context.dataStore.edit {
            it[PreferencesKeys.LOG_LEVEL] = logLevel
            it[PreferencesKeys.DNS_LOG] = dnsLog
            it[PreferencesKeys.MASK_ADDRESS] = maskAddress
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.PRIVACY_MODE] = enabled
        }
    }

    suspend fun setP2PShareMode(mode: String) {
        context.dataStore.edit {
            it[PreferencesKeys.P2P_SHARE_MODE] = mode
        }
    }

    suspend fun getSettingsDataOnce(): SettingsData = settingsFlow.first()
}

data class SettingsData(
    val isIpv6Enabled: Boolean,
    val vpnAddress: String,
    val vpnDns: String,
    val vpnAddressIpv6: String,
    val vpnDnsIpv6: String,
    val vpnMtu: Int,
    val selectedProfileId: String?,
    val isTunModeEnabled: Boolean,
    val socksPort: Int,
    val logLevel: String = "warning",
    val dnsLog: Boolean = false,
    val maskAddress: String = "",
    val isPrivacyModeEnabled: Boolean = false,
    val p2pShareMode: String = "direct_qr",
    val customDnsConfig: CustomDnsConfig = CustomDnsConfig.default(),
    val customRoutingConfig: CustomRoutingConfig = CustomRoutingConfig.default()
)
