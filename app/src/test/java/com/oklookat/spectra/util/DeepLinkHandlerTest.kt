package com.oklookat.spectra.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DeepLinkHandlerTest {

    @Test
    fun testSpectraLocalLinkParsing() {
        val queryStr = "name=TestProfile&autoupdate=true&autoupdateinterval=30&url=https://example.com/config.json?a=1&b=2"
        val base64Data = Base64.getUrlEncoder().encodeToString(queryStr.toByteArray())
        val fullUrl = "https://spectra.local/import?data=$base64Data"

        val result = DeepLinkHandler.handleUrlString(fullUrl)
        assertTrue(result is DeepLinkHandler.DeepLinkResult.Profile)
        val profileResult = result as DeepLinkHandler.DeepLinkResult.Profile

        assertEquals("TestProfile", profileResult.pending.name)
        assertEquals("https://example.com/config.json?a=1&b=2", profileResult.pending.url)
        assertEquals(true, profileResult.pending.autoUpdate)
        assertEquals(30, profileResult.pending.interval)
    }

    @Test
    fun testSpectraLocalGroupLinkParsing() {
        val queryStr = "type=group&name=TestGroup&autoupdate=false&autoupdateinterval=60&url=https://example.com/group.txt"
        val base64Data = Base64.getEncoder().encodeToString(queryStr.toByteArray())
        val fullUrl = "http://spectra.local/import?data=$base64Data"

        val result = DeepLinkHandler.handleUrlString(fullUrl)
        assertTrue(result is DeepLinkHandler.DeepLinkResult.Group)
        val groupResult = result as DeepLinkHandler.DeepLinkResult.Group

        assertEquals("TestGroup", groupResult.pending.name)
        assertEquals("https://example.com/group.txt", groupResult.pending.url)
        assertEquals(false, groupResult.pending.autoUpdate)
        assertEquals(60, groupResult.pending.interval)
    }
}
