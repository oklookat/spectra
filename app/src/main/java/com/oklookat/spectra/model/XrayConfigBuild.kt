package com.oklookat.spectra.model

import androidx.annotation.Keep
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

    fun prepareConfigForAndroid(
        configJson: String,
        isTunModeEnabled: Boolean = true,
        socksPort: Int = 10808,
        logLevel: String = "warning",
        dnsLog: Boolean = false,
        maskAddress: String = ""
    ): String {
        try {
            val jsonObject = com.google.gson.JsonParser.parseString(configJson).asJsonObject
            var modified = false

            // ── Handle "log" object ──
            val logObj = if (jsonObject.has("log") && jsonObject.get("log").isJsonObject) {
                jsonObject.getAsJsonObject("log")
            } else {
                val newLog = com.google.gson.JsonObject()
                jsonObject.add("log", newLog)
                modified = true
                newLog
            }

            // Remove file paths (access, error) as we do not write to files
            if (logObj.has("access")) {
                logObj.remove("access")
                modified = true
            }
            if (logObj.has("error")) {
                logObj.remove("error")
                modified = true
            }

            // Override loglevel with global setting if different
            if (logObj.get("loglevel")?.asString != logLevel) {
                logObj.addProperty("loglevel", logLevel)
                modified = true
            }

            // Override dnsLog with global setting if different
            if (logObj.get("dnsLog")?.asBoolean != dnsLog) {
                logObj.addProperty("dnsLog", dnsLog)
                modified = true
            }

            // Override maskAddress with global setting if different
            val currentMask = if (logObj.has("maskAddress")) logObj.get("maskAddress").asString else ""
            if (maskAddress.isNotBlank()) {
                if (currentMask != maskAddress) {
                    logObj.addProperty("maskAddress", maskAddress)
                    modified = true
                }
            } else if (logObj.has("maskAddress")) {
                logObj.remove("maskAddress")
                modified = true
            }

            var hasTun = false
            var replacedSocksTag: String? = null

            if (jsonObject.has("inbounds")) {
                val inbounds = jsonObject.getAsJsonArray("inbounds")
                for (i in 0 until inbounds.size()) {
                    val inbound = inbounds.get(i).asJsonObject
                    val protocol = inbound.get("protocol")?.asString
                    
                    if (protocol == "tun") {
                        hasTun = true
                    }
                    
                    if (isTunModeEnabled && protocol == "socks") {
                        val portElement = inbound.get("port")
                        val port = if (portElement != null && portElement.isJsonPrimitive) portElement.asInt else 0
                        if (port == socksPort) {
                            replacedSocksTag = inbound.get("tag")?.asString
                            
                            // Remove all old socks settings
                            inbound.remove("listen")
                            inbound.remove("port")
                            
                            // Replace this inbound with tun
                            inbound.addProperty("protocol", "tun")
                            inbound.addProperty("tag", "tun-in")
                            
                            val settings = com.google.gson.JsonObject()
                            settings.addProperty("name", "tun0")
                            settings.addProperty("mtu", 9000)
                            
                            inbound.add("settings", settings)
                            
                            // Sniffing
                            val sniffing = com.google.gson.JsonObject()
                            sniffing.addProperty("enabled", true)
                            sniffing.addProperty("routeOnly", true)
                            val destOverride = com.google.gson.JsonArray()
                            destOverride.add("http")
                            destOverride.add("tls")
                            destOverride.add("quic")
                            destOverride.add("fakedns")
                            sniffing.add("destOverride", destOverride)
                            inbound.add("sniffing", sniffing)
                            
                            modified = true
                            hasTun = true
                        }
                    } else if (protocol == "tun") {
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
                
                // If tun mode is enabled but no tun inbound exists, inject one
                if (isTunModeEnabled && !hasTun) {
                    val tunInbound = com.google.gson.JsonObject()
                    tunInbound.addProperty("protocol", "tun")
                    tunInbound.addProperty("tag", "tun-in")
                    
                    val settings = com.google.gson.JsonObject()
                    settings.addProperty("name", "tun0")
                    settings.addProperty("mtu", 9000)
                    tunInbound.add("settings", settings)
                    
                    val sniffing = com.google.gson.JsonObject()
                    sniffing.addProperty("enabled", true)
                    sniffing.addProperty("routeOnly", true)
                    val destOverride = com.google.gson.JsonArray()
                    destOverride.add("http")
                    destOverride.add("tls")
                    destOverride.add("quic")
                    destOverride.add("fakedns")
                    sniffing.add("destOverride", destOverride)
                    tunInbound.add("sniffing", sniffing)
                    
                    inbounds.add(tunInbound)
                    modified = true
                }
            } else if (isTunModeEnabled) {
                // If there are no inbounds at all, but tun mode is enabled, add inbounds array with tun
                val tunInbound = com.google.gson.JsonObject()
                tunInbound.addProperty("protocol", "tun")
                tunInbound.addProperty("tag", "tun-in")
                
                val settings = com.google.gson.JsonObject()
                settings.addProperty("name", "tun0")
                settings.addProperty("mtu", 9000)
                tunInbound.add("settings", settings)
                
                val sniffing = com.google.gson.JsonObject()
                sniffing.addProperty("enabled", true)
                sniffing.addProperty("routeOnly", true)
                val destOverride = com.google.gson.JsonArray()
                destOverride.add("http")
                destOverride.add("tls")
                destOverride.add("quic")
                destOverride.add("fakedns")
                sniffing.add("destOverride", destOverride)
                tunInbound.add("sniffing", sniffing)
                
                val inbounds = com.google.gson.JsonArray()
                inbounds.add(tunInbound)
                jsonObject.add("inbounds", inbounds)
                modified = true
            }
            
            if (replacedSocksTag != null && jsonObject.has("routing")) {
                val routing = jsonObject.get("routing")
                if (routing.isJsonObject) {
                    val rules = routing.asJsonObject.get("rules")
                    if (rules != null && rules.isJsonArray) {
                        for (i in 0 until rules.asJsonArray.size()) {
                            val rule = rules.asJsonArray.get(i)
                            if (rule.isJsonObject) {
                                val inboundTag = rule.asJsonObject.get("inboundTag")
                                if (inboundTag != null) {
                                    if (inboundTag.isJsonArray) {
                                        val tags = inboundTag.asJsonArray
                                        for (j in 0 until tags.size()) {
                                            if (tags.get(j).asString == replacedSocksTag) {
                                                tags.set(j, com.google.gson.JsonPrimitive("tun-in"))
                                                modified = true
                                            }
                                        }
                                    } else if (inboundTag.isJsonPrimitive && inboundTag.asString == replacedSocksTag) {
                                        rule.asJsonObject.addProperty("inboundTag", "tun-in")
                                        modified = true
                                    }
                                }
                            }
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

@Keep
data class XrayFullConfig(
    @SerializedName("log") val log: Log? = Log(),
    @SerializedName("dns") val dns: Dns? = null,
    @SerializedName("routing") val routing: Routing? = null,
    @SerializedName("inbounds") val inbounds: List<Inbound>,
    @SerializedName("outbounds") val outbounds: List<Outbound<*>>
)

@Keep
data class Log(
    @SerializedName("loglevel") val loglevel: String = "warning"
)

@Keep
data class Dns(
    @SerializedName("queryStrategy") val queryStrategy: String = "UseIPv4",
    @SerializedName("servers") val servers: List<DnsServer>
)

@Keep
data class DnsServer(
    @SerializedName("address") val address: String
)

@Keep
data class Routing(
    @SerializedName("domainStrategy") val domainStrategy: String = "AsIs",
    @SerializedName("rules") val rules: List<RoutingRule>
)

@Keep
data class RoutingRule(
    @SerializedName("type") val type: String? = "field",
    @SerializedName("inboundTag") val inboundTag: List<String>? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("domain") val domain: List<String>? = null,
    @SerializedName("ip") val ip: List<String>? = null,
    @SerializedName("protocol") val protocol: List<String>? = null,
    @SerializedName("outboundTag") val outboundTag: String
)

@Keep
data class Inbound(
    @SerializedName("protocol") val protocol: String,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("listen") val listen: String? = null,
    @SerializedName("port") val port: Int? = null,
    @SerializedName("settings") val settings: Any,
    @SerializedName("sniffing") val sniffing: Sniffing? = null
)

@Keep
data class TunSettings(
    @SerializedName("name") val name: String = "tun0",
    @SerializedName("mtu") val mtu: Int = 9000
)

@Keep
data class Sniffing(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("destOverride") val destOverride: List<String> = listOf("http", "tls", "quic")
)

@Keep
data class Outbound<T>(
    @SerializedName("protocol") val protocol: String,
    @SerializedName("settings") val settings: T,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("streamSettings") val streamSettings: StreamSettings? = null
)

@Keep
data class FreedomSettings(
    @SerializedName("targetStrategy") val targetStrategy: String = "UseIPv4"
)

@Keep
data class DnsOutboundSettings(
    @SerializedName("nonIPQuery") val nonIPQuery: String = "skip"
)

@Keep
data class BlackholeSettings(
    @SerializedName("response") val response: BlackholeResponse
)

@Keep
data class BlackholeResponse(
    @SerializedName("type") val type: String = "http"
)

// VLESS
@Keep
data class VlessOutboundSettings(
    @SerializedName("vnext") val vnext: List<VlessVnext>
)

@Keep
data class VlessVnext(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("users") val users: List<VlessUser>
)

@Keep
data class VlessUser(
    @SerializedName("id") val id: String,
    @SerializedName("encryption") val encryption: String = "none",
    @SerializedName("flow") val flow: String? = null
)

// VMess
@Keep
data class VmessOutboundSettings(
    @SerializedName("vnext") val vnext: List<VmessVnext>
)

@Keep
data class VmessVnext(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("users") val users: List<VmessUser>
)

@Keep
data class VmessUser(
    @SerializedName("id") val id: String,
    @SerializedName("security") val security: String = "auto",
    @SerializedName("alterId") val alterId: Int = 0
)

// Trojan
@Keep
data class TrojanOutboundSettings(
    @SerializedName("servers") val servers: List<TrojanServer>
)

@Keep
data class TrojanServer(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("password") val password: String
)

// Shadowsocks
@Keep
data class ShadowsocksOutboundSettings(
    @SerializedName("servers") val servers: List<ShadowsocksServer>
)

@Keep
data class ShadowsocksServer(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("method") val method: String,
    @SerializedName("password") val password: String,
    @SerializedName("uot") val uot: Boolean = true
)

// SOCKS
@Keep
data class SocksOutboundSettings(
    @SerializedName("servers") val servers: List<SocksServer>
)

@Keep
data class SocksServer(
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int,
    @SerializedName("users") val users: List<SocksUser>? = null
)

@Keep
data class SocksUser(
    @SerializedName("user") val user: String,
    @SerializedName("pass") val pass: String
)

// Hysteria 2
@Keep
data class Hysteria2OutboundSettings(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("address") val address: String,
    @SerializedName("port") val port: Int
)

@Keep
data class HysteriaSettings(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("auth") val auth: String = ""
)

@Keep
data class FinalMask(
    @SerializedName("udp") val udp: List<UdpMask>? = null
)

@Keep
data class UdpMask(
    @SerializedName("type") val type: String,
    @SerializedName("settings") val settings: Map<String, Any>? = null
)

// Stream Settings
@Keep
data class StreamSettings(
    @SerializedName("network") val network: String = "tcp",
    @SerializedName("security") val security: String = "",
    @SerializedName("realitySettings") val realitySettings: RealitySettings? = null,
    @SerializedName("tlsSettings") val tlsSettings: TlsSettings? = null,
    @SerializedName("wsSettings") val wsSettings: WsSettings? = null,
    @SerializedName("grpcSettings") val grpcSettings: GrpcSettings? = null,
    @SerializedName("httpupgradeSettings") val httpupgradeSettings: HttpUpgradeSettings? = null,
    @SerializedName("xhttpSettings") val xhttpSettings: XhttpSettings? = null,
    @SerializedName("kcpSettings") val kcpSettings: KcpSettings? = null,
    @SerializedName("tcpSettings") val tcpSettings: TcpSettings? = null,
    @SerializedName("httpSettings") val httpSettings: HttpSettings? = null,
    @SerializedName("hysteriaSettings") val hysteriaSettings: HysteriaSettings? = null,
    @SerializedName("finalmask") val finalmask: FinalMask? = null,
    @SerializedName("sockopt") val sockopt: Sockopt? = null
)

@Keep
data class TcpSettings(
    @SerializedName("header") val header: TcpHeader? = null
)

@Keep
data class TcpHeader(
    @SerializedName("type") val type: String = "none",
    @SerializedName("request") val request: TcpHttpRequest? = null
)

@Keep
data class TcpHttpRequest(
    @SerializedName("path") val path: List<String>? = null,
    @SerializedName("headers") val headers: Map<String, List<String>>? = null
)

@Keep
data class RealitySettings(
    @SerializedName("fingerprint") val fingerprint: String = "chrome",
    @SerializedName("serverName") val serverName: String = "",
    @SerializedName("publicKey") val publicKey: String = "",
    @SerializedName("shortId") val shortId: String = "",
    @SerializedName("spiderX") val spiderX: String = "",
    @SerializedName("mldsa65Verify") val mldsa65Verify: String? = null
)

@Keep
data class TlsSettings(
    @SerializedName("allowInsecure") val allowInsecure: Boolean = false,
    @SerializedName("serverName") val serverName: String = "",
    @SerializedName("alpn") val alpn: List<String>? = null,
    @SerializedName("fingerprint") val fingerprint: String? = null,
    @SerializedName("pinnedPeerCertSha256") val pinnedPeerCertSha256: String? = null,
    @SerializedName("echConfigList") val echConfigList: String? = null
)

@Keep
data class WsSettings(
    @SerializedName("path") val path: String = "/",
    @SerializedName("headers") val headers: Map<String, String>? = null
)

@Keep
data class GrpcSettings(
    @SerializedName("serviceName") val serviceName: String = "",
    @SerializedName("multiMode") val multiMode: Boolean = false,
    @SerializedName("authority") val authority: String? = null
)

@Keep
data class HttpUpgradeSettings(
    @SerializedName("path") val path: String = "/",
    @SerializedName("host") val host: String? = null
)

@Keep
data class XhttpSettings(
    @SerializedName("path") val path: String = "/",
    @SerializedName("mode") val mode: String = "auto",
    @SerializedName("host") val host: String? = null,
    @SerializedName("extra") val extra: Any? = null
)

@Keep
data class KcpSettings(
    @SerializedName("header") val header: KcpHeader? = null,
    @SerializedName("seed") val seed: String? = null
)

@Keep
data class KcpHeader(
    @SerializedName("type") val type: String = "none"
)

@Keep
data class HttpSettings(
    @SerializedName("host") val host: List<String> = emptyList(),
    @SerializedName("path") val path: String = "/"
)

@Keep
data class Sockopt(
    @SerializedName("tcpFastOpen") val tcpFastOpen: Boolean = true,
    @SerializedName("tcpCongestion") val tcpCongestion: String = "bbr",
    @SerializedName("tcpNoDelay") val tcpNoDelay: Boolean = true
)
// endregion
