package com.oklookat.spectra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.oklookat.spectra.MainActivity
import com.oklookat.spectra.R
import com.oklookat.spectra.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class XrayVpnService : VpnService() {

    inner class ServiceBinder : Binder() {
        fun getService(): XrayVpnService = this@XrayVpnService
    }

    private val binder = ServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleLock = ReentrantLock()

    companion object {
        private const val TAG = "XrayVpnService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "XrayVpnServiceChannel"
        const val ACTION_STOP = "com.oklookat.spectra.STOP_VPN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        val isServiceRunning: Boolean get() = _isRunning.value

        @Volatile
        var runningProfileId: String? = null
            private set

        fun startOrRestart(
            context: Context,
            configJson: String,
            profileId: String?,
            enableIpv6: Boolean,
            vpnAddress: String,
            vpnDns: String,
            vpnAddressIpv6: String,
            vpnDnsIpv6: String,
            vpnMtu: Int
        ) {
            val intent = Intent(context, XrayVpnService::class.java).apply {
                putExtra("CONFIG_JSON", configJson)
                putExtra("PROFILE_ID", profileId)
                putExtra("ENABLE_IPV6", enableIpv6)
                putExtra("VPN_ADDRESS", vpnAddress)
                putExtra("VPN_DNS", vpnDns)
                putExtra("VPN_ADDRESS_IPV6", vpnAddressIpv6)
                putExtra("VPN_DNS_IPV6", vpnDnsIpv6)
                putExtra("VPN_MTU", vpnMtu)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var coreController: CoreController? = null

    @Volatile
    private var logcatProcess: Process? = null

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == VpnService.SERVICE_INTERFACE) {
            return super.onBind(intent)
        }
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                copyAssetsToFilesDir()
                try {
                    val key = generateRandom32ByteKey()
                    Libv2ray.initCoreEnv(filesDir.absolutePath, key)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to init core env", e)
                    LogManager.addLog("[Service] Failed to init core environment: ${e.message}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val configJson = intent?.getStringExtra("CONFIG_JSON") ?: ""
        val profileId = intent?.getStringExtra("PROFILE_ID")

        serviceScope.launch {
            val config = if (configJson.isEmpty()) {
                withContext(Dispatchers.IO) { loadConfigFromAssets() }
            } else configJson

            if (config.isEmpty()) {
                Log.e(TAG, "No config found, stopping service")
                LogManager.addLog("[Service] Stopping: No Xray configuration provided")
                stopVpn()
                return@launch
            }

            val vpnParams = VpnParams(
                enableIpv6 = intent?.getBooleanExtra("ENABLE_IPV6", false) ?: false,
                vpnAddress = intent?.getStringExtra("VPN_ADDRESS") ?: "10.0.0.1",
                vpnDns = intent?.getStringExtra("VPN_DNS") ?: "10.0.0.2",
                vpnAddressIpv6 = intent?.getStringExtra("VPN_ADDRESS_IPV6") ?: "fd00::1",
                vpnDnsIpv6 = intent?.getStringExtra("VPN_DNS_IPV6") ?: "fd00::2",
                vpnMtu = intent?.getIntExtra("VPN_MTU", 9000) ?: 9000
            )

            withContext(Dispatchers.IO) {
                lifecycleLock.withLock {
                    runningProfileId = profileId
                    startVpnLocked(config, vpnParams)
                }
            }
        }

        return START_NOT_STICKY
    }

    private data class VpnParams(
        val enableIpv6: Boolean,
        val vpnAddress: String,
        val vpnDns: String,
        val vpnAddressIpv6: String,
        val vpnDnsIpv6: String,
        val vpnMtu: Int
    )

    private fun startVpnLocked(configJson: String, params: VpnParams) {
        // Stop previous instance if running
        stopCoreLocked()
        closeVpnInterfaceLocked()

        if (logcatProcess == null) {
            startLogcatCapture()
        }

        try {
            val builder = Builder()
                .addAddress(params.vpnAddress, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(params.vpnDns)
                .setMtu(params.vpnMtu)
                .setSession("Spectra Xray")
                .addDisallowedApplication(packageName)

            if (params.enableIpv6) {
                builder.addAddress(params.vpnAddressIpv6, 128)
                builder.addRoute("::", 0)
                builder.addDnsServer(params.vpnDnsIpv6)
            }

            vpnInterface = builder.establish()

            val pfd = vpnInterface
            if (pfd == null) {
                val errorMsg = "Failed to establish VPN interface: Permission denied or system busy"
                Log.e(TAG, errorMsg)
                LogManager.addLog("[Service] $errorMsg")
                return
            }

            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val fd = pfd.fd
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    if (!p1.isNullOrBlank()) {
                        LogManager.addLog("[Core] $p1")
                    }
                    return 0
                }
                override fun shutdown(): Long = 0
                override fun startup(): Long = 0
            }

            coreController = Libv2ray.newCoreController(callback)
            
            thread(start = true, name = "XrayThread") {
                try {
                    LogManager.addLog("[Service] Starting Xray core...")
                    coreController?.startLoop(configJson, fd)
                } catch (e: Exception) {
                    val errorMsg = "Core loop error: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    LogManager.addLog("[Service] $errorMsg")
                } finally {
                    Log.d(TAG, "Core loop finished")
                }
            }
            
            _isRunning.value = true
        } catch (e: Exception) {
            val errorMsg = "VPN setup failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            LogManager.addLog("[Service] $errorMsg")
            lifecycleLock.withLock {
                stopCoreLocked()
                closeVpnInterfaceLocked()
            }
            _isRunning.value = false
            stopSelf()
        }
    }

    private fun startLogcatCapture() {
        thread(start = true, name = "LogcatThread") {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat GoLog:I *:S")
                logcatProcess = process
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    val message = line.substringAfter("GoLog   : ").trim()
                    if (message.isNotEmpty() && message != line) {
                        LogManager.addLog(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat capture error", e)
                LogManager.addLog("[Service] Internal logcat capture error")
            } finally {
                logcatProcess?.destroy()
                logcatProcess = null
            }
        }
    }

    private fun loadConfigFromAssets(): String {
        return try {
            assets.open("config.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from assets", e)
            LogManager.addLog("[Service] Asset error: Failed to load default config.json")
            ""
        }
    }

    private fun stopCoreLocked() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping core loop", e)
        }
        coreController = null
    }

    private fun closeVpnInterfaceLocked() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
    }

    private fun stopVpn() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                lifecycleLock.withLock {
                    runningProfileId = null
                    logcatProcess?.destroy()
                    logcatProcess = null
                    stopCoreLocked()
                    closeVpnInterfaceLocked()
                }
            }
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            _isRunning.value = false
            stopSelf()
        }
    }

    override fun onRevoke() {
        LogManager.addLog("[Service] VPN permission revoked by system")
        stopVpn()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, XrayVpnService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_content_active))
            .setSmallIcon(R.drawable.notification)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.notification,
                getString(R.string.notification_action_stop),
                pendingStopIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun generateRandom32ByteKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun copyAssetsToFilesDir() {
        arrayOf("geoip.dat", "geosite.dat").forEach { fileName ->
            val file = File(filesDir, fileName)
            if (!file.exists()) {
                try {
                    assets.open(fileName).use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Asset error: $fileName", e)
                    LogManager.addLog("[Service] Critical: Failed to copy $fileName from assets")
                }
            }
        }
    }

    override fun onDestroy() {
        LogManager.addLog("[Service] VPN Service destroyed")
        lifecycleLock.withLock {
            stopCoreLocked()
            closeVpnInterfaceLocked()
            logcatProcess?.destroy()
            logcatProcess = null
        }
        serviceScope.cancel()
        _isRunning.value = false
        runningProfileId = null
        super.onDestroy()
    }
}
