package com.oklookat.spectra.model

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

object XrayConfigBuild {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun buildFullConfig(
        proxyOutbound: Outbound<*>,
        mtu: Int = 9000,
        dnsConfig: CustomDnsConfig = CustomDnsConfig.default(),
        routingConfig: CustomRoutingConfig = CustomRoutingConfig.default()
    ): String {
        val activeDnsServers = dnsConfig.servers
            .filter { it.enabled && it.address.isNotBlank() }
            .map { DnsServer(address = it.address) }
            .ifEmpty {
                listOf(
                    DnsServer(address = "https://1.1.1.1/dns-query"),
                    DnsServer(address = "https://8.8.8.8/dns-query")
                )
            }

        val activeRoutingRules = routingConfig.rules
            .filter { it.enabled }
            .map { rule ->
                RoutingRule(
                    type = "field",
                    inboundTag = rule.inboundTag.takeIf { it.isNotEmpty() },
                    port = rule.port.takeIf { it.isNotBlank() },
                    domain = rule.domains.takeIf { it.isNotEmpty() },
                    ip = rule.ip.takeIf { it.isNotEmpty() },
                    protocol = rule.protocols.takeIf { it.isNotEmpty() },
                    outboundTag = rule.outboundTag
                )
            }
            .ifEmpty {
                listOf(
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
            }

        val config = XrayFullConfig(
            log = Log(loglevel = "warning"),
            dns = Dns(
                queryStrategy = dnsConfig.queryStrategy.ifBlank { "UseIPv4" },
                servers = activeDnsServers
            ),
            routing = Routing(
                domainStrategy = routingConfig.domainStrategy.ifBlank { "AsIs" },
                rules = activeRoutingRules
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
            is ShareLink.Socks -> socksOutbound(link)
            is ShareLink.Hysteria2 -> hysteria2Outbound(link)
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
                                id = link.uuid,
                                encryption = link.encryption,
                                flow = link.flow.ifEmpty { null }
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
                                id = link.uuid,
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
        link.hostName?.let { params["host"] = it }
        link.alpn?.let { params["alpn"] = it }
        link.fp?.let { params["fp"] = it }
        link.sni?.let { params["sni"] = it }

        val net = link.transport.lowercase()
        if (net == "grpc" || net == "gun") {
            params["serviceName"] = link.path ?: ""
            if (link.type?.lowercase() == "multi") {
                params["mode"] = "multi"
            }
            params["type"] = "grpc"
        }

        return Outbound(
            protocol = "vmess",
            settings = VmessOutboundSettings(
                vnext = listOf(
                    VmessVnext(
                        address = link.host,
                        port = link.port,
                        users = listOf(
                            VmessUser(
                                id = link.id,
                                security = link.encryption,
                                alterId = link.aid
                            )
                        )
                    )
                )
            ),
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni ?: link.hostName ?: link.host, params)
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
            streamSettings = buildStreamSettings(link.security, link.transport, link.sni, link.params)
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
            ),
            streamSettings = if (link.params.isNotEmpty()) {
                buildStreamSettings(link.security, link.transport, link.params["sni"] ?: link.host, link.params)
            } else null
        )
    }

    private fun socksOutbound(link: ShareLink.Socks): Outbound<SocksOutboundSettings> {
        val users = if (!link.user.isNullOrEmpty()) {
            listOf(SocksUser(user = link.user, pass = link.pass ?: ""))
        } else null

        return Outbound(
            protocol = "socks",
            settings = SocksOutboundSettings(
                servers = listOf(
                    SocksServer(
                        address = link.host,
                        port = link.port,
                        users = users
                    )
                )
            ),
            streamSettings = StreamSettings(
                sockopt = Sockopt(tcpFastOpen = true, tcpCongestion = "bbr", tcpNoDelay = true)
            )
        )
    }

    private fun hysteria2Outbound(link: ShareLink.Hysteria2): Outbound<Hysteria2OutboundSettings> {
        val insecure = truthy(link.params["insecure"] ?: link.params["allowInsecure"] ?: "0")
        val sni = link.params["sni"] ?: link.host
        val pinSHA256 = link.params["pinSHA256"]?.replace(":", "")?.lowercase()
        val obfs = link.params["obfs"]?.lowercase()
        val obfsPassword = link.params["obfs-password"] ?: ""

        val finalMask = if (obfs == "salamander") {
            FinalMask(
                udp = listOf(
                    UdpMask(
                        type = "salamander",
                        settings = mapOf("password" to obfsPassword)
                    )
                )
            )
        } else null

        return Outbound(
            protocol = "hysteria",
            settings = Hysteria2OutboundSettings(
                version = 2,
                address = link.host,
                port = link.port
            ),
            streamSettings = StreamSettings(
                network = "hysteria",
                security = "tls",
                tlsSettings = TlsSettings(
                    allowInsecure = insecure,
                    serverName = sni,
                    alpn = listOf("h3"),
                    pinnedPeerCertSha256 = pinSHA256
                ),
                hysteriaSettings = HysteriaSettings(version = 2, auth = link.auth),
                finalmask = finalMask,
                sockopt = Sockopt(tcpFastOpen = true, tcpCongestion = "bbr", tcpNoDelay = true)
            )
        )
    }

    private fun buildStreamSettings(
        security: String,
        transport: String,
        sni: String,
        params: Map<String, String>
    ): StreamSettings {
        val rawNet = transport.lowercase()
        val network = when (rawNet) {
            "", "tcp", "raw" -> "tcp"
            "ws", "websocket" -> "ws"
            "grpc", "gun" -> "grpc"
            "httpupgrade" -> "httpupgrade"
            "xhttp", "splithttp" -> "xhttp"
            "kcp", "mkcp" -> "kcp"
            "h2", "http" -> "h2"
            else -> rawNet
        }

        val rawSec = security.lowercase()
        val secMode = when (rawSec) {
            "", "none" -> "none"
            "tls", "xtls" -> "tls"
            "reality" -> "reality"
            else -> rawSec
        }

        val hostHeader = params["host"] ?: ""
        val actualSni = (params["sni"] ?: params["peer"] ?: sni).ifEmpty { hostHeader }

        // ── Transports ──
        var tcpSettings: TcpSettings? = null
        var wsSettings: WsSettings? = null
        var grpcSettings: GrpcSettings? = null
        var httpupgradeSettings: HttpUpgradeSettings? = null
        var xhttpSettings: XhttpSettings? = null
        var kcpSettings: KcpSettings? = null
        var httpSettings: HttpSettings? = null

        when (network) {
            "tcp" -> {
                val headerType = params["headerType"]?.lowercase() ?: "none"
                if (headerType == "http") {
                    val pathList = splitCsv(params["path"] ?: "/")
                    val hostList = splitCsv(hostHeader)
                    tcpSettings = TcpSettings(
                        header = TcpHeader(
                            type = "http",
                            request = TcpHttpRequest(
                                path = pathList.ifEmpty { listOf("/") },
                                headers = if (hostList.isNotEmpty()) mapOf("Host" to hostList) else null
                            )
                        )
                    )
                }
            }
            "ws" -> {
                val path = params["path"] ?: "/"
                wsSettings = WsSettings(
                    path = path,
                    headers = if (hostHeader.isNotBlank()) mapOf("Host" to hostHeader) else null
                )
            }
            "grpc" -> {
                val serviceName = params["serviceName"] ?: params["path"] ?: ""
                val multiMode = params["mode"]?.lowercase() == "multi"
                val authority = params["authority"]?.takeIf { it.isNotBlank() }
                grpcSettings = GrpcSettings(
                    serviceName = serviceName,
                    multiMode = multiMode,
                    authority = authority
                )
            }
            "httpupgrade" -> {
                val path = params["path"] ?: "/"
                httpupgradeSettings = HttpUpgradeSettings(
                    path = path,
                    host = hostHeader.takeIf { it.isNotBlank() }
                )
            }
            "xhttp" -> {
                val path = params["path"] ?: "/"
                val mode = params["mode"] ?: "auto"
                val extraStr = params["extra"]
                val extraJson = extraStr?.let {
                    try {
                        com.google.gson.JsonParser.parseString(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                xhttpSettings = XhttpSettings(
                    path = path,
                    mode = mode,
                    host = hostHeader.takeIf { it.isNotBlank() },
                    extra = extraJson
                )
            }
            "kcp" -> {
                val headerType = params["headerType"] ?: "none"
                val seed = params["seed"] ?: params["path"]
                kcpSettings = KcpSettings(
                    header = KcpHeader(type = headerType),
                    seed = seed?.takeIf { it.isNotBlank() }
                )
            }
            "h2", "http" -> {
                val path = params["path"] ?: "/"
                val hostList = splitCsv(hostHeader)
                httpSettings = HttpSettings(
                    host = hostList,
                    path = path
                )
            }
        }

        // ── Security ──
        var tlsSettings: TlsSettings? = null
        var realitySettings: RealitySettings? = null

        when (secMode) {
            "tls" -> {
                val allowInsecure = truthy(params["allowInsecure"] ?: params["insecure"] ?: "0")
                val alpnList = splitCsv(params["alpn"] ?: "")
                val fp = params["fp"]?.takeIf { it.isNotBlank() }
                val pcs = params["pcs"]?.takeIf { it.isNotBlank() }
                val ech = params["ech"]?.takeIf { it.isNotBlank() }
                tlsSettings = TlsSettings(
                    allowInsecure = allowInsecure,
                    serverName = actualSni,
                    alpn = alpnList.ifEmpty { null },
                    fingerprint = fp,
                    pinnedPeerCertSha256 = pcs,
                    echConfigList = ech
                )
            }
            "reality" -> {
                val fp = params["fp"]?.takeIf { it.isNotBlank() } ?: "chrome"
                val pbk = params["pbk"] ?: ""
                val sid = params["sid"] ?: ""
                val spx = params["spx"] ?: ""
                val pqv = params["pqv"]?.takeIf { it.isNotBlank() }
                realitySettings = RealitySettings(
                    fingerprint = fp,
                    serverName = actualSni,
                    publicKey = pbk,
                    shortId = sid,
                    spiderX = spx,
                    mldsa65Verify = pqv
                )
            }
        }

        return StreamSettings(
            network = network,
            security = if (secMode == "none") "" else secMode,
            realitySettings = realitySettings,
            tlsSettings = tlsSettings,
            wsSettings = wsSettings,
            grpcSettings = grpcSettings,
            httpupgradeSettings = httpupgradeSettings,
            xhttpSettings = xhttpSettings,
            kcpSettings = kcpSettings,
            tcpSettings = tcpSettings,
            httpSettings = httpSettings,
            sockopt = Sockopt(tcpFastOpen = true, tcpCongestion = "bbr", tcpNoDelay = true)
        )
    }

    private fun splitCsv(value: String): List<String> =
        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun truthy(value: String): Boolean =
        value.trim().lowercase() in setOf("1", "true", "yes", "on")

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

            if (modified) {
                return gson.toJson(jsonObject)
            }
        } catch (_: Exception) {
        }
        return configJson
    }

    fun prepareConfigForPing(configJson: String): String {
        try {
            val jsonObject = com.google.gson.JsonParser.parseString(configJson).asJsonObject
            jsonObject.add("inbounds", com.google.gson.JsonArray())
            return gson.toJson(jsonObject)
        } catch (_: Exception) {
            return configJson
        }
    }

    fun createWifiBypassConfig(configJson: String): String {
        try {
            val jsonObject = com.google.gson.JsonParser.parseString(configJson).asJsonObject

            val outbounds = if (jsonObject.has("outbounds") && jsonObject.get("outbounds").isJsonArray) {
                jsonObject.getAsJsonArray("outbounds")
            } else {
                val array = com.google.gson.JsonArray()
                jsonObject.add("outbounds", array)
                array
            }

            var directIdx = -1
            for (i in 0 until outbounds.size()) {
                val outbound = outbounds.get(i).asJsonObject
                val tag = outbound.get("tag")?.asString
                val protocol = outbound.get("protocol")?.asString
                if (tag == "direct-out" || protocol == "freedom") {
                    directIdx = i
                    break
                }
            }

            val directOutbound = if (directIdx != -1) {
                outbounds.remove(directIdx).asJsonObject
            } else {
                val d = com.google.gson.JsonObject()
                d.addProperty("tag", "direct-out")
                d.addProperty("protocol", "freedom")
                val s = com.google.gson.JsonObject()
                s.addProperty("domainStrategy", "UseIPv4")
                d.add("settings", s)
                d
            }

            val newOutbounds = com.google.gson.JsonArray()
            newOutbounds.add(directOutbound)
            for (i in 0 until outbounds.size()) {
                newOutbounds.add(outbounds.get(i))
            }
            jsonObject.add("outbounds", newOutbounds)

            if (jsonObject.has("dns") && jsonObject.get("dns").isJsonObject) {
                val dns = jsonObject.getAsJsonObject("dns")
                if (dns.has("servers") && dns.get("servers").isJsonArray) {
                    val servers = dns.getAsJsonArray("servers")
                    val newServers = com.google.gson.JsonArray()
                    for (i in 0 until servers.size()) {
                        val server = servers.get(i)
                        var isFake = false
                        if (server.isJsonPrimitive && server.asString == "fakedns") {
                            isFake = true
                        } else if (server.isJsonObject) {
                            val addr = server.asJsonObject.get("address")?.asString
                            if (addr == "fakedns") isFake = true
                        }
                        if (!isFake) {
                            newServers.add(server)
                        }
                    }
                    if (newServers.size() == 0) {
                        newServers.add("1.1.1.1")
                        newServers.add("8.8.8.8")
                    }
                    dns.add("servers", newServers)
                }
            }

            val routing = if (jsonObject.has("routing") && jsonObject.get("routing").isJsonObject) {
                jsonObject.getAsJsonObject("routing")
            } else {
                val r = com.google.gson.JsonObject()
                jsonObject.add("routing", r)
                r
            }

            val rules = if (routing.has("rules") && routing.get("rules").isJsonArray) {
                routing.getAsJsonArray("rules")
            } else {
                val r = com.google.gson.JsonArray()
                routing.add("rules", r)
                r
            }

            val bypassRule = com.google.gson.JsonObject()
            bypassRule.addProperty("type", "field")
            bypassRule.addProperty("network", "tcp,udp")
            val inboundsArray = com.google.gson.JsonArray()
            inboundsArray.add("tun-in")
            bypassRule.add("inboundTag", inboundsArray)
            bypassRule.addProperty("outboundTag", "direct-out")

            val newRules = com.google.gson.JsonArray()
            newRules.add(bypassRule)
            for (i in 0 until rules.size()) {
                newRules.add(rules.get(i))
            }
            routing.add("rules", newRules)

            return gson.toJson(jsonObject)
        } catch (_: Exception) {
            return configJson
        }
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
    val flow: String? = null
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

// SOCKS
data class SocksOutboundSettings(
    val servers: List<SocksServer>
)

data class SocksServer(
    val address: String,
    val port: Int,
    val users: List<SocksUser>? = null
)

data class SocksUser(
    val user: String,
    val pass: String
)

// Hysteria 2
data class Hysteria2OutboundSettings(
    val version: Int = 2,
    val address: String,
    val port: Int
)

data class HysteriaSettings(
    val version: Int = 2,
    val auth: String = ""
)

data class FinalMask(
    val udp: List<UdpMask>? = null
)

data class UdpMask(
    val type: String,
    val settings: Map<String, Any>? = null
)

// Stream Settings
data class StreamSettings(
    val network: String = "tcp",
    val security: String = "",
    val realitySettings: RealitySettings? = null,
    val tlsSettings: TlsSettings? = null,
    val wsSettings: WsSettings? = null,
    val grpcSettings: GrpcSettings? = null,
    val httpupgradeSettings: HttpUpgradeSettings? = null,
    val xhttpSettings: XhttpSettings? = null,
    val kcpSettings: KcpSettings? = null,
    val tcpSettings: TcpSettings? = null,
    val httpSettings: HttpSettings? = null,
    val hysteriaSettings: HysteriaSettings? = null,
    val finalmask: FinalMask? = null,
    val sockopt: Sockopt? = null
)

data class TcpSettings(
    val header: TcpHeader? = null
)

data class TcpHeader(
    val type: String = "none",
    val request: TcpHttpRequest? = null
)

data class TcpHttpRequest(
    val path: List<String>? = null,
    val headers: Map<String, List<String>>? = null
)

data class RealitySettings(
    val fingerprint: String = "chrome",
    val serverName: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val mldsa65Verify: String? = null
)

data class TlsSettings(
    val allowInsecure: Boolean = false,
    val serverName: String = "",
    val alpn: List<String>? = null,
    val fingerprint: String? = null,
    val pinnedPeerCertSha256: String? = null,
    val echConfigList: String? = null
)

data class WsSettings(
    val path: String = "/",
    val headers: Map<String, String>? = null
)

data class GrpcSettings(
    val serviceName: String = "",
    val multiMode: Boolean = false,
    val authority: String? = null
)

data class HttpUpgradeSettings(
    val path: String = "/",
    val host: String? = null
)

data class XhttpSettings(
    val path: String = "/",
    val mode: String = "auto",
    val host: String? = null,
    val extra: Any? = null
)

data class KcpSettings(
    val header: KcpHeader? = null,
    val seed: String? = null
)

data class KcpHeader(
    val type: String = "none"
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
