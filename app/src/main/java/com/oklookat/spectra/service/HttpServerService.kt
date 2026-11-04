package com.oklookat.spectra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.oklookat.spectra.R
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.P2PServer

class HttpServerService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var p2pServer: P2PServer? = null

    private val binder = ServiceBinder()

    inner class ServiceBinder : Binder() {
        fun getService(): HttpServerService = this@HttpServerService
    }

    companion object {
        private const val TAG = "HttpServerService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "p2p_http_server_channel"

        var isServerRunning = false
            private set
        var serverUrl: String? = null
            private set
        var serverToken: String? = null
            private set
        var activeInstance: HttpServerService? = null
            private set

        private var onPayloadReceivedListener: ((P2PPayload) -> Unit)? = null

        fun setOnPayloadReceivedListener(listener: ((P2PPayload) -> Unit)?) {
            onPayloadReceivedListener = listener
        }

        fun startService(context: Context) {
            val intent = Intent(context, HttpServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HttpServerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        Log.d(TAG, "HttpServerService onCreate")
        acquireLocks()
        startHttpServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.p2p_share_mode))
            .setContentText("Local HTTP server listening for P2P transfers")
            .setSmallIcon(R.mipmap.app_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                fgsType
            )
            Log.d(TAG, "Service started as ForegroundService with type $fgsType")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun acquireLocks() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                val mode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                wifiLock = wm.createWifiLock(mode, "Spectra:P2PWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Acquired WifiLock mode=$mode")
                LogManager.addLog("[P2P Service] Acquired WifiLock")
            }

            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spectra:P2PWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(15 * 60 * 1000L) // 15 mins
                }
                Log.d(TAG, "Acquired WakeLock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks", e)
        }
    }

    private fun startHttpServer() {
        try {
            val server = P2PServer(
                context = applicationContext,
                preferredPort = 0,
                onPayloadReceived = { payload ->
                    LogManager.addLog("[P2P Service] Payload received for device: ${payload.deviceName}")
                    onPayloadReceivedListener?.invoke(payload)
                }
            )
            server.start()
            p2pServer = server
            isServerRunning = true
            serverUrl = server.getShareUrl()
            serverToken = server.token
            LogManager.addLog("[P2P Service] Server started at $serverUrl with token $serverToken")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start P2PServer", e)
            LogManager.addLog("[P2P Service] Error starting P2PServer: ${e.message}")
            isServerRunning = false
            serverUrl = null
            serverToken = null
        }
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            Log.d(TAG, "Released WifiLock & WakeLock")
            LogManager.addLog("[P2P Service] Released Locks")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HttpServerService onDestroy")
        try {
            p2pServer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        p2pServer = null
        isServerRunning = false
        serverUrl = null
        serverToken = null
        activeInstance = null
        releaseLocks()
        LogManager.addLog("[P2P Service] Stopped")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "P2P HTTP Server Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
