package com.oklookat.spectra.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.oklookat.spectra.model.EncryptedPayload
import com.oklookat.spectra.model.P2PPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class P2PClient(private val context: Context? = null) {
    private val TAG = "P2PClient"

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val nonVpnNetwork = cm.allNetworks.find { net ->
                        val caps = cm.getNetworkCapabilities(net)
                        caps != null &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                        (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                         caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                         caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    }
                    if (nonVpnNetwork != null) {
                        builder.socketFactory(nonVpnNetwork.socketFactory)
                        Log.d(TAG, "Bound OkHttp socketFactory to physical network: $nonVpnNetwork")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not bind socketFactory to non-VPN network", e)
            }
        }

        builder.build()
    }

    private val gson = Gson()

    suspend fun sendPayload(url: String, payload: P2PPayload): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Encrypting and sending payload to $url")
            
            // 1. Serialize payload to JSON
            val plainJson = gson.toJson(payload)
            
            // 2. Encrypt JSON using the token as key
            val encryptedData = EncryptionUtils.encrypt(plainJson, payload.token)
            
            // 3. Wrap in EncryptedPayload
            val encryptedPayload = EncryptedPayload(data = encryptedData)
            val finalJson = gson.toJson(encryptedPayload)
            
            val request = Request.Builder()
                .url(url)
                .post(finalJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully sent encrypted payload")
                    Result.success(Unit)
                } else {
                    val errorMsg = "Failed to send: ${response.code} ${response.message}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending payload to $url", e)
            Result.failure(e)
        }
    }
}
