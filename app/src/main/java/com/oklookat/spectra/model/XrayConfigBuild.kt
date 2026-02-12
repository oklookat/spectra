package com.oklookat.spectra.model

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

object XrayConfigBuild {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun buildFullConfig(proxyOutbound: Outbound<*>, mtu: Int = 9000): String {
        val config = XrayFullConfig(
            log = Log(loglevel = "warning"),
            dns = Dns(
                queryStrategy = "UseIPv4",
                servers = listOf(
                    DnsServer(address = "https://1.1.1.1/dns-query"),
                    DnsServer(address = "https://8.8.8.8/dns-query")
                )
            ),
            routing = Routing(
                domainStrategy = "AsIs",
                rules = listOf(
                    RoutingRule(
                        type = "field",
                        inboundTag = listOf("tun-in"),
                        port = "53",
                        outboundTag = "dns-out"
                    ),
                    RoutingRule(
                        type = "field",
                        ip = listOf("geoip:private"),
                        outboundTag = "direct-out"
                    ),
                    RoutingRule(
                        type = "field",
                        protocol = listOf("bittorrent"),
                        outboundTag = "direct-out"
                    )
                )
            ),
            inbounds = listOf(
                Inbound(
                    protocol = "tun",
                    tag = "tun-in",
                    settings = TunSettings(
                        mtu = mtu,
                        stack = "gvisor"
                    ),
                    sniffing = Sniffing(
                        enabled = true,
                        destOverride = listOf("http", "tls")
                    )
                )
            ),
            outbounds = listOf(
                proxyOutbound.copy(tag = "proxy-out"),
                Outbound(
                    tag = "direct-out",
                    protocol = "freedom",
                    settings = FreedomSettings(targetStrategy = "UseIPv4")
                ),
                Outbound(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = DnsOutboundSettings(nonIPQuery = "skip")
                ),
                Outbound(
                    tag = "block-out",
                    protocol = "blackhole",
                    settings = BlackholeSettings(response = BlackholeResponse(type = "http"))
                )
            )
        )
        return gson.toJson(config)
    }

    fun fromShareLink(link: ShareLink): Outbound<*>? {
        return when (link) {
            is ShareLink.Vless -> vlessOutbound(link)
            is ShareLink.Vmess -> vmessOutbound(link)
            is ShareLink.VmessLegacy -> vmessLegacyOutbound(link)
            is ShareLink.Trojan -> trojanOutbound(link)
            is ShareLink.Shadowsocks -> shadowsocksOutbound(link)
        }
    }

    private fun vlessOutbound(link: ShareLink.Vless): Outbound<VlessSettings> {
        return Outbound(
            protocol = "vless",
            settings = VlessSettings(
                address = link.host,
                port = link.port,
                id = link.uuid.toString(),
                encryption = link.encryption,
                flow = link.flow
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni, link.params)
        )
    }

    private fun vmessOutbound(link: ShareLink.Vmess): Outbound<VmessSettings> {
        return Outbound(
            protocol = "vmess",
            settings = VmessSettings(
                address = link.host,
                port = link.port,
                id = link.uuid.toString(),
                security = link.encryption
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni, link.params)
        )
    }

    private fun vmessLegacyOutbound(link: ShareLink.VmessLegacy): Outbound<VmessSettings> {
        val params = mutableMapOf<String, String>()
        link.path?.let { params["path"] = it }
        link.type?.let { params["type"] = it }
        return Outbound(
            protocol = "vmess",
            settings = VmessSettings(
                address = link.host,
                port = link.port,
                id = link.id.toString(),
                security = link.encryption
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni ?: link.hostName ?: "", params)
        )
    }

    private fun trojanOutbound(link: ShareLink.Trojan): Outbound<TrojanSettings> {
        return Outbound(
            protocol = "trojan",
            settings = TrojanSettings(
                address = link.host,
                port = link.port,
                password = link.password
            ),
            streamSettings = buildStreamSettings(link.params["security"] ?: "tls", link.params["type"] ?: "tcp", link.sni, link.params)
        )
    }

