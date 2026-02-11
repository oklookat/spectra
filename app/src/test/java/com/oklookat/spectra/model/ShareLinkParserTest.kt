package com.oklookat.spectra.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [ShareLinkParser] covering all supported protocols and edge cases.
 */
class ShareLinkParserTest {

    @Test
    fun testParseVless() {
        val uuid = UUID.randomUUID()
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
    fun testVlessDefaults() {
        val uuid = UUID.randomUUID()
        val link = "vless://$uuid@example.com:443"
        val result = ShareLinkParser.parse(link) as ShareLink.Vless

        assertEquals("none", result.encryption)
        assertEquals("none", result.security)
        assertEquals("tcp", result.transport)
        assertEquals("example.com", result.sni)
    }

    @Test
    fun testParseVmessNew() {
        val uuid = UUID.randomUUID()
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
        // Based on the provided specification
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
        assertEquals(UUID.fromString("1386f85e-657b-4d6e-9d56-78badb75e1fd"), result.id)
        assertEquals(100, result.aid)
        assertEquals("tcp", result.transport)
        assertEquals("tls", result.security)
        assertEquals("www.example.com", result.sni)
        assertEquals("chrome", result.fp)
    }

    @Test
    fun testParseShadowsocksUriWithBase64UserInfo() {
        // userInfo: aes-256-gcm:password -> YWVzLTI1Ni1nY206cGFzc3dvcmQ=
        val userInfo = java.util.Base64.getEncoder().encodeToString("aes-256-gcm:password".toByteArray())
        val link = "ss://$userInfo@example.com:8388#MySS"
        
        val result = ShareLinkParser.parse(link) as ShareLink.Shadowsocks
        assertEquals("aes-256-gcm", result.method)
        assertEquals("password", result.password)
        assertEquals("example.com", result.host)
        assertEquals("MySS", result.getDisplayName())
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
    fun testInvalidUuid() {
        val link = "vless://invalid-uuid@example.com:443"
        ShareLinkParser.parse(link)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMissingPort() {
        val link = "trojan://pass@example.com"
        ShareLinkParser.parse(link)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidProtocol() {
        ShareLinkParser.parse("ftp://example.com")
    }
}
