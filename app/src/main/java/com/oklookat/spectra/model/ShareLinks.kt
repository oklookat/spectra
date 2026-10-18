package com.oklookat.spectra.model

import java.net.URLDecoder
import java.util.Base64

/**
 * Proxy node data model matching Python/v2rayNG specification.
 */
data class ProxyNode(
    var protocol: String = "",
    var remarks: String = "",
    var server: String = "",
    var serverPort: Int = 0,

    // auth
    var uuid: String? = null,          // VLESS / VMess / SOCKS user
    var password: String? = null,      // Trojan / SS / HY2 / SOCKS pass
    var method: String? = null,        // encryption / SS method

    // transport
    var network: String = "tcp",        // type
    var headerType: String? = null,
    var host: String? = null,
    var path: String? = null,
    var seed: String? = null,          // kcp
    var kcpMtu: Int? = null,
    var kcpTti: Int? = null,
    var quicSecurity: String? = null,
    var quicKey: String? = null,
    var mode: String? = null,          // grpc / xhttp
    var serviceName: String? = null,
    var authority: String? = null,
    var xhttpMode: String? = null,
    var xhttpExtra: String? = null,
    var finalMask: String? = null,    // fm

    // security / tls / reality
    var security: String? = null,      // none / tls / reality
    var insecure: Boolean = false,
    var sni: String? = null,
    var fingerprint: String? = null,   // fp
    var alpn: String? = null,
    var ech: String? = null,
    var vcn: String? = null,           // verifyPeerCertByName
    var pcs: String? = null,           // pinnedCA256
    var publicKey: String? = null,    // pbk
    var shortId: String? = null,      // sid
    var spiderX: String? = null,      // spx
    var pqv: String? = null,           // mldsa65Verify
    var flow: String? = null,

    // hysteria2
    var obfs: String? = null,
    var obfsPassword: String? = null,
    var portHopping: String? = null,  // mport
    var pinSha256: String? = null,

    // raw
    var params: Map<String, String> = emptyMap(),
    var raw: String = "",
) {
    fun applyQuery(qp: Map<String, String>) {
        qp["type"]?.takeIf { it.isNotBlank() }?.let { network = it }

        headerType = qp["headerType"]
        host = qp["host"]
        path = qp["path"]

        seed = qp["seed"]
        kcpMtu = qp["mtu"]?.toIntOrNull()
        kcpTti = qp["tti"]?.toIntOrNull()
        quicSecurity = qp["quicSecurity"]
        quicKey = qp["key"]
        mode = qp["mode"]
        serviceName = qp["serviceName"]
        authority = qp["authority"]
        xhttpMode = qp["mode"]
        xhttpExtra = qp["extra"]
        finalMask = qp["fm"]

        val sec = qp["security"]
        security = when (sec) {
            "tls", "reality" -> sec
            "none", "", null -> null
            else -> sec
        }

        val insecureVal = qp["insecure"] ?: qp["allowInsecure"] ?: qp["allow_insecure"]
        if (insecureVal != null) {
            insecure = insecureVal in setOf("1", "true", "True")
        }

        sni = qp["sni"]
        fingerprint = qp["fp"]
        alpn = qp["alpn"]
        ech = qp["ech"]
        vcn = qp["vcn"]
        pcs = qp["pcs"]
        publicKey = qp["pbk"]
        shortId = qp["sid"]
        spiderX = qp["spx"]
        pqv = qp["pqv"]
        flow = qp["flow"]

        params = qp
    }

    fun toDict(skipNone: Boolean = true): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        fun putIfValid(key: String, value: Any?) {
            if (!skipNone) {
                if (value != null) map[key] = value
            } else {
                val isValidValue = value != null && value != "" && value != false && (value !is Map<*, *> || value.isNotEmpty())
                if (isValidValue) {
                    map[key] = value
                }
            }
        }

        putIfValid("protocol", protocol)
        putIfValid("remarks", remarks)
        putIfValid("server", server)
        putIfValid("server_port", serverPort)
        putIfValid("uuid", uuid)
        putIfValid("password", password)
        putIfValid("method", method)
        putIfValid("network", network)
        putIfValid("header_type", headerType)
        putIfValid("host", host)
        putIfValid("path", path)
        putIfValid("seed", seed)
        putIfValid("kcp_mtu", kcpMtu)
        putIfValid("kcp_tti", kcpTti)
        putIfValid("quic_security", quicSecurity)
        putIfValid("quic_key", quicKey)
        putIfValid("mode", mode)
        putIfValid("service_name", serviceName)
        putIfValid("authority", authority)
        putIfValid("xhttp_mode", xhttpMode)
        putIfValid("xhttp_extra", xhttpExtra)
        putIfValid("final_mask", finalMask)
        putIfValid("security", security)
        putIfValid("insecure", insecure)
        putIfValid("sni", sni)
        putIfValid("fingerprint", fingerprint)
        putIfValid("alpn", alpn)
        putIfValid("ech", ech)
        putIfValid("vcn", vcn)
        putIfValid("pcs", pcs)
        putIfValid("public_key", publicKey)
        putIfValid("short_id", shortId)
        putIfValid("spider_x", spiderX)
        putIfValid("pqv", pqv)
        putIfValid("flow", flow)
        putIfValid("obfs", obfs)
        putIfValid("obfs_password", obfsPassword)
        putIfValid("port_hopping", portHopping)
        putIfValid("pin_sha256", pinSha256)
        putIfValid("params", params)
        putIfValid("raw", raw)

        return map
    }

    fun toShareLink(): ShareLink {
        val nameVal = remarks.takeIf { it.isNotBlank() && it != "none" }
        val paramsMap = buildParamsMap()

        return when (protocol.lowercase()) {
            "vless" -> ShareLink.Vless(
                uuid = uuid ?: "",
                host = server,
                port = serverPort,
                params = paramsMap,
                name = nameVal
            )
            "vmess" -> {
                if (params.containsKey("v") || params.containsKey("id") || params.containsKey("add")) {
                    val aidVal = params["aid"]?.toIntOrNull() ?: 0
                    ShareLink.VmessLegacy(
                        id = uuid ?: params["id"] ?: "",
                        add = server.ifBlank { params["add"] ?: "" },
                        port = serverPort,
                        aid = aidVal,
                        scy = method ?: params["scy"],
                        net = network.ifBlank { params["net"] },
                        type = headerType ?: params["type"],
                        hostName = host ?: params["host"],
                        path = path ?: params["path"],
                        tls = security ?: params["tls"],
                        sni = sni ?: params["sni"],
                        alpn = alpn ?: params["alpn"],
                        fp = fingerprint ?: params["fp"],
                        name = nameVal
                    )
                } else {
                    ShareLink.Vmess(
                        uuid = uuid ?: "",
                        host = server,
                        port = serverPort,
                        params = paramsMap,
                        name = nameVal
                    )
                }
            }
            "trojan" -> ShareLink.Trojan(
                password = password ?: "",
                host = server,
                port = serverPort,
                params = paramsMap,
                name = nameVal
            )
            "shadowsocks" -> ShareLink.Shadowsocks(
                method = method ?: "",
                password = password ?: "",
                host = server,
                port = serverPort,
                params = paramsMap,
                name = nameVal
            )
            "socks" -> ShareLink.Socks(
                user = uuid,
                pass = password,
                host = server,
                port = serverPort,
                name = nameVal
            )
            "hysteria2" -> ShareLink.Hysteria2(
                auth = password ?: uuid ?: "",
                host = server,
                port = serverPort,
                params = paramsMap,
                name = nameVal
            )
            else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
        }
    }

    private fun buildParamsMap(): Map<String, String> {
        val map = LinkedHashMap<String, String>(params)
        network.takeIf { it.isNotBlank() }?.let { map["type"] = it }
        headerType?.takeIf { it.isNotBlank() }?.let { map["headerType"] = it }
        host?.takeIf { it.isNotBlank() }?.let { map["host"] = it }
        path?.takeIf { it.isNotBlank() }?.let { map["path"] = it }
        seed?.takeIf { it.isNotBlank() }?.let { map["seed"] = it }
        quicSecurity?.takeIf { it.isNotBlank() }?.let { map["quicSecurity"] = it }
        quicKey?.takeIf { it.isNotBlank() }?.let { map["key"] = it }
        mode?.takeIf { it.isNotBlank() }?.let { map["mode"] = it }
        serviceName?.takeIf { it.isNotBlank() }?.let { map["serviceName"] = it }
        authority?.takeIf { it.isNotBlank() }?.let { map["authority"] = it }
        xhttpExtra?.takeIf { it.isNotBlank() }?.let { map["extra"] = it }
        finalMask?.takeIf { it.isNotBlank() }?.let { map["fm"] = it }
        security?.takeIf { it.isNotBlank() }?.let { map["security"] = it }
        if (insecure) map["insecure"] = "1"
        sni?.takeIf { it.isNotBlank() }?.let { map["sni"] = it }
        fingerprint?.takeIf { it.isNotBlank() }?.let { map["fp"] = it }
        alpn?.takeIf { it.isNotBlank() }?.let { map["alpn"] = it }
        ech?.takeIf { it.isNotBlank() }?.let { map["ech"] = it }
        vcn?.takeIf { it.isNotBlank() }?.let { map["vcn"] = it }
        pcs?.takeIf { it.isNotBlank() }?.let { map["pcs"] = it }
        publicKey?.takeIf { it.isNotBlank() }?.let { map["pbk"] = it }
        shortId?.takeIf { it.isNotBlank() }?.let { map["sid"] = it }
        spiderX?.takeIf { it.isNotBlank() }?.let { map["spx"] = it }
        pqv?.takeIf { it.isNotBlank() }?.let { map["pqv"] = it }
        flow?.takeIf { it.isNotBlank() }?.let { map["flow"] = it }
        method?.takeIf { it.isNotBlank() }?.let { map["encryption"] = it }
        obfs?.takeIf { it.isNotBlank() }?.let { map["obfs"] = it }
        obfsPassword?.takeIf { it.isNotBlank() }?.let { map["obfs-password"] = it }
        portHopping?.takeIf { it.isNotBlank() }?.let { map["mport"] = it }
        pinSha256?.takeIf { it.isNotBlank() }?.let { map["pinSHA256"] = it }
        return map
    }
}

