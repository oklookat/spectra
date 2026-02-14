package com.oklookat.spectra.util

import android.content.Intent
import android.util.Base64
import com.oklookat.spectra.model.PendingGroup
import com.oklookat.spectra.model.PendingProfile

object DeepLinkHandler {
    sealed class DeepLinkResult {
        data class Profile(val pending: PendingProfile) : DeepLinkResult()
        data class Group(val pending: PendingGroup) : DeepLinkResult()
        data object Invalid : DeepLinkResult()
        data object None : DeepLinkResult()
    }

    fun handle(intent: Intent?): DeepLinkResult {
        val uri = intent?.data ?: return DeepLinkResult.None
        try {
            val encodedData = if (uri.scheme == "https" && uri.host == "spectra.local") {
                uri.getQueryParameter("data") ?: ""
            } else {
                ""
            }

            if (encodedData.isEmpty()) return DeepLinkResult.Invalid

            val decodedData = String(Base64.decode(encodedData, Base64.DEFAULT))
            val params = decodedData.split("&").associate {
                val split = it.split("=")
                split[0] to split.getOrElse(1) { "" }
            }

            val type = params["type"] ?: "profile"
            val name = params["name"] ?: ""
            val url = params["url"] ?: ""
            val autoUpdate = params["autoupdate"]?.toBoolean() ?: false
            val interval = params["autoupdateinterval"]?.toIntOrNull() ?: 15

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
}
