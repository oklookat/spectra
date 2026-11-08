package com.oklookat.spectra.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Configuration model for custom DNS settings in Xray.
 */
@Keep
data class CustomDnsConfig(
    @SerializedName("queryStrategy") val queryStrategy: String = "UseIPv4",
    @SerializedName("servers") val servers: List<DnsServerConfig> = defaultServers()
) {
    companion object {
        fun defaultServers(): List<DnsServerConfig> = listOf(
            DnsServerConfig(id = "1", address = "https://1.1.1.1/dns-query", enabled = true),
            DnsServerConfig(id = "2", address = "https://8.8.8.8/dns-query", enabled = true)
        )

        fun default(): CustomDnsConfig = CustomDnsConfig()
    }
}

/**
 * Individual DNS server configuration.
 */
@Keep
data class DnsServerConfig(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("address") val address: String,
    @SerializedName("enabled") val enabled: Boolean = true
)

/**
 * Configuration model for custom routing rules in Xray.
 */
@Keep
data class CustomRoutingConfig(
    @SerializedName("domainStrategy") val domainStrategy: String = "AsIs",
    @SerializedName("rules") val rules: List<RoutingRuleConfig> = defaultRules()
) {
    companion object {
        fun defaultRules(): List<RoutingRuleConfig> = listOf(
            RoutingRuleConfig(
                id = "1",
                name = "DNS Queries",
                outboundTag = "dns-out",
                inboundTag = listOf("tun-in"),
                port = "53",
                enabled = true
            ),
            RoutingRuleConfig(
                id = "2",
                name = "Private IP Direct",
                outboundTag = "direct-out",
                ip = listOf("geoip:private"),
                enabled = true
            ),
            RoutingRuleConfig(
                id = "3",
                name = "Category RU Direct",
                outboundTag = "direct-out",
                domains = listOf("geosite:category-ru"),
                enabled = true
            ),
            RoutingRuleConfig(
                id = "4",
                name = "BitTorrent Direct",
                outboundTag = "direct-out",
                protocols = listOf("bittorrent"),
                enabled = true
            )
        )

        fun default(): CustomRoutingConfig = CustomRoutingConfig()
    }
}

/**
 * Individual routing rule configuration.
 */
@Keep
data class RoutingRuleConfig(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String = "",
    @SerializedName("outboundTag") val outboundTag: String = "proxy-out", // "proxy-out", "direct-out", "block-out", "dns-out"
    @SerializedName("domains") val domains: List<String> = emptyList(),
    @SerializedName("ip") val ip: List<String> = emptyList(),
    @SerializedName("port") val port: String = "",
    @SerializedName("protocols") val protocols: List<String> = emptyList(),
    @SerializedName("inboundTag") val inboundTag: List<String> = emptyList(),
    @SerializedName("enabled") val enabled: Boolean = true
)