/**
 * Represents a parsed sharing link (VLESS, VMess, Trojan, Shadowsocks, SOCKS, Hysteria2).
 */
sealed class ShareLink {
    abstract val host: String
    abstract val port: Int
    abstract val name: String?

    data class Vless(
        val uuid: String,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val encryption: String get() = params["encryption"]?.takeIf { it.isNotBlank() } ?: "none"
        val security: String get() = params["security"]?.takeIf { it.isNotBlank() } ?: "none"
        val transport: String get() = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp"
        val sni: String get() = params["sni"] ?: params["peer"] ?: params["host"] ?: host
        val flow: String get() = params["flow"] ?: ""
    }

    data class Vmess(
        val uuid: String,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val encryption: String get() = params["encryption"]?.takeIf { it.isNotBlank() } ?: "auto"
        val security: String get() = params["security"]?.takeIf { it.isNotBlank() } ?: "none"
        val transport: String get() = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp"
        val sni: String get() = params["sni"] ?: host
    }

    /**
     * Legacy VMess format (Base64 encoded JSON).
     */
    data class VmessLegacy(
        val id: String,
        val add: String,
        override val port: Int,
        val aid: Int,
        val scy: String?,
        val net: String?,
        val type: String?,
        val hostName: String?,
        val path: String?,
        val tls: String?,
        val sni: String?,
        val alpn: String?,
        val fp: String?,
        override val name: String?
    ) : ShareLink() {
        override val host: String get() = add
        val transport: String get() = net ?: "tcp"
        val encryption: String get() = scy ?: "auto"
        val security: String get() = tls ?: "none"
    }