    private fun shadowsocksOutbound(link: ShareLink.Shadowsocks): Outbound<ShadowsocksSettings> {
        return Outbound(
            protocol = "shadowsocks",
            settings = ShadowsocksSettings(
                address = link.host,
                port = link.port,
                method = link.method,
                password = link.password
            )
        )
    }

    private fun buildStreamSettings(security: String, transport: String, sni: String, params: Map<String, String>): StreamSettings {
        val realitySettings = if (security == "reality") {
            RealitySettings(
                fingerprint = params["fp"] ?: "edge",
                serverName = sni,
                password = params["pbk"] ?: "",
                shortId = params["sid"] ?: ""
            )
        } else null

        val tlsSettings = if (security == "tls" || security == "xtls") {
            TlsSettings(serverName = sni)
        } else null

        val wsSettings = if (transport == "ws") {
            WsSettings(path = params["path"] ?: "/", headers = mapOf("Host" to (params["host"] ?: sni)))
        } else null

        return StreamSettings(
            network = if (transport == "tcp") "raw" else transport,
            security = if (security == "none") "" else security,
            realitySettings = realitySettings,
            tlsSettings = tlsSettings,
            wsSettings = wsSettings,
            sockopt = Sockopt(tcpFastOpen = true, tcpCongestion = "bbr", tcpNoDelay = true)
        )
    }
}

// region Data Classes

data class XrayFullConfig(
    val log: Log? = Log(),
    val dns: Dns? = null,
    val routing: Routing? = null,
    val inbounds: List<Inbound>,
    val outbounds: List<Outbound<*>>
)

data class Log(
    val loglevel: String = "warning"
)

data class Dns(
    val queryStrategy: String = "UseIPv4",
    val servers: List<DnsServer>
)

data class DnsServer(
    val address: String
)

data class Routing(
    val domainStrategy: String = "AsIs",
    val rules: List<RoutingRule>
)

data class RoutingRule(
    val type: String? = "field",
    @SerializedName("inboundTag")
    val inboundTag: List<String>? = null,
    val port: String? = null,
    val domain: List<String>? = null,
    val ip: List<String>? = null,
    val protocol: List<String>? = null,
    val outboundTag: String
)

data class Inbound(
    val protocol: String,
    val tag: String? = null,
    val settings: Any,
    val sniffing: Sniffing? = null
)

data class TunSettings(
    val mtu: Int = 9000,
    val stack: String = "gvisor"
)

data class Sniffing(
    val enabled: Boolean = true,
    @SerializedName("destOverride")
    val destOverride: List<String> = listOf("http", "tls")
)

data class Outbound<T>(
    val protocol: String,
    val settings: T,
    val tag: String? = null,
    val streamSettings: StreamSettings? = null
)

data class FreedomSettings(
    val targetStrategy: String = "UseIPv4"
)

data class DnsOutboundSettings(
    val nonIPQuery: String = "skip"
)

data class BlackholeSettings(
    val response: BlackholeResponse
)

data class BlackholeResponse(
    val type: String = "http"
)

data class VlessSettings(
    val address: String,
    val port: Int,
    val id: String,
    val encryption: String = "none",
    val flow: String = ""
)

data class VmessSettings(
    val address: String,
    val port: Int,
    val id: String,
    val security: String = "auto"
)

data class TrojanSettings(
    val address: String,
    val port: Int,
    val password: String
)

data class ShadowsocksSettings(
    val address: String,
    val port: Int,
    val method: String,
    val password: String
)

data class StreamSettings(
    val network: String = "tcp",
    val security: String = "",
    val realitySettings: RealitySettings? = null,
    val tlsSettings: TlsSettings? = null,
    val wsSettings: WsSettings? = null,
    val sockopt: Sockopt? = null
)

data class RealitySettings(
    val fingerprint: String,
    val serverName: String,
    val password: String,
    val shortId: String
)

data class TlsSettings(
    val serverName: String
)

data class WsSettings(
    val path: String,
    val headers: Map<String, String>
)

data class Sockopt(
    val tcpFastOpen: Boolean = true,
    val tcpCongestion: String = "bbr",
    val tcpNoDelay: Boolean = true
)
// endregion
