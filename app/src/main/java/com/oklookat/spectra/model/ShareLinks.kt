package com.oklookat.spectra.model

import com.google.gson.Gson
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID

/**
 * Represents a parsed sharing link (VLESS, VMess, Trojan, Shadowsocks).
 */
sealed class ShareLink {
    abstract val host: String
    abstract val port: Int
    abstract val name: String?

    data class Vless(
        val uuid: UUID,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val encryption: String get() = params["encryption"] ?: "none"
        val security: String get() = params["security"] ?: "none"
        val transport: String get() = params["type"] ?: "tcp"
        val sni: String get() = params["sni"] ?: host
        val flow: String get() = params["flow"] ?: ""
    }

    data class Vmess(
        val uuid: UUID,
        override val host: String,
        override val port: Int,
        val params: Map<String, String>,
        override val name: String?
    ) : ShareLink() {
        val encryption: String get() = params["encryption"] ?: "auto"
        val security: String get() = params["security"] ?: "none"
        val transport: String get() = params["type"] ?: "tcp"
        val sni: String get() = params["sni"] ?: host
    }

    /**
     * Legacy VMess format (Base64 encoded JSON).
     */
    data class VmessLegacy(
        val id: UUID,
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
        val sni: String get() = params["sni"] ?: host
    }

    data class Shadowsocks(
        val method: String,
        val password: String,
        override val host: String,
        override val port: Int,
        override val name: String?
    ) : ShareLink()

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
    }
}

/**
 * Internal DTO for VMess Legacy JSON parsing.
 */
private data class VmessJson(
    val v: String?,
    val ps: String?,
    val add: String?,
    val port: Any?,
    val id: String?,
    val aid: Any?,
    val scy: String?,
    val net: String?,
    val type: String?,
    val host: String?,
    val path: String?,
    val tls: String?,
    val sni: String?,
    val alpn: String?,
    val fp: String?
)

/**
 * Parser for V2Ray/Xray style sharing links.
 * Supported protocols: vless://, vmess://, trojan://, ss://
 */
object ShareLinkParser {
    private const val MAX_VMESS_LEGACY_LENGTH = 20_000
    private val gson = Gson()

    /**
     * Parses a raw sharing link string into a [ShareLink] object.
     * @throws IllegalArgumentException if the link is malformed or unsupported.
     */
    fun parse(link: String): ShareLink {
        val trimmed = link.trim()
        return try {
            when {
                trimmed.startsWith("vless://", true) -> parseVless(trimmed)
                trimmed.startsWith("vmess://", true) -> parseVmess(trimmed)
                trimmed.startsWith("trojan://", true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", true) -> parseShadowsocks(trimmed)
                else -> throw IllegalArgumentException("Unsupported protocol: ${trimmed.substringBefore("://")}")
            }
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException("Invalid link format: ${e.message}", e)
        }
    }

    private fun parseQuery(q: String?): Map<String, String> =
        q?.split("&")?.mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0] to URLDecoder.decode(p[1], "UTF-8") else null
        }?.toMap() ?: emptyMap()

    private fun parseName(fragment: String?): String? =
        fragment?.let {
            try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
        }?.takeIf { it.isNotBlank() }

    private fun parseVless(link: String): ShareLink.Vless {
        val uri = URI(link)
        val host = uri.host ?: throw IllegalArgumentException("VLESS: Missing host")
        val port = uri.port.takeIf { it != -1 } ?: throw IllegalArgumentException("VLESS: Missing port")
        val uuidStr = uri.userInfo ?: throw IllegalArgumentException("VLESS: Missing UUID")
        val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) { throw IllegalArgumentException("VLESS: Invalid UUID format") }