    data class Trojan(
        val password: String,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val sni: String get() = params["sni"] ?: params["peer"] ?: params["host"] ?: host
        val security: String get() = params["security"]?.takeIf { it.isNotBlank() } ?: "tls"
        val transport: String get() = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp"
    }

    data class Shadowsocks(
        val method: String,
        val password: String,
        override val host: String,
        override val port: Int,
        val params: Map<String, String> = emptyMap(),
        override val name: String?
    ) : ShareLink() {
        val security: String get() = params["security"]?.takeIf { it.isNotBlank() } ?: "none"
        val transport: String get() = params["type"]?.takeIf { it.isNotBlank() } ?: "tcp"
    }

    data class Socks(
        val user: String?,
        val pass: String?,
        override val host: String,
        override val port: Int,
        override val name: String?
    ) : ShareLink()

    data class Hysteria2(
        val auth: String,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val sni: String get() = params["sni"] ?: host
    }

    /**
     * Returns a human-readable name for the server.
     * Falls back to host if the name is missing or blank.
     */
    fun getDisplayName(): String = name?.takeIf { it.isNotBlank() } ?: host

    /**
     * Returns a user-friendly protocol name.
     */
    fun getProtocolName(): String = when (this) {
        is Vless -> "VLESS"
        is Vmess -> "VMess"
        is VmessLegacy -> "VMess (Legacy)"
        is Trojan -> "Trojan"
        is Shadowsocks -> "Shadowsocks"
        is Socks -> "SOCKS5"
        is Hysteria2 -> "Hysteria 2"
    }

