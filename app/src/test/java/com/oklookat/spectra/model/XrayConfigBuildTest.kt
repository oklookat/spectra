package com.oklookat.spectra.model

import org.junit.Assert.assertFalse
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
    fun testVlessXhttpAndRealityAndFlow() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=xhttp&mode=auto&path=/xhttp&security=reality&pbk=publicKey123&sid=shortId456&spx=/spider&flow=xtls-rprx-vision#XHttpTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"protocol\": \"vless\""))
        assertTrue(fullConfig.contains("\"flow\": \"xtls-rprx-vision\""))
        assertTrue(fullConfig.contains("\"xhttpSettings\""))
        assertTrue(fullConfig.contains("\"path\": \"/xhttp\""))
        assertTrue(fullConfig.contains("\"mode\": \"auto\""))
        assertTrue(fullConfig.contains("\"publicKey\": \"publicKey123\""))
        assertTrue(fullConfig.contains("\"spiderX\": \"/spider\""))
    }

    @Test
    fun testVlessHttpUpgradeAndTls() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=httpupgrade&path=/hu&host=hu.example.com&security=tls&alpn=h2,http/1.1&fp=firefox#HUTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"protocol\": \"vless\""))
        assertTrue(fullConfig.contains("\"httpupgradeSettings\""))
        assertTrue(fullConfig.contains("\"path\": \"/hu\""))
        assertTrue(fullConfig.contains("\"host\": \"hu.example.com\""))
        assertTrue(fullConfig.contains("\"tlsSettings\""))
        assertTrue(fullConfig.contains("\"fingerprint\": \"firefox\""))
    }

    @Test
    fun testVlessGrpcAuthority() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=grpc&serviceName=svc&authority=auth.example.com&security=tls#GrpcTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"grpcSettings\""))
        assertTrue(fullConfig.contains("\"serviceName\": \"svc\""))
        assertTrue(fullConfig.contains("\"authority\": \"auth.example.com\""))
    }

    @Test
    fun testVlessKcp() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=kcp&headerType=wechat&seed=myseed#KcpTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"kcpSettings\""))
        assertTrue(fullConfig.contains("\"type\": \"wechat\""))
        assertTrue(fullConfig.contains("\"seed\": \"myseed\""))
    }

    @Test
    fun testVlessTcpHeaderHttp() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=tcp&headerType=http&path=/index.html&host=fake.com#TcpHttpTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"tcpSettings\""))
        assertTrue(fullConfig.contains("\"type\": \"http\""))
        assertTrue(fullConfig.contains("/index.html"))
        assertTrue(fullConfig.contains("fake.com"))
    }

    @Test
    fun testSocksConfigBuild() {
        val link = "socks5://user:pass@example.com:1080#SocksTest"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"protocol\": \"socks\""))
        assertTrue(fullConfig.contains("\"user\": \"user\""))
        assertTrue(fullConfig.contains("\"pass\": \"pass\""))
    }

    @Test
    fun testHysteria2ConfigBuild() {
        val link = "hysteria2://mytoken@example.com:443?insecure=1&sni=example.com&obfs=salamander&obfs-password=secret#Hy2Test"
        val shareLink = ShareLinkParser.parse(link)

        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val fullConfig = XrayConfigBuild.buildFullConfig(outbound)

        assertTrue(fullConfig.contains("\"protocol\": \"hysteria\""))
        assertTrue(fullConfig.contains("\"auth\": \"mytoken\""))
        assertTrue(fullConfig.contains("\"finalmask\""))
        assertTrue(fullConfig.contains("\"salamander\""))
        assertTrue(fullConfig.contains("\"password\": \"secret\""))
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

    @Test
    fun testCreateWifiBypassConfig() {
        val rawConfig = """
        {
            "routing": {
                "rules": [
                    { "type": "field", "domain": ["example.com"], "outboundTag": "proxy-out" }
                ]
            }
        }
        """.trimIndent()

        val bypassed = XrayConfigBuild.createWifiBypassConfig(rawConfig)

        assertTrue(bypassed.contains("\"outboundTag\": \"direct-out\""))
    }

    @Test
    fun testPrepareConfigForPing() {
        val rawConfig = """
        {
            "inbounds": [
                {
                    "protocol": "tun",
                    "tag": "tun-in"
                }
            ]
        }
        """.trimIndent()

        val pingConfig = XrayConfigBuild.prepareConfigForPing(rawConfig)

        assertTrue(pingConfig.contains("\"inbounds\": []"))
    }

    @Test
    fun testCustomDnsAndRoutingRulesBuild() {
        val link = "vless://1386f85e-657b-4d6e-9d56-78badb75e1fd@example.com:443?type=tcp&security=tls#TestCustom"
        val shareLink = ShareLinkParser.parse(link)
        val outbound = XrayConfigBuild.fromShareLink(shareLink)
        requireNotNull(outbound)

        val dnsConfig = CustomDnsConfig(
            queryStrategy = "PreferIPv4",
            servers = listOf(
                DnsServerConfig(address = "https://dns.adguard-dns.com/dns-query", enabled = true),
                DnsServerConfig(address = "1.1.1.1", enabled = true)
            )
        )

        val routingConfig = CustomRoutingConfig(
            domainStrategy = "IPIfNonMatch",
            rules = listOf(
                RoutingRuleConfig(
                    name = "Bypass RU",
                    outboundTag = "direct-out",
                    domains = listOf("geosite:ru"),
                    ip = listOf("geoip:ru")
                ),
                RoutingRuleConfig(
                    name = "Block Ads",
                    outboundTag = "block-out",
                    domains = listOf("geosite:category-ads-all")
                )
            )
        )

        val fullConfig = XrayConfigBuild.buildFullConfig(
            proxyOutbound = outbound,
            dnsConfig = dnsConfig,
            routingConfig = routingConfig
        )

        assertTrue(fullConfig.contains("\"queryStrategy\": \"PreferIPv4\""))
        assertTrue(fullConfig.contains("https://dns.adguard-dns.com/dns-query"))
        assertTrue(fullConfig.contains("\"domainStrategy\": \"IPIfNonMatch\""))
        assertTrue(fullConfig.contains("geosite:ru"))
        assertTrue(fullConfig.contains("geosite:category-ads-all"))
        assertTrue(fullConfig.contains("\"outboundTag\": \"block-out\""))
    }

    @Test
    fun testGlobalLogSettingsOverride() {
        val rawConfig = """
        {
            "log": {
                "loglevel": "info",
                "dnsLog": true,
                "maskAddress": "full"
            },
            "inbounds": []
        }
        """.trimIndent()

        val prepared = XrayConfigBuild.prepareConfigForAndroid(
            configJson = rawConfig,
            logLevel = "none",
            dnsLog = false,
            maskAddress = ""
        )

        assertTrue(prepared.contains("\"loglevel\": \"none\""))
        assertTrue(prepared.contains("\"dnsLog\": false"))
        assertFalse(prepared.contains("\"maskAddress\""))
    }
}
