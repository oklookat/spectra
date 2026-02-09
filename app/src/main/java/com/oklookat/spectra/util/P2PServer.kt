package com.oklookat.spectra.util

import android.util.Log
import com.google.gson.Gson
import com.oklookat.spectra.model.EncryptedPayload
import com.oklookat.spectra.model.P2PPayload
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

class P2PServer(
    // Request port 0 to let the system pick a free port
    port: Int = 0,
    private val onPayloadReceived: (P2PPayload) -> Unit
) : NanoHTTPD(port) {

    val token: String = UUID.randomUUID().toString().replace("-", "").take(16)
    private val gson = Gson()
    private val TAG = "P2PServer"

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Incoming request: ${session.method} ${session.uri}")
        if (session.method == Method.POST && (session.uri == "/share" || session.uri == "/share/")) {
            try {
                val map = mutableMapOf<String, String>()
                session.parseBody(map)
                
                val postData = map["postData"] ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "text/plain", "No data. Use JSON body."
                ).also { Log.w(TAG, "Request failed: No postData") }
                
                val encryptedPayload = gson.fromJson(postData, EncryptedPayload::class.java)
                
                val decryptedJson = try {
                    EncryptionUtils.decrypt(encryptedPayload.data, token)
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption failed. Wrong token or corrupted data.", e)
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN, "text/plain", "Decryption failed"
                    )
                }
                
                val payload = gson.fromJson(decryptedJson, P2PPayload::class.java)
                Log.d(TAG, "Received and decrypted payload: $payload")
                
                if (payload.token != token) {
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN, "text/plain", "Invalid internal token"
                    ).also { Log.w(TAG, "Request failed: Invalid internal token") }
                }

                onPayloadReceived(payload)
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK").also {
                    Log.d(TAG, "Request successful")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing request", e)
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", e.message
                )
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found").also {
            Log.w(TAG, "Path not found: ${session.uri}")
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val addresses = mutableListOf<Pair<String, String>>()
            
            for (networkInterface in interfaces) {
                val inetAddresses = networkInterface.inetAddresses.toList()
                for (address in inetAddresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val host = address.hostAddress
                        if (host != null) {
                            addresses.add(networkInterface.name to host)
                            Log.d(TAG, "Found IP: ${networkInterface.name} -> $host")
                        }
                    }
                }
            }

            return addresses.find { it.first.contains("wlan") }?.second
                ?: addresses.find { !it.second.startsWith("10.0.2.") }?.second
                ?: addresses.firstOrNull()?.second
                
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
        }
        return null
    }

    fun getShareUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        if (!isAlive) return null
        return "http://$ip:$listeningPort/share"
    }
}
