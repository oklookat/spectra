package com.oklookat.spectra.model

import com.google.gson.annotations.SerializedName

object XrayConfigBuild {

    fun vlessOutbound(link: ShareLink.Vless): Outbound<VlessOutboundConfigurationObject> {
        return Outbound(
            protocol = "vless",
            tag = "vless-out",
            settings = VlessOutboundConfigurationObject(
                address = link.host,
                port = link.port,
                id = link.uuid.toString(),
                encryption = link.encryption,
                flow = link.flow
            )
        )
    }

}

data class Outbound<T>(
    @SerializedName("protocol")
    val protocol: String,

    @SerializedName("settings")
    val settings: T,

    @SerializedName("tag")
    val tag: String? = null,

    @SerializedName("streamSettings")
    val streamSettings: Any? = null,

    @SerializedName("targetStrategy")
    val targetStrategy: String? = null
)

// https://xtls.github.io/ru/config/outbounds/vless.html#outboundconfigurationobject
data class VlessOutboundConfigurationObject(
    @SerializedName("address")
    val address: String,

    @SerializedName("port")
    val port: Int,

    @SerializedName("id")
    val id: String,

    @SerializedName("encryption")
    val encryption: String = "none",

    @SerializedName("flow")
    val flow: String = "",
)