    fun toProxyNode(): ProxyNode {
        return when (this) {
            is Vless -> ProxyNode(
                protocol = "vless",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                uuid = uuid,
                method = encryption,
                network = transport,
                security = security.takeIf { it != "none" },
                sni = params["sni"],
                flow = flow,
                params = params
            )
            is Vmess -> ProxyNode(
                protocol = "vmess",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                uuid = uuid,
                method = encryption,
                network = transport,
                security = security.takeIf { it != "none" },
                sni = params["sni"],
                params = params
            )
            is VmessLegacy -> ProxyNode(
                protocol = "vmess",
                remarks = name ?: "none",
                server = add,
                serverPort = port,
                uuid = id,
                method = scy ?: "auto",
                network = net ?: "tcp",
                headerType = type,
                host = hostName,
                path = path,
                security = tls,
                sni = sni,
                fingerprint = fp,
                alpn = alpn
            )
            is Trojan -> ProxyNode(
                protocol = "trojan",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                password = password,
                network = transport,
                security = security,
                sni = sni,
                params = params
            )
            is Shadowsocks -> ProxyNode(
                protocol = "shadowsocks",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                method = method,
                password = password,
                network = transport,
                security = security.takeIf { it != "none" },
                params = params
            )
            is Socks -> ProxyNode(
                protocol = "socks",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                uuid = user,
                password = pass
            )
            is Hysteria2 -> ProxyNode(
                protocol = "hysteria2",
                remarks = name ?: "none",
                server = host,
                serverPort = port,
                password = auth,
                security = "tls",
                network = "hysteria",
                sni = sni,
                params = params
            )
        }
    }
}

/**
 * Parsed URI structure.
 */
private data class RawParsedUri(
    val scheme: String,
    val userInfo: String,
    val host: String,
    val port: Int,
    val query: Map<String, String>,
    val rawQuery: String,
    val fragment: String
)

/**
 * Parser for V2Ray/Xray style sharing links matching Python/v2rayNG specification.
 * Supported protocols: vless://, vmess://, trojan://, ss://, socks://, hysteria2://
 */
object ShareLinkParser {
    private const val MAX_VMESS_LEGACY_LENGTH = 20_000

    /**
     * Checks if a string is a supported proxy sharing link.
     */
    fun isShareLink(link: String): Boolean {
        val trimmed = link.trim().lowercase()
        return trimmed.startsWith("vless://") ||
                trimmed.startsWith("vmess://") ||
                trimmed.startsWith("trojan://") ||
                trimmed.startsWith("trojan-go://") ||
                trimmed.startsWith("ss://") ||
                trimmed.startsWith("socks://") ||
                trimmed.startsWith("socks5://") ||
                trimmed.startsWith("socks5h://") ||
                trimmed.startsWith("socks4://") ||
                trimmed.startsWith("hysteria2://") ||
                trimmed.startsWith("hy2://")
    }

    /**
     * Parses a raw sharing link string into a [ProxyNode] object.
     * @throws IllegalArgumentException if the link is malformed or unsupported.
     */
    fun parseNode(link: String): ProxyNode {
        val url = link.trim()
        if (url.isEmpty()) throw IllegalArgumentException("empty link")
        if (!url.contains("://")) throw IllegalArgumentException("no scheme")

        return when (val scheme = url.substringBefore("://").lowercase()) {
            "vless" -> parseVlessNode(url)
            "trojan", "trojan-go" -> parseTrojanNode(url)
            "vmess" -> parseVmessNode(url)
            "ss" -> parseSsNode(url)
            "hysteria2", "hy2" -> parseHysteria2Node(url)
            "socks", "socks5", "socks5h", "socks4" -> parseSocksNode(url)
            else -> throw IllegalArgumentException("unsupported scheme: $scheme")
        }
    }

