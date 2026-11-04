package com.oklookat.spectra.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.google.gson.Gson
import com.oklookat.spectra.model.EncryptedPayload
import com.oklookat.spectra.model.P2PPayload
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.UUID

class P2PServer(
    private val context: Context? = null,
    preferredPort: Int = 18080,
    private val onPayloadReceived: (P2PPayload) -> Unit = {}
) : NanoHTTPD(null, choosePort(preferredPort)) {  // hostname=null => bind 0.0.0.0

    val token: String = UUID.randomUUID().toString().replace("-", "").take(16)
    private val gson = Gson()
    private val TAG = "P2PServer"

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private fun choosePort(preferred: Int): Int {
            return try {
                ServerSocket(preferred).use { preferred }
            } catch (_: Exception) {
                try {
                    ServerSocket(0).use { it.localPort }
                } catch (_: Exception) {
                    preferred // last resort, will fail later
                }
            }
        }
    }

    override fun start() {
        acquireLocks()
        try {
            super.start()
            Log.d(TAG, "Server started on port $listeningPort")
            LogManager.addLog("[P2P Server] Started on port $listeningPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            LogManager.addLog("[P2P Server] Failed to start: ${e.message}")
            throw e
        }
    }

    override fun stop() {
        super.stop()
        releaseLocks()
    }

    private fun acquireLocks() {
        if (context == null) return
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                val mode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                wifiLock = wm.createWifiLock(mode, "Spectra:P2PWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Acquired WifiLock mode=$mode")
                LogManager.addLog("[P2P Server] Acquired WifiLock")
            }

            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spectra:P2PWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 1000L) // 10 mins
                }
                Log.d(TAG, "Acquired WakeLock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring Locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            Log.d(TAG, "Released Locks")
            LogManager.addLog("[P2P Server] Released Locks")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Locks", e)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Incoming request: ${session.method} ${session.uri}")
        LogManager.addLog("[P2P Server] Incoming request: ${session.method} ${session.uri}")

        if (session.method == Method.POST && (session.uri == "/share" || session.uri == "/share/")) {
            try {
                val map = mutableMapOf<String, String>()
                session.parseBody(map)
                
                val postData = map["postData"] ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "text/plain", "No data. Use JSON body."
                ).also {
                    Log.w(TAG, "Request failed: No postData")
                    LogManager.addLog("[P2P Server] Request failed: No postData")
                }
                
                val encryptedPayload = gson.fromJson(postData, EncryptedPayload::class.java)
                
                val decryptedJson = try {
                    EncryptionUtils.decrypt(encryptedPayload.data, token)
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption failed. Wrong token or corrupted data.", e)
                    LogManager.addLog("[P2P Server] Decryption failed: ${e.message}")
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN, "text/plain", "Decryption failed"
                    )
                }
                
                val payload = gson.fromJson(decryptedJson, P2PPayload::class.java)
                Log.d(TAG, "Received and decrypted payload: $payload")
                LogManager.addLog("[P2P Server] Received payload for device ${payload.deviceName}")
                
                if (payload.token != token) {
                    LogManager.addLog("[P2P Server] Request failed: Invalid internal token")
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN, "text/plain", "Invalid internal token"
                    ).also { Log.w(TAG, "Request failed: Invalid internal token") }
                }

                onPayloadReceived(payload)
                LogManager.addLog("[P2P Server] Request successful")
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK").also {
                    Log.d(TAG, "Request successful")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing request", e)
                LogManager.addLog("[P2P Server] Error processing request: ${e.message}")
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", e.message
                )
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    fun getLocalIpAddress(): String? {
        try {
            // 1. Prefer Wi-Fi IP from WifiManager (most reliable when VPN is active)
            val wifiIp = getWifiIpAddress()
            if (wifiIp != null) {
                LogManager.addLog("[P2P Server] Selected Wi-Fi IP via WifiManager: $wifiIp")
                return wifiIp
            }

            // 2. Fallback: scan NetworkInterfaces, prefer wlan*
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val addresses = mutableListOf<Pair<String, String>>()
            
            for (networkInterface in interfaces) {
                val name = networkInterface.name.lowercase()
                // Skip common virtual / VPN / loopback interfaces
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet") ||
                    name.startsWith("ccmni") || name.contains("vpn") || name == "lo") {
                    continue
                }
                val inetAddresses = networkInterface.inetAddresses.toList()
                for (address in inetAddresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val host = address.hostAddress
                        if (host != null && !host.startsWith("10.0.2.") /* emulator */) {
                            addresses.add(networkInterface.name to host)
                        }
                    }
                }
            }

            val selected = addresses.find { it.first.lowercase().contains("wlan") }?.second
                ?: addresses.find { it.second.startsWith("192.168.") }?.second
                ?: addresses.find { it.second.startsWith("10.") }?.second
                ?: addresses.firstOrNull()?.second

            LogManager.addLog("[P2P Server] Available IPs: $addresses | Selected IP: $selected")
            return selected
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP", e)
            LogManager.addLog("[P2P Server] Error getting local IP: ${e.message}")
        }
        return null
    }

    private fun getWifiIpAddress(): String? {
        return try {
            val wm = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            @Suppress("DEPRECATION")
            val ipInt = wm.connectionInfo?.ipAddress ?: return null
            if (ipInt == 0) return null
            // WifiManager returns little-endian int
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            Log.w(TAG, "getWifiIpAddress failed", e)
            null
        }
    }

    fun getShareUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        if (!isAlive) return null
        return "http://$ip:$listeningPort/share"
    }
}
