package com.oklookat.spectra.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class XrayConfigBuildTest {

    @Test
    fun testVlessConfigBuild() {
        val uuid = UUID.randomUUID()
        val link = "vless://$uuid@example.com:443?encryption=none&security=reality&type=grpc&fp=chrome&pbk=publicKey123&sid=shortId456#VlessTest"
        val shareLink = ShareLinkParser.parse(link)
        
        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)
        
        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)
        
        assertTrue(fullConfig.contains("\"protocol\": \"vless\""))
        assertTrue(fullConfig.contains("\"vnext\""))
        assertTrue(fullConfig.contains("\"publicKey\": \"publicKey123\""))
        assertTrue(fullConfig.contains("\"shortId\": \"shortId456\""))
        assertTrue(fullConfig.contains("\"grpcSettings\""))
    }

    @Test
    fun testVmessConfigBuild() {
        val uuid = UUID.randomUUID()
        val link = "vmess://$uuid@example.com:443?type=ws&security=tls#VmessTest"
        val shareLink = ShareLinkParser.parse(link)
        
        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)
        
        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)
        
        assertTrue(fullConfig.contains("\"protocol\": \"vmess\""))
        assertTrue(fullConfig.contains("\"vnext\""))
        assertTrue(fullConfig.contains("\"wsSettings\""))
    }

    @Test
    fun testTrojanConfigBuild() {
        val link = "trojan://secretpassword@example.com:443?security=tls&type=tcp&sni=sni.example.com#TrojanTest"
        val shareLink = ShareLinkParser.parse(link)
        
        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)
        
        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)
        
        assertTrue(fullConfig.contains("\"protocol\": \"trojan\""))
        assertTrue(fullConfig.contains("\"servers\""))
        assertTrue(fullConfig.contains("\"password\": \"secretpassword\""))
    }

    @Test
    fun testShadowsocksConfigBuild() {
        val userInfo = java.util.Base64.getEncoder().encodeToString("aes-256-gcm:pass123".toByteArray())
        val link = "ss://$userInfo@example.com:8388#SSTest"
        val shareLink = ShareLinkParser.parse(link)
        
        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)
        
        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)
        
        assertTrue(fullConfig.contains("\"protocol\": \"shadowsocks\""))
        assertTrue(fullConfig.contains("\"servers\""))
        assertTrue(fullConfig.contains("\"method\": \"aes-256-gcm\""))
        assertTrue(fullConfig.contains("\"password\": \"pass123\""))
    }

    @Test
    fun testPrepareConfigForAndroid() {
        val rawConfig = """
        {
            "inbounds": [
                {
                    "protocol": "tun",
                    "tag": "tun-in",
                    "settings": { "mtu": 9000 },
                    "sniffing": {
                        "enabled": true,
                        "destOverride": ["http", "tls", "fakedns"]
                    }
                }
            ]
        }
        """.trimIndent()

        val prepared = XrayConfigBuild.prepareConfigForAndroid(rawConfig)

        assertTrue(prepared.contains("\"protocol\": \"tun\""))
        assertTrue(prepared.contains("\"name\": \"tun0\""))
        assertTrue(prepared.contains("\"fakedns\""))
    }
}
