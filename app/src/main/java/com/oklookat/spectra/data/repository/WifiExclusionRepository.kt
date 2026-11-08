package com.oklookat.spectra.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.oklookat.spectra.util.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wifiExclusionDataStore: DataStore<Preferences> by preferencesDataStore(name = "wifi_exclusion_settings")

@Singleton
class WifiExclusionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        const val DEFAULT_PRIMARY_DNS = "1.1.1.1"
        const val DEFAULT_SECONDARY_DNS = "8.8.8.8"
    }

    private object PreferencesKeys {
        val EXCLUDED_SSIDS_JSON = stringPreferencesKey("excluded_ssids_json")
        val WIFI_EXCLUSION_ENABLED = booleanPreferencesKey("wifi_exclusion_enabled")
        val BYPASS_DNS_PRIMARY = stringPreferencesKey("bypass_dns_primary")
        val BYPASS_DNS_SECONDARY = stringPreferencesKey("bypass_dns_secondary")
        val CUSTOM_BYPASS_CONFIG_JSON = stringPreferencesKey("custom_bypass_config_json")
    }

    val isWifiExclusionEnabledFlow: Flow<Boolean> = context.wifiExclusionDataStore.data.map { prefs ->
        prefs[PreferencesKeys.WIFI_EXCLUSION_ENABLED] ?: false
    }

    val bypassDnsPrimaryFlow: Flow<String> = context.wifiExclusionDataStore.data.map { prefs ->
        val saved = prefs[PreferencesKeys.BYPASS_DNS_PRIMARY] ?: DEFAULT_PRIMARY_DNS
        if (saved == "localhost") DEFAULT_PRIMARY_DNS else saved
    }

    val bypassDnsSecondaryFlow: Flow<String> = context.wifiExclusionDataStore.data.map { prefs ->
        prefs[PreferencesKeys.BYPASS_DNS_SECONDARY] ?: DEFAULT_SECONDARY_DNS
    }

    val customBypassConfigJsonFlow: Flow<String> = context.wifiExclusionDataStore.data.map { prefs ->
        prefs[PreferencesKeys.CUSTOM_BYPASS_CONFIG_JSON] ?: ""
    }

    val excludedSsidsFlow: Flow<Set<String>> = context.wifiExclusionDataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.EXCLUDED_SSIDS_JSON] ?: ""
        if (json.isBlank()) emptySet()
        else {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson<Set<String>>(json, type) ?: emptySet()
            } catch (e: Exception) {
                LogManager.addLog("[WifiExclusion] Error parsing SSIDs: ${e.message}")
                emptySet()
            }
        }
    }

    fun buildDefaultWifiBypassConfig(
        primaryDns: String = DEFAULT_PRIMARY_DNS,
        secondaryDns: String = DEFAULT_SECONDARY_DNS,
        mtu: Int = 9000
    ): String {
        val primary = primaryDns.ifBlank { DEFAULT_PRIMARY_DNS }
        val secondary = secondaryDns.ifBlank { DEFAULT_SECONDARY_DNS }
        val configMap = mapOf(
            "log" to mapOf("loglevel" to "warning"),
            "dns" to mapOf(
                "queryStrategy" to "UseIPv4",
                "servers" to listOf(
                    mapOf("tag" to "dns-direct", "address" to primary),
                    mapOf("tag" to "dns-direct", "address" to secondary)
                )
            ),
            "routing" to mapOf(
                "domainStrategy" to "AsIs",
                "rules" to listOf(
                    mapOf(
                        "type" to "field",
                        "inboundTag" to listOf("tun-in"),
                        "port" to "53",
                        "outboundTag" to "dns-out"
                    ),
                    mapOf(
                        "type" to "field",
                        "inboundTag" to listOf("dns-direct"),
                        "outboundTag" to "direct-out"
                    )
                )
            ),
            "inbounds" to listOf(
                mapOf(
                    "protocol" to "tun",
                    "tag" to "tun-in",
                    "settings" to mapOf(
                        "name" to "tun0",
                        "mtu" to mtu
                    ),
                    "sniffing" to mapOf(
                        "enabled" to true,
                        "destOverride" to listOf("http", "tls", "quic", "fakedns")
                    )
                )
            ),
            "outbounds" to listOf(
                mapOf(
                    "tag" to "direct-out",
                    "protocol" to "freedom",
                    "streamSettings" to mapOf(
                        "sockopt" to mapOf(
                            "domainStrategy" to "UseIPv4"
                        )
                    )
                ),
                mapOf(
                    "tag" to "dns-out",
                    "protocol" to "dns",
                    "settings" to emptyMap<String, Any>()
                ),
                mapOf(
                    "tag" to "block-out",
                    "protocol" to "blackhole",
                    "settings" to mapOf(
                        "response" to mapOf("type" to "http")
                    )
                )
            )
        )
        return gson.toJson(configMap)
    }

    suspend fun getActiveBypassConfigJson(): String {
        val customJson = customBypassConfigJsonFlow.first()
        if (customJson.isNotBlank()) return customJson

        val primary = bypassDnsPrimaryFlow.first()
        val secondary = bypassDnsSecondaryFlow.first()
        return buildDefaultWifiBypassConfig(primary, secondary)
    }

    suspend fun saveBypassDns(primary: String, secondary: String) {
        context.wifiExclusionDataStore.edit { prefs ->
            prefs[PreferencesKeys.BYPASS_DNS_PRIMARY] = primary.trim()
            prefs[PreferencesKeys.BYPASS_DNS_SECONDARY] = secondary.trim()
        }
        LogManager.addLog("[WifiExclusion] Updated Wi-Fi bypass DNS: Primary=$primary, Secondary=$secondary")
    }

    suspend fun saveCustomBypassConfigJson(json: String) {
        context.wifiExclusionDataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_BYPASS_CONFIG_JSON] = json.trim()
        }
        LogManager.addLog("[WifiExclusion] Updated custom Wi-Fi bypass config JSON")
    }

    suspend fun resetBypassConfigToDefault() {
        context.wifiExclusionDataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.CUSTOM_BYPASS_CONFIG_JSON)
            prefs[PreferencesKeys.BYPASS_DNS_PRIMARY] = DEFAULT_PRIMARY_DNS
            prefs[PreferencesKeys.BYPASS_DNS_SECONDARY] = DEFAULT_SECONDARY_DNS
        }
        LogManager.addLog("[WifiExclusion] Reset Wi-Fi bypass config to default")
    }

    suspend fun setWifiExclusionEnabled(enabled: Boolean) {
        context.wifiExclusionDataStore.edit { prefs ->
            prefs[PreferencesKeys.WIFI_EXCLUSION_ENABLED] = enabled
        }
    }

    suspend fun addWifiSsid(ssid: String) {
        val cleanSsid = ssid.trim().removePrefix("\"").removeSuffix("\"")
        if (cleanSsid.isBlank() || cleanSsid == "<unknown ssid>") return

        context.wifiExclusionDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.EXCLUDED_SSIDS_JSON] ?: ""
            val type = object : TypeToken<Set<String>>() {}.type
            val current = if (json.isBlank()) mutableSetOf() else gson.fromJson<Set<String>>(json, type).toMutableSet()
            current.add(cleanSsid)
            prefs[PreferencesKeys.EXCLUDED_SSIDS_JSON] = gson.toJson(current)
        }
        LogManager.addLog("[WifiExclusion] Added SSID to exclusions: $cleanSsid")
    }

    suspend fun removeWifiSsid(ssid: String) {
        val cleanSsid = ssid.trim().removePrefix("\"").removeSuffix("\"")
        context.wifiExclusionDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.EXCLUDED_SSIDS_JSON] ?: ""
            val type = object : TypeToken<Set<String>>() {}.type
            val current = if (json.isBlank()) mutableSetOf() else gson.fromJson<Set<String>>(json, type).toMutableSet()
            current.remove(cleanSsid)
            prefs[PreferencesKeys.EXCLUDED_SSIDS_JSON] = gson.toJson(current)
        }
        LogManager.addLog("[WifiExclusion] Removed SSID from exclusions: $cleanSsid")
    }

    fun getCurrentWifiSsid(caps: NetworkCapabilities? = null): String? {
        if (!hasLocationPermission()) return null

        var ssid: String? = null

        // 1. Check provided NetworkCapabilities transportInfo if available (from NetworkCallback with FLAG_INCLUDE_LOCATION_INFO)
        if (caps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = caps.transportInfo as? WifiInfo
            if (wifiInfo != null && !wifiInfo.ssid.isNullOrBlank() && wifiInfo.ssid != "<unknown ssid>") {
                ssid = wifiInfo.ssid
            }
        }

        // 2. Check WifiManager connectionInfo directly (works even when VPN is active)
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connectionInfo = wifiManager?.connectionInfo
            if (connectionInfo != null && !connectionInfo.ssid.isNullOrBlank() && connectionInfo.ssid != "<unknown ssid>") {
                ssid = connectionInfo.ssid
            }
        }

        // 3. Fallback: Iterate over physical underlying networks
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                for (network in connectivityManager.allNetworks) {
                    val netCaps = connectivityManager.getNetworkCapabilities(network)
                    if (netCaps != null && netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val wifiInfo = netCaps.transportInfo as? WifiInfo
                            if (wifiInfo != null && !wifiInfo.ssid.isNullOrBlank() && wifiInfo.ssid != "<unknown ssid>") {
                                ssid = wifiInfo.ssid
                                break
                            }
                        }
                    }
                }
            }
        }

        val clean = ssid?.trim()?.removePrefix("\"")?.removeSuffix("\"")
        return if (clean.isNullOrBlank() || clean == "<unknown ssid>") null else clean
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return (fine || coarse) && background
    }
}