        val params = parseQuery(uri.query)
        val name = parseName(uri.fragment)
        return ShareLink.Vless(uuid, host, port, params, name)
    }

    private fun parseTrojan(link: String): ShareLink.Trojan {
        val uri = URI(link)
        val host = uri.host ?: throw IllegalArgumentException("Trojan: Missing host")
        val port = uri.port.takeIf { it != -1 } ?: throw IllegalArgumentException("Trojan: Missing port")
        val password = uri.userInfo ?: throw IllegalArgumentException("Trojan: Missing password")
        val params = parseQuery(uri.query)
        val name = parseName(uri.fragment)
        return ShareLink.Trojan(password, host, port, params, name)
    }

    private fun parseVmess(link: String): ShareLink {
        val payload = link.removePrefix("vmess://")
        return if (payload.contains("@")) {
            val uri = URI(link)
            val host = uri.host ?: throw IllegalArgumentException("VMESS: Missing host")
            val port = uri.port.takeIf { it != -1 } ?: throw IllegalArgumentException("VMESS: Missing port")
            val uuidStr = uri.userInfo ?: throw IllegalArgumentException("VMESS: Missing UUID")
            val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) { throw IllegalArgumentException("VMESS: Invalid UUID format") }

            val params = parseQuery(uri.query)
            val name = parseName(uri.fragment)
            return ShareLink.Vmess(uuid, host, port, params, name)
        } else {
            parseVmessLegacy(payload)
        }
    }

    private fun parseVmessLegacy(b64: String): ShareLink.VmessLegacy {
        require(b64.length < MAX_VMESS_LEGACY_LENGTH) { "VMess Legacy: Payload too large" }
        val decoded = try {
            String(Base64.getDecoder().decode(b64))
        } catch (_: Exception) {
            throw IllegalArgumentException("VMess Legacy: Invalid Base64")
        }

        val data = gson.fromJson(decoded, VmessJson::class.java)
            ?: throw IllegalArgumentException("VMess Legacy: Invalid JSON")

        val idStr = data.id ?: throw IllegalArgumentException("VMess Legacy: Missing id")
        val id = try { UUID.fromString(idStr) } catch (_: Exception) { throw IllegalArgumentException("VMess Legacy: Invalid id format") }

        val port = when (val p = data.port) {
            is Number -> p.toInt()
            is String -> p.toIntOrNull() ?: 0
            else -> 0
        }

        val aid = when (val a = data.aid) {
            is Number -> a.toInt()
            is String -> a.toIntOrNull() ?: 0
            else -> 0
        }

        return ShareLink.VmessLegacy(
            id = id,
            add = data.add ?: throw IllegalArgumentException("VMess Legacy: Missing address"),
            port = port,
            aid = aid,
            scy = data.scy,
            net = data.net,
            type = data.type,
            hostName = data.host,
            path = data.path,
            tls = data.tls,
            sni = data.sni,
            alpn = data.alpn,
            fp = data.fp,
            name = parseName(data.ps)
        )
    }

    private fun parseShadowsocks(link: String): ShareLink.Shadowsocks {
        val body = link.removePrefix("ss://")
        val fragment = body.substringAfter("#", "").takeIf { it.isNotEmpty() }
        val content = body.substringBefore("#")

        return if (!content.contains("@")) {
            val decoded = try {
                String(Base64.getDecoder().decode(content))
            } catch (_: Exception) {
                throw IllegalArgumentException("Shadowsocks: Invalid Base64")
            }
            parseShadowsocksInternal("ss://$decoded", parseName(fragment))
        } else {
            val uri = URI(link)
            val userInfo = uri.userInfo ?: ""
            val finalUserInfo = if (!userInfo.contains(":")) {
                try { String(Base64.getDecoder().decode(userInfo)) } catch (_: Exception) { userInfo }
            } else userInfo
            parseShadowsocksInternal("ss://$finalUserInfo@${uri.host}:${uri.port}", parseName(fragment) ?: parseName(uri.fragment))
        }
    }

    private fun parseShadowsocksInternal(link: String, name: String?): ShareLink.Shadowsocks {
        val uri = URI(link)
        val userInfo = uri.userInfo ?: throw IllegalArgumentException("Shadowsocks: Missing user info")
        val parts = userInfo.split(":", limit = 2)
        if (parts.size < 2) throw IllegalArgumentException("Shadowsocks: Invalid user info format")

        val host = uri.host ?: throw IllegalArgumentException("Shadowsocks: Missing host")
        val port = uri.port.takeIf { it != -1 } ?: throw IllegalArgumentException("Shadowsocks: Missing port")

        return ShareLink.Shadowsocks(parts[0], parts[1], host, port, name)
    }
}
