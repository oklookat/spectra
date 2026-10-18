package com.oklookat.spectra.model

import java.util.UUID

/**
 * Configuration model for custom DNS settings in Xray.
 */
data class CustomDnsConfig(
    val queryStrategy: String = "UseIPv4",
    val servers: List<DnsServerConfig> = defaultServers()
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
data class DnsServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val address: String,
    val enabled: Boolean = true
)

/**
 * Configuration model for custom routing rules in Xray.
 */
data class CustomRoutingConfig(
    val domainStrategy: String = "AsIs",
    val rules: List<RoutingRuleConfig> = defaultRules()
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
data class RoutingRuleConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val outboundTag: String = "proxy-out", // "proxy-out", "direct-out", "block-out", "dns-out"
    val domains: List<String> = emptyList(),
    val ip: List<String> = emptyList(),
    val port: String = "",
    val protocols: List<String> = emptyList(),
    val inboundTag: List<String> = emptyList(),
    val enabled: Boolean = true
)