    /**
     * Parses multi-line text into a list of [ProxyNode] objects.
     */
    fun parseNodes(text: String): List<ProxyNode> {
        return text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) null
            else try { parseNode(trimmed) } catch (_: Exception) { null }
        }
    }

    /**
     * Parses a raw sharing link string into a [ShareLink] object.
     * @throws IllegalArgumentException if the link is malformed or unsupported.
     */
    fun parse(link: String): ShareLink {
        return parseNode(link).toShareLink()
    }

    /**
     * Parses multi-line text into a list of [ShareLink] objects.
     */
    fun parseLinks(text: String): List<ShareLink> {
        return text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) null
            else try { parse(trimmed) } catch (_: Exception) { null }
        }
    }

    private fun parseVlessNode(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.scheme != "vless") throw IllegalArgumentException("not vless")

        val node = ProxyNode(
            protocol = "vless",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host.ifBlank { throw IllegalArgumentException("VLESS: Missing host") },
            serverPort = if (uri.port > 0) uri.port else 443,
            uuid = uri.userInfo.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("VLESS: Missing UUID"),
            method = uri.query["encryption"]?.takeIf { it.isNotBlank() } ?: "none",
            raw = url
        )
        node.applyQuery(uri.query)
        return node
    }

    private fun parseTrojanNode(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.scheme != "trojan" && uri.scheme != "trojan-go") {
            throw IllegalArgumentException("not trojan")
        }

        val node = ProxyNode(
            protocol = "trojan",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host.ifBlank { throw IllegalArgumentException("Trojan: Missing host") },
            serverPort = if (uri.port > 0) uri.port else 443,
            password = uri.userInfo.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Trojan: Missing password"),
            raw = url
        )

        if (uri.rawQuery.isBlank()) {
            node.network = "tcp"
            node.security = "tls"
            node.insecure = false
        } else {
            val qp = uri.query
            node.applyQuery(qp)
            node.security = qp["security"]?.takeIf { it.isNotBlank() } ?: "tls"
        }
        return node
    }

    private fun parseVmessNode(url: String): ProxyNode {
        if (!url.startsWith("vmess://", ignoreCase = true)) {
            throw IllegalArgumentException("not vmess")
        }

        val body = url.substring(8).trim()

        val beforeQuery = body.substringBefore("?")
        if ("?" in body && "@" in beforeQuery) {
            return parseVmessStd(url)
        }

        var rawPayload = body
        var remark = ""
        if ("#" in rawPayload) {
            remark = decodeUrl(rawPayload.substringAfter("#"))
            rawPayload = rawPayload.substringBefore("#")
        }

        require(rawPayload.length < MAX_VMESS_LEGACY_LENGTH) { "VMess Legacy: Payload too large" }

        val decoded = try {
            b64decodeStr(rawPayload)
        } catch (e: Exception) {
            throw IllegalArgumentException("VMess Base64 decode failed: ${e.message}")
        }

        val jsonObject = try {
            com.google.gson.JsonParser.parseString(decoded).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("VMess JSON decode failed: ${e.message}")
        }

        val add = jsonObject["add"]?.asString
        val portObj = jsonObject["port"]
        val port = when {
            portObj == null -> 0
            portObj.isJsonPrimitive && portObj.asJsonPrimitive.isNumber -> portObj.asInt
            else -> portObj.asString.toIntOrNull() ?: 0
        }
        val id = jsonObject["id"]?.asString
        val net = jsonObject["net"]?.asString

        if (add.isNullOrBlank() || port <= 0 || id.isNullOrBlank() || net.isNullOrBlank()) {
            throw IllegalArgumentException("VMess JSON missing required fields")
        }

        val ps = jsonObject["ps"]?.asString ?: ""
        val node = ProxyNode(
            protocol = "vmess",
            remarks = remark.ifBlank { ps },
            server = add,
            serverPort = port,
            uuid = id,
            method = jsonObject["scy"]?.asString?.takeIf { it.isNotBlank() } ?: "auto",
            network = net,
            headerType = jsonObject["type"]?.asString,
            host = jsonObject["host"]?.asString,
            path = jsonObject["path"]?.asString,
            security = jsonObject["tls"]?.asString?.takeIf { it.isNotBlank() },
            sni = jsonObject["sni"]?.asString,
            fingerprint = jsonObject["fp"]?.asString,
            alpn = jsonObject["alpn"]?.asString,
            insecure = jsonObject["insecure"]?.asString?.lowercase() in setOf("1", "true"),
            vcn = jsonObject["vcn"]?.asString,
            pcs = jsonObject["pcs"]?.asString,
            raw = url
        )

        val netLower = node.network.lowercase()
        if (netLower == "kcp") {
            node.seed = jsonObject["path"]?.asString
        } else if (netLower == "grpc") {
            node.mode = jsonObject["type"]?.asString
            node.serviceName = jsonObject["path"]?.asString
            node.authority = jsonObject["host"]?.asString
        }

        val map = LinkedHashMap<String, String>()
        for ((key, value) in jsonObject.entrySet()) {
            if (value.isJsonPrimitive) {
                map[key] = value.asString
            }
        }
        node.params = map

        return node
    }

    private fun parseVmessStd(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.rawQuery.isBlank()) {
            throw IllegalArgumentException("VMess std without query")
        }

        val node = ProxyNode(
            protocol = "vmess",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host.ifBlank { throw IllegalArgumentException("VMESS: Missing host") },
            serverPort = if (uri.port > 0) uri.port else 443,
            uuid = uri.userInfo.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("VMESS: Missing UUID"),
            method = "auto",
            raw = url
        )
        node.applyQuery(uri.query)
        return node
    }

    private fun parseSsNode(url: String): ProxyNode {
        if (!url.startsWith("ss://", ignoreCase = true)) {
            throw IllegalArgumentException("not ss")
        }

        return try {
            parseSsSip002(url)
        } catch (_: Exception) {
            parseSsLegacy(url)
        }
    }

    private fun parseSsSip002(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.host.isBlank() || uri.port <= 0 || uri.userInfo.isBlank()) {
            throw IllegalArgumentException("SS SIP002 invalid")
        }

        val userInfo = uri.userInfo
        var method: String? = null
        var password: String? = null

        if (":" in userInfo) {
            method = userInfo.substringBefore(":")
            password = userInfo.substringAfter(":")
        } else {
            val decoded = try { b64decodeStr(userInfo) } catch (_: Exception) { userInfo }
            if (":" in decoded) {
                method = decoded.substringBefore(":")
                password = decoded.substringAfter(":")
            }
        }

        if (method.isNullOrBlank()) {
            throw IllegalArgumentException("SS SIP002 invalid method")
        }

        val node = ProxyNode(
            protocol = "shadowsocks",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host,
            serverPort = uri.port,
            method = method,
            password = password,
            raw = url
        )

        if (uri.rawQuery.isNotBlank()) {
            val qp = uri.query
            node.params = qp
            val plugin = qp["plugin"] ?: ""
            if ("obfs=http" in plugin) {
                val pairs = mutableMapOf<String, String>()
                for (part in plugin.split(";")) {
                    if ("=" in part) {
                        pairs[part.substringBefore("=")] = part.substringAfter("=")
                    }
                }
                node.network = "tcp"
                node.headerType = "http"
                node.host = pairs["obfs-host"]
                node.path = pairs["path"]
            }
        }
        return node
    }

    private fun parseSsLegacy(url: String): ProxyNode {
        var body = url.substring(5)
        var remark = ""
        if ("#" in body) {
            remark = decodeUrl(body.substringAfter("#"))
            body = body.substringBefore("#")
        }

        val idx = body.indexOf("@")
        if (idx > 0) {
            try {
                val userpart = b64decodeStr(body.substring(0, idx))
                body = userpart + body.substring(idx)
            } catch (_: Exception) {
            }
        } else {
            try {
                body = b64decodeStr(body)
            } catch (_: Exception) {
            }
        }

        val regex = Regex("""^(.+?):(.*)@(.+?):(\d+)/?$""")
        val match = regex.find(body) ?: throw IllegalArgumentException("Shadowsocks: Missing host, port or cipher method")

        val method = match.groupValues[1].lowercase()
        val password = match.groupValues[2]
        var host = match.groupValues[3]
        val port = match.groupValues[4].toIntOrNull() ?: 0

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length - 1)
        }

        if (host.isBlank() || port <= 0 || method.isBlank()) {
            throw IllegalArgumentException("Shadowsocks: Missing host, port or cipher method")
        }

        return ProxyNode(
            protocol = "shadowsocks",
            remarks = remark.ifBlank { "none" },
            server = host,
            serverPort = port,
            method = method,
            password = password,
            raw = url
        )
    }

    private fun parseHysteria2Node(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.scheme != "hysteria2" && uri.scheme != "hy2") {
            throw IllegalArgumentException("not hysteria2")
        }

        val node = ProxyNode(
            protocol = "hysteria2",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host.ifBlank { throw IllegalArgumentException("Hysteria2: Missing host") },
            serverPort = if (uri.port > 0) uri.port else 443,
            password = uri.userInfo.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Hysteria2: Missing auth"),
            security = "tls",
            network = "hysteria",
            raw = url
        )

        if (uri.rawQuery.isNotBlank()) {
            val qp = uri.query
            node.applyQuery(qp)
            node.security = qp["security"]?.takeIf { it.isNotBlank() } ?: "tls"
            node.obfsPassword = qp["obfs-password"]
            node.obfs = qp["obfs"]
            node.portHopping = qp["mport"]
            node.pinSha256 = qp["pinSHA256"]
            if (!node.obfsPassword.isNullOrBlank() && node.obfs.isNullOrBlank()) {
                node.obfs = "salamander"
            }
        }
        return node
    }

    private fun parseSocksNode(url: String): ProxyNode {
        val uri = parseUri(url)
        if (uri.scheme != "socks" && uri.scheme != "socks5" && uri.scheme != "socks5h" && uri.scheme != "socks4") {
            throw IllegalArgumentException("not socks")
        }

        var user: String? = null
        var pass: String? = null
        val userInfo = uri.userInfo
        if (userInfo.isNotBlank()) {
            if (":" in userInfo) {
                user = userInfo.substringBefore(":")
                pass = userInfo.substringAfter(":")
            } else {
                user = userInfo
            }
        }

        return ProxyNode(
            protocol = "socks",
            remarks = uri.fragment.ifBlank { "none" },
            server = uri.host.ifBlank { throw IllegalArgumentException("SOCKS: Missing host") },
            serverPort = if (uri.port > 0) uri.port else 1080,
            uuid = user,
            password = pass,
            raw = url
        )
    }

    private fun parseUri(url: String): RawParsedUri {
        val trimmed = url.trim()
        val scheme = trimmed.substringBefore("://").lowercase()
        var remainder = trimmed.substringAfter("://")

        var fragment = ""
        if ("#" in remainder) {
            fragment = decodeUrl(remainder.substringAfter("#"))
            remainder = remainder.substringBefore("#")
        }

        var rawQuery = ""
        if ("?" in remainder) {
            rawQuery = remainder.substringAfter("?")
            remainder = remainder.substringBefore("?")
        }

        val query = getQueryParam(rawQuery)

        if ("/" in remainder) {
            remainder = remainder.substringBefore("/")
        }

        var userInfo = ""
        var hostPort = remainder
        if ("@" in remainder) {
            userInfo = decodeUrl(remainder.substringBeforeLast("@"))
            hostPort = remainder.substringAfterLast("@")
        }

        val host: String
        var port = 0
        if (hostPort.startsWith("[")) {
            val closeBracket = hostPort.indexOf("]")
            if (closeBracket != -1) {
                host = hostPort.substring(1, closeBracket)
                val after = hostPort.substring(closeBracket + 1)
                if (after.startsWith(":")) {
                    port = after.substring(1).toIntOrNull() ?: 0
                }
            } else {
                host = hostPort
            }
        } else {
            if (":" in hostPort) {
                host = hostPort.substringBefore(":")
                port = hostPort.substringAfter(":").toIntOrNull() ?: 0
            } else {
                host = hostPort
            }
        }

        return RawParsedUri(
            scheme = scheme,
            userInfo = userInfo,
            host = host,
            port = port,
            query = query,
            rawQuery = rawQuery,
            fragment = fragment
        )
    }

    private fun getQueryParam(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (part in rawQuery.split("&")) {
            if (part.isBlank()) continue
            if ("=" in part) {
                val k = part.substringBefore("=")
                val v = part.substringAfter("=")
                result[k] = decodeUrl(v)
            } else {
                result[part] = ""
            }
        }
        return result
    }

    private fun decodeUrl(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun b64decodeStr(input: String): String {
        var text = decodeUrl(input.trim()).replace("-", "+").replace("_", "/")
        val paddingNeeded = (4 - text.length % 4) % 4
        if (paddingNeeded > 0) {
            text += "=".repeat(paddingNeeded)
        }
        return try {
            String(Base64.getDecoder().decode(text), Charsets.UTF_8)
        } catch (_: Exception) {
            String(Base64.getUrlDecoder().decode(text), Charsets.UTF_8)
        }
    }
}
