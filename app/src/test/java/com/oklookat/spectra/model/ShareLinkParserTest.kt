package com.oklookat.spectra.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [ShareLinkParser] covering all supported protocols, ProxyNode features, and edge cases.
 */
class ShareLinkParserTest {

    @Test
    fun testParseVless() {
        val uuid = UUID.randomUUID().toString()
        val link = "vless://$uuid@example.com:443?encryption=none&security=tls&type=grpc#MyServer"
        val result = ShareLinkParser.parse(link) as ShareLink.Vless

        assertEquals(uuid, result.uuid)
        assertEquals("example.com", result.host)
        assertEquals(443, result.port)
        assertEquals("none", result.encryption)
        assertEquals("tls", result.security)
        assertEquals("grpc", result.transport)
        assertEquals("MyServer", result.getDisplayName())
    }

    @Test
    fun testVlessTransportsAndSecurity() {
        val uuid = "1386f85e-657b-4d6e-9d56-78badb75e1fd"

        // XHTTP / Reality
        val xhttpLink = "vless://$uuid@1.2.3.4:443?type=xhttp&mode=auto&path=/xhttp&security=reality&pbk=pubkey123&sni=example.com&fp=chrome&sid=sid123&spx=/spider&flow=xtls-rprx-vision#XHttpReality"
        val xhttpResult = ShareLinkParser.parse(xhttpLink) as ShareLink.Vless
        assertEquals("xhttp", xhttpResult.transport)
        assertEquals("reality", xhttpResult.security)
        assertEquals("xtls-rprx-vision", xhttpResult.flow)
        assertEquals("XHttpReality", xhttpResult.getDisplayName())

        // HTTPUpgrade / TLS
        val huLink = "vless://$uuid@1.2.3.4:443?type=httpupgrade&path=/hu&host=hu.example.com&security=tls&alpn=h2,http/1.1#HU"
        val huResult = ShareLinkParser.parse(huLink) as ShareLink.Vless
        assertEquals("httpupgrade", huResult.transport)
        assertEquals("tls", huResult.security)
        assertEquals("HU", huResult.getDisplayName())

        // gRPC / authority
        val grpcLink = "vless://$uuid@1.2.3.4:443?type=grpc&serviceName=my-service&authority=auth.example.com&security=tls#GRPC"
        val grpcResult = ShareLinkParser.parse(grpcLink) as ShareLink.Vless
        assertEquals("grpc", grpcResult.transport)
        assertEquals("my-service", grpcResult.params["serviceName"])
        assertEquals("auth.example.com", grpcResult.params["authority"])

        // KCP
        val kcpLink = "vless://$uuid@1.2.3.4:443?type=kcp&headerType=wechat&seed=myseed#KCP"
        val kcpResult = ShareLinkParser.parse(kcpLink) as ShareLink.Vless
        assertEquals("kcp", kcpResult.transport)
        assertEquals("wechat", kcpResult.params["headerType"])
        assertEquals("myseed", kcpResult.params["seed"])
    }

    @Test
    fun testVlessDefaults() {
        val uuid = UUID.randomUUID().toString()
        val link = "vless://$uuid@example.com:443"
        val result = ShareLinkParser.parse(link) as ShareLink.Vless

        assertEquals("none", result.encryption)
        assertEquals("none", result.security)
        assertEquals("tcp", result.transport)
        assertEquals("example.com", result.sni)
    }

    @Test
    fun testParseVmessNew() {
        val uuid = UUID.randomUUID().toString()
        val link = "vmess://$uuid@example.com:443?type=ws&security=tls#VmessNew"
        val result = ShareLinkParser.parse(link) as ShareLink.Vmess

        assertEquals(uuid, result.uuid)
        assertEquals("example.com", result.host)
        assertEquals("ws", result.transport)
        assertEquals("tls", result.security)
        assertEquals("auto", result.encryption)
        assertEquals("VmessNew", result.getDisplayName())
    }

    @Test
    fun testParseVmessLegacyFull() {
        val json = """
        {
            "v": "2",
            "ps": "MyLegacyServer",
            "add": "1.2.3.4",
            "port": "32000",
            "id": "1386f85e-657b-4d6e-9d56-78badb75e1fd",
            "aid": "100",
            "net": "tcp",
            "type": "none",
            "tls": "tls",
            "sni": "www.example.com",
            "fp": "chrome"
        }
        """.trimIndent()

        val b64 = java.util.Base64.getEncoder().encodeToString(json.toByteArray())
        val link = "vmess://$b64"

        val result = ShareLinkParser.parse(link) as ShareLink.VmessLegacy
        assertEquals("MyLegacyServer", result.name)
        assertEquals("1.2.3.4", result.add)
        assertEquals(32000, result.port)
        assertEquals("1386f85e-657b-4d6e-9d56-78badb75e1fd", result.id)
        assertEquals(100, result.aid)
        assertEquals("tcp", result.transport)
        assertEquals("tls", result.security)
        assertEquals("www.example.com", result.sni)
        assertEquals("chrome", result.fp)
    }

    @Test
    fun testParseShadowsocksUriWithBase64UserInfo() {
        val userInfo = java.util.Base64.getEncoder().encodeToString("aes-256-gcm:password".toByteArray())
        val link = "ss://$userInfo@example.com:8388#MySS"

        val result = ShareLinkParser.parse(link) as ShareLink.Shadowsocks
        assertEquals("aes-256-gcm", result.method)
        assertEquals("password", result.password)
        assertEquals("example.com", result.host)
        assertEquals("MySS", result.getDisplayName())
    }

