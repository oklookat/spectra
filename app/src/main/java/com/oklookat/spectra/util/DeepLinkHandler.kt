package com.oklookat.spectra.util

import android.content.Intent
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile
import java.util.Base64

object DeepLinkHandler {
    sealed class DeepLinkResult {
        data class Profile(val pending: PendingProfile) : DeepLinkResult()
        data class Group(val pending: PendingGroup) : DeepLinkResult()
        data object Invalid : DeepLinkResult()
        data object None : DeepLinkResult()
    }

    fun handle(intent: Intent?): DeepLinkResult {
        val urlString = intent?.dataString ?: return DeepLinkResult.None
        return handleUrlString(urlString)
    }

    fun handleUrlString(urlString: String?): DeepLinkResult {
        if (urlString.isNullOrBlank()) return DeepLinkResult.None
        return parseLinkString(urlString.trim())
    }

    private fun parseLinkString(urlString: String): DeepLinkResult {
        try {
            val lower = urlString.lowercase()
            if (!lower.contains("spectra.local")) return DeepLinkResult.None

            val dataKey = "data="
            val dataIdx = urlString.indexOf(dataKey)
            if (dataIdx == -1) return DeepLinkResult.Invalid

            val rawEncoded = urlString.substring(dataIdx + dataKey.length).substringBefore("&").trim()
            if (rawEncoded.isEmpty()) return DeepLinkResult.Invalid

            val bytes = try {
                Base64.getUrlDecoder().decode(rawEncoded)
            } catch (_: Exception) {
                try {
                    Base64.getDecoder().decode(rawEncoded)
                } catch (_: Exception) {
                    return DeepLinkResult.Invalid
                }
            }

            val decodedData = String(bytes, Charsets.UTF_8)
            val params = parseDecodedQuery(decodedData)

            val type = params["type"] ?: "profile"
            val name = params["name"] ?: ""
            val url = params["url"] ?: ""
            val autoUpdate = params["autoupdate"]?.toBoolean() ?: false
            val interval = params["autoupdateinterval"]?.toIntOrNull() ?: 1440

            if (name.isNotEmpty() && url.isNotEmpty()) {
                return if (type == "group") {
                    DeepLinkResult.Group(PendingGroup(name, url, autoUpdate, interval))
                } else {
                    DeepLinkResult.Profile(PendingProfile(name, url, autoUpdate, interval))
                }
            }
        } catch (_: Exception) {
            return DeepLinkResult.Invalid
        }
        return DeepLinkResult.Invalid
    }

    private fun parseDecodedQuery(decoded: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = decoded.split("&")
        var i = 0
        while (i < pairs.size) {
            val pair = pairs[i]
            val split = pair.split("=", limit = 2)
            if (split.size == 2) {
                val key = split[0].trim()
                var value = split[1].trim()
                if (key == "url") {
                    val urlParams = mutableListOf<String>()
                    var j = i + 1
                    while (j < pairs.size) {
                        val nextPair = pairs[j]
                        if (nextPair.startsWith("type=") || nextPair.startsWith("name=") ||
                            nextPair.startsWith("autoupdate=") || nextPair.startsWith("autoupdateinterval=")
                        ) {
                            break
                        }
                        urlParams.add(nextPair)
                        j++
                    }
                    if (urlParams.isNotEmpty()) {
                        value += "&" + urlParams.joinToString("&")
                    }
                    i = j - 1
                }
                map[key] = value
            }
            i++
        }
        return map
    }
}
