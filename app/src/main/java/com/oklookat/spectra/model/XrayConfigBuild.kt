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
                        name = "tun0",
                        mtu = mtu
                    ),
                    sniffing = Sniffing(
                        enabled = true,
                        destOverride = listOf("http", "tls", "quic")
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

    private fun vlessOutbound(link: ShareLink.Vless): Outbound<VlessOutboundSettings> {
        return Outbound(
            protocol = "vless",
            settings = VlessOutboundSettings(
                vnext = listOf(
                    VlessVnext(
                        address = link.host,
                        port = link.port,
                        users = listOf(
                            VlessUser(
                                id = link.uuid.toString(),
                                encryption = link.encryption,
                                flow = link.flow
                            )
                        )
                    )
                )
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni, link.params)
        )
    }

    private fun vmessOutbound(link: ShareLink.Vmess): Outbound<VmessOutboundSettings> {
        return Outbound(
            protocol = "vmess",
            settings = VmessOutboundSettings(
                vnext = listOf(
                    VmessVnext(
                        address = link.host,
                        port = link.port,
                        users = listOf(
                            VmessUser(
                                id = link.uuid.toString(),
                                security = link.encryption,
                                alterId = 0
                            )
                        )
                    )
                )
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni, link.params)
        )
    }

    private fun vmessLegacyOutbound(link: ShareLink.VmessLegacy): Outbound<VmessOutboundSettings> {
        val params = mutableMapOf<String, String>()
        link.path?.let { params["path"] = it }
        link.type?.let { params["type"] = it }
        return Outbound(
            protocol = "vmess",
            settings = VmessOutboundSettings(
                vnext = listOf(
                    VmessVnext(
                        address = link.host,
                        port = link.port,
                        users = listOf(
                            VmessUser(
                                id = link.id.toString(),
                                security = link.encryption,
                                alterId = link.aid
                            )
                        )
                    )
                )
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni ?: link.hostName ?: "", params)
        )
    }

    private fun trojanOutbound(link: ShareLink.Trojan): Outbound<TrojanOutboundSettings> {
        return Outbound(
            protocol = "trojan",
            settings = TrojanOutboundSettings(
                servers = listOf(
                    TrojanServer(
                        address = link.host,
                        port = link.port,
                        password = link.password
                    )
                )
            ),
            streamSettings = buildStreamSettings(link.params["security"] ?: "tls", link.params["type"] ?: "tcp", link.sni, link.params)
        )
    }

    private fun shadowsocksOutbound(link: ShareLink.Shadowsocks): Outbound<ShadowsocksOutboundSettings> {
        return Outbound(
            protocol = "shadowsocks",
            settings = ShadowsocksOutboundSettings(
                servers = listOf(
                    ShadowsocksServer(
                        address = link.host,
                        port = link.port,
                        method = link.method,
                        password = link.password,
                        uot = true
                    )
                )
            )
        )
    }

    private fun buildStreamSettings(security: String, transport: String, sni: String, params: Map<String, String>): StreamSettings {
        val actualSni = (params["sni"] ?: sni).ifEmpty { linkHost(params) }

        val realitySettings = if (security == "reality") {
            RealitySettings(
                fingerprint = params["fp"] ?: "chrome",
                serverName = actualSni,
                publicKey = params["pbk"] ?: "",
                shortId = params["sid"] ?: ""
            )
        } else null

        val tlsSettings = if (security == "tls" || security == "xtls") {
            TlsSettings(serverName = actualSni)
        } else null

        val wsSettings = if (transport == "ws") {
            WsSettings(
                path = params["path"] ?: "/",
                headers = mapOf("Host" to (params["host"] ?: actualSni))
            )
        } else null

        val grpcSettings = if (transport == "grpc") {
            GrpcSettings(
                serviceName = params["serviceName"] ?: params["path"] ?: "",
                multiMode = params["mode"] == "multi"
            )
        } else null

        val httpSettings = if (transport == "h2" || transport == "http") {
            HttpSettings(
                host = listOf(params["host"] ?: actualSni).filter { it.isNotBlank() },
                path = params["path"] ?: "/"
            )
        } else null

        val network = when (transport) {
            "tcp", "raw" -> "tcp"
            "h2", "http" -> "h2"
            else -> transport
        }

        return StreamSettings(
            network = network,
            security = if (security == "none") "" else security,
            realitySettings = realitySettings,
            tlsSettings = tlsSettings,
            wsSettings = wsSettings,
            grpcSettings = grpcSettings,
            httpSettings = httpSettings,
            sockopt = Sockopt(tcpFastOpen = true, tcpCongestion = "bbr", tcpNoDelay = true)
        )
    }

    fun prepareConfigForAndroid(configJson: String): String {
        try {
            val jsonObject = com.google.gson.JsonParser.parseString(configJson).asJsonObject
            var modified = false

            if (jsonObject.has("inbounds")) {
                val inbounds = jsonObject.getAsJsonArray("inbounds")
                for (i in 0 until inbounds.size()) {
                    val inbound = inbounds.get(i).asJsonObject
                    val protocol = inbound.get("protocol")?.asString
                    if (protocol == "tun") {
                        val settings = if (inbound.has("settings") && inbound.get("settings").isJsonObject) {
                            inbound.getAsJsonObject("settings")
                        } else {
                            val newSettings = com.google.gson.JsonObject()
                            inbound.add("settings", newSettings)
                            modified = true
                            newSettings
                        }

                        if (!settings.has("name") || settings.get("name").asString.isBlank()) {
                            settings.addProperty("name", "tun0")
                            modified = true
                        }
                    }
                }
            }

            // Ensure direct DNS IPs (1.1.1.1, 8.8.8.8, etc.) are routed to direct-out so internal DNS queries don't loop into proxy-out
            if (jsonObject.has("routing") && jsonObject.getAsJsonObject("routing").has("rules")) {
                val routing = jsonObject.getAsJsonObject("routing")
                val rules = routing.getAsJsonArray("rules")
                var hasDnsDirectRule = false
                for (i in 0 until rules.size()) {
                    val rule = rules.get(i).asJsonObject
                    if (rule.has("outboundTag") && rule.get("outboundTag").asString == "direct-out") {
                        if (rule.has("ip")) {
                            val ips = rule.getAsJsonArray("ip")
                            for (j in 0 until ips.size()) {
                                val ipStr = ips.get(j).asString
                                if (ipStr == "1.1.1.1" || ipStr == "8.8.8.8") {
                                    hasDnsDirectRule = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (!hasDnsDirectRule) {
                    val dnsRule = com.google.gson.JsonObject()
                    dnsRule.addProperty("type", "field")
                    val ips = com.google.gson.JsonArray()
                    ips.add("1.1.1.1")
                    ips.add("1.0.0.1")
                    ips.add("8.8.8.8")
                    ips.add("8.8.4.4")
                    dnsRule.add("ip", ips)
                    dnsRule.addProperty("outboundTag", "direct-out")
                    rules.add(dnsRule)
                    modified = true
                }
            }

            if (modified) {
                return gson.toJson(jsonObject)
            }
        } catch (_: Exception) {
        }
        return configJson
    }

    private fun linkHost(params: Map<String, String>): String = params["host"] ?: ""
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
    val listen: String? = null,
    val port: Int? = null,
    val settings: Any,
    val sniffing: Sniffing? = null
)

data class TunSettings(
    val name: String = "tun0",
    val mtu: Int = 9000
)

data class Sniffing(
    val enabled: Boolean = true,
    @SerializedName("destOverride")
    val destOverride: List<String> = listOf("http", "tls", "quic")
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

// VLESS
data class VlessOutboundSettings(
    val vnext: List<VlessVnext>
)

data class VlessVnext(
    val address: String,
    val port: Int,
    val users: List<VlessUser>
)

data class VlessUser(
    val id: String,
    val encryption: String = "none",
    val flow: String = ""
)

// VMess
data class VmessOutboundSettings(
    val vnext: List<VmessVnext>
)

data class VmessVnext(
    val address: String,
    val port: Int,
    val users: List<VmessUser>
)

data class VmessUser(
    val id: String,
    val security: String = "auto",
    val alterId: Int = 0
)

// Trojan
data class TrojanOutboundSettings(
    val servers: List<TrojanServer>
)

data class TrojanServer(
    val address: String,
    val port: Int,
    val password: String
)

// Shadowsocks
data class ShadowsocksOutboundSettings(
    val servers: List<ShadowsocksServer>
)

data class ShadowsocksServer(
    val address: String,
    val port: Int,
    val method: String,
    val password: String,
    val uot: Boolean = true
)

// Stream Settings
data class StreamSettings(
    val network: String = "tcp",
    val security: String = "",
    val realitySettings: RealitySettings? = null,
    val tlsSettings: TlsSettings? = null,
    val wsSettings: WsSettings? = null,
    val grpcSettings: GrpcSettings? = null,
    val httpSettings: HttpSettings? = null,
    val sockopt: Sockopt? = null
)

data class RealitySettings(
    val fingerprint: String = "chrome",
    val serverName: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = ""
)

data class TlsSettings(
    val allowInsecure: Boolean = false,
    val serverName: String = ""
)

data class WsSettings(
    val path: String = "/",
    val headers: Map<String, String> = emptyMap()
)

data class GrpcSettings(
    val serviceName: String = "",
    val multiMode: Boolean = false
)

data class HttpSettings(
    val host: List<String> = emptyList(),
    val path: String = "/"
)

data class Sockopt(
    val tcpFastOpen: Boolean = true,
    val tcpCongestion: String = "bbr",
    val tcpNoDelay: Boolean = true
)
// endregion