    @Test
    fun testParseSocks() {
        val link = "socks5://user:pass@1.2.3.4:1080#MySocks"
        val result = ShareLinkParser.parse(link) as ShareLink.Socks

        assertEquals("user", result.user)
        assertEquals("pass", result.pass)
        assertEquals("1.2.3.4", result.host)
        assertEquals(1080, result.port)
        assertEquals("MySocks", result.getDisplayName())
    }

    @Test
    fun testParseHysteria2() {
        val link = "hysteria2://mytoken@1.2.3.4:443?insecure=1&sni=example.com&obfs=salamander&obfs-password=secret#MyHy2"
        val result = ShareLinkParser.parse(link) as ShareLink.Hysteria2

        assertEquals("mytoken", result.auth)
        assertEquals("1.2.3.4", result.host)
        assertEquals(443, result.port)
        assertEquals("example.com", result.sni)
        assertEquals("salamander", result.params["obfs"])
        assertEquals("secret", result.params["obfs-password"])
        assertEquals("MyHy2", result.getDisplayName())
    }

    @Test
    fun testParseNodeVlessRealityVision() {
        val link = "vless://5783a3e7-e373-51cd-8642-c83782b807c5@example.com:443" +
                "?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome" +
                "&pbk=PUBLIC_KEY&sid=abcd1234&type=tcp&flow=xtls-rprx-vision" +
                "&spx=%2F&pqv=test#Reality-Vision"

        val node = ShareLinkParser.parseNode(link)
        assertEquals("vless", node.protocol)
        assertEquals("Reality-Vision", node.remarks)
        assertEquals("example.com", node.server)
        assertEquals(443, node.serverPort)
        assertEquals("5783a3e7-e373-51cd-8642-c83782b807c5", node.uuid)
        assertEquals("none", node.method)
        assertEquals("tcp", node.network)
        assertEquals("reality", node.security)
        assertEquals("www.microsoft.com", node.sni)
        assertEquals("chrome", node.fingerprint)
        assertEquals("PUBLIC_KEY", node.publicKey)
        assertEquals("abcd1234", node.shortId)
        assertEquals("xtls-rprx-vision", node.flow)
        assertEquals("/", node.spiderX)
        assertEquals("test", node.pqv)

        val dict = node.toDict()
        assertEquals("vless", dict["protocol"])
        assertEquals("example.com", dict["server"])
        assertEquals(443, dict["server_port"])
        assertEquals("chrome", dict["fingerprint"])
    }

    @Test
    fun testParseNodeHysteria2ObfsPasswordDefaulting() {
        val link = "hysteria2://mytoken@hy2.example.com:443?sni=hy2.example.com&insecure=0&obfs-password=gawrgura&mport=1000-2000#HY2"

        val node = ShareLinkParser.parseNode(link)
        assertEquals("hysteria2", node.protocol)
        assertEquals("HY2", node.remarks)
        assertEquals("hy2.example.com", node.server)
        assertEquals(443, node.serverPort)
        assertEquals("mytoken", node.password)
        assertEquals("salamander", node.obfs)
        assertEquals("gawrgura", node.obfsPassword)
        assertEquals("1000-2000", node.portHopping)
    }

    @Test
    fun testParseNodesAndParseLinksMultiLine() {
        val text = """
            # Sample subscription
            vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@1.1.1.1:443?type=tcp&security=tls#Node1
            trojan://secret@2.2.2.2:443?security=tls#Node2
            invalid://junk
            
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@3.3.3.3:8388#Node3
        """.trimIndent()

        val nodes = ShareLinkParser.parseNodes(text)
        assertEquals(3, nodes.size)
        assertEquals("vless", nodes[0].protocol)
        assertEquals("trojan", nodes[1].protocol)
        assertEquals("shadowsocks", nodes[2].protocol)

        val shareLinks = ShareLinkParser.parseLinks(text)
        assertEquals(3, shareLinks.size)
        assertEquals("Node1", shareLinks[0].getDisplayName())
        assertEquals("Node2", shareLinks[1].getDisplayName())
        assertEquals("Node3", shareLinks[2].getDisplayName())
    }

    @Test
    fun testIsShareLink() {
        assertTrue(ShareLinkParser.isShareLink("vless://test@host:443"))
        assertTrue(ShareLinkParser.isShareLink("vmess://test@host:443"))
        assertTrue(ShareLinkParser.isShareLink("trojan://pass@host:443"))
        assertTrue(ShareLinkParser.isShareLink("ss://pass@host:8388"))
        assertTrue(ShareLinkParser.isShareLink("socks5://host:1080"))
        assertTrue(ShareLinkParser.isShareLink("hysteria2://token@host:443"))
    }

    @Test
    fun testEmptyFragmentHandling() {
        val link = "vless://${UUID.randomUUID()}@example.com:443#"
        val result = ShareLinkParser.parse(link)

        assertNull(result.name)
        assertEquals("example.com", result.getDisplayName())
    }

    @Test
    fun testBlankFragmentHandling() {
        val link = "vless://${UUID.randomUUID()}@example.com:443#%20%20"
        val result = ShareLinkParser.parse(link)

        assertNull(result.name)
        assertEquals("example.com", result.getDisplayName())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMissingUuid() {
        val link = "vless://@example.com:443"
        ShareLinkParser.parse(link)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMissingHost() {
        val link = "trojan://pass@:443"
        ShareLinkParser.parse(link)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidProtocol() {
        ShareLinkParser.parse("ftp://example.com")
    }
}
