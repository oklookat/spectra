package com.oklookat.spectra

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
import kotlin.concurrent.thread

class XrayVpnService : VpnService() {

    inner class ServiceBinder : Binder() {
        fun getService(): XrayVpnService = this@XrayVpnService
    }

    private val binder = ServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
                val key = generateRandom32ByteKey()
                Libv2ray.initCoreEnv(filesDir.absolutePath, key)
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
            if (configJson.isEmpty() && withContext(Dispatchers.IO) { loadConfigFromAssets().isEmpty() }) {
                Log.e(TAG, "No config found, stopping service")
                stopVpn()
                return@launch
            }

            _isRunning.value = true

            if (coreController != null) {
                Log.d(TAG, "Core already running, stopping existing core for restart")
                withContext(Dispatchers.IO) { stopCore() }
            }

            runningProfileId = profileId

            val enableIpv6 = intent?.getBooleanExtra("ENABLE_IPV6", false) ?: false
            val vpnAddress = intent?.getStringExtra("VPN_ADDRESS") ?: "10.0.0.1"
            val vpnDns = intent?.getStringExtra("VPN_DNS") ?: "10.0.0.2"
            val vpnAddressIpv6 = intent?.getStringExtra("VPN_ADDRESS_IPV6") ?: "fd00::1"
            val vpnDnsIpv6 = intent?.getStringExtra("VPN_DNS_IPV6") ?: "fd00::2"
            val vpnMtu = intent?.getIntExtra("VPN_MTU", 9000) ?: 9000

            if (logcatProcess == null) {
                startLogcatCapture()
            }

            setupVpn(
                configJson = configJson.ifEmpty { withContext(Dispatchers.IO) { loadConfigFromAssets() } },
                enableIpv6 = enableIpv6,
                vpnAddress = vpnAddress,
                vpnDns = vpnDns,
                vpnAddressIpv6 = vpnAddressIpv6,
                vpnDnsIpv6 = vpnDnsIpv6,
                vpnMtu = vpnMtu
            )
        }

        return START_NOT_STICKY
    }

    private fun startLogcatCapture() {
        thread(start = true, name = "LogcatThread") {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                logcatProcess = Runtime.getRuntime().exec("logcat GoLog:I *:S")
                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))
                while (logcatProcess != null) {
                    val line = reader.readLine() ?: break
                    val message = line.substringAfter("GoLog   : ").trim()
                    if (message.isNotEmpty() && message != line) {
                        LogManager.addLog(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat capture error", e)
            }
        }
    }

    private fun loadConfigFromAssets(): String {
        return try {
            assets.open("config.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config from assets", e)
            ""
        }
    }

    private fun setupVpn(
        configJson: String,
        enableIpv6: Boolean,
        vpnAddress: String,
        vpnDns: String,
        vpnAddressIpv6: String,
        vpnDnsIpv6: String,
        vpnMtu: Int
    ) {
        val notification = buildNotification()

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}")
            _isRunning.value = false
            stopSelf()
            return
        }

        try {
            val builder = Builder()
                .addAddress(vpnAddress, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(vpnDns)
                .setMtu(vpnMtu)
                .setSession("Spectra Xray")
                .addDisallowedApplication(packageName)

            if (enableIpv6) {
                builder.addAddress(vpnAddressIpv6, 128)
                builder.addRoute("::", 0)
                builder.addDnsServer(vpnDnsIpv6)
            }

            vpnInterface?.close()
            vpnInterface = builder.establish()

            vpnInterface?.let { pfd ->
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

                thread(start = true, name = "XrayThread") {
                    try {
                        synchronized(this) {
                            if (coreController == null) {
                                coreController = Libv2ray.newCoreController(callback)
                            }
                        }
                        coreController?.startLoop(configJson, fd)
                    } catch (e: Exception) {
                        Log.e(TAG, "Core loop error", e)
                    } finally {
                        synchronized(this) { coreController = null }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN setup failed", e)
            _isRunning.value = false
            stopSelf()
        }
    }

    private fun stopCore() {
        synchronized(this) {
            coreController?.let {
                try {
                    it.stopLoop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping core loop", e)
                }
                coreController = null
            }
        }
    }

    private fun stopVpn() {
        if (!_isRunning.value && coreController == null) {
            stopSelf()
            return
        }

        serviceScope.launch {
            runningProfileId = null
            logcatProcess?.destroy()
            logcatProcess = null

            withContext(Dispatchers.IO) {
                try {
                    vpnInterface?.close()
                } catch (_: Exception) {}
                vpnInterface = null
                stopCore()
            }

            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            _isRunning.value = false
            stopSelf()
        }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopVpn()
        Handler(Looper.getMainLooper()).postDelayed({
            _isRunning.value = false
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 500)
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
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        _isRunning.value = false
        runningProfileId = null
        stopVpn()
        super.onDestroy()
    }
}
