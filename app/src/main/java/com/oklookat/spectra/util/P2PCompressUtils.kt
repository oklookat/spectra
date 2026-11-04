package com.oklookat.spectra.util

import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.ProfileTransferItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object P2PCompressUtils {
    private val gson = Gson()

    fun compress(text: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decompress(compressedBase64: String): String {
        val bytes = Base64.decode(compressedBase64, Base64.NO_WRAP or Base64.URL_SAFE)
        val bis = ByteArrayInputStream(bytes)
        GZIPInputStream(bis).use { return it.readBytes().toString(Charsets.UTF_8) }
    }

    fun buildProfileQrString(
        profile: Profile,
        profileContent: String?
    ): String {
        if (profile.isRemote && !profile.url.isNullOrBlank()) {
            return profile.url
        }
        val content = profileContent?.trim() ?: ""
        if (content.startsWith("vless://") || content.startsWith("vmess://") ||
            content.startsWith("trojan://") || content.startsWith("ss://") ||
            content.startsWith("socks://") || content.startsWith("hysteria2://") ||
            content.startsWith("http://") || content.startsWith("https://")
        ) {
            return content
        }
        if (content.isNotBlank()) {
            val compressed = compress(content)
            return "spectra://import?name=${Uri.encode(profile.name)}&data=$compressed"
        }
        return ""
    }

    data class GroupTransferBundle(
        val group: Group,
        val profiles: List<ProfileTransferItem>
    )

    fun buildGroupQrString(
        group: Group,
        profiles: List<ProfileTransferItem>
    ): String {
        if (group.isRemote && !group.url.isNullOrBlank()) {
            return group.url
        }
        val bundle = GroupTransferBundle(group, profiles)
        val json = gson.toJson(bundle)
        val compressed = compress(json)
        return "spectra://import?type=group&name=${Uri.encode(group.name)}&data=$compressed"
    }

    fun parseGroupBundle(json: String): GroupTransferBundle? {
        return try {
            gson.fromJson(json, GroupTransferBundle::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
