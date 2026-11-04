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
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

import com.oklookat.spectra.data.repository.WifiExclusionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class XrayVpnService : VpnService() {

    @Inject
    lateinit var wifiExclusionRepository: WifiExclusionRepository

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

        @Volatile
        var activeConfigJson: String? = null
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
            vpnMtu: Int,
            isTunModeEnabled: Boolean,
            socksPort: Int,
            disallowedPackages: ArrayList<String> = arrayListOf()
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
                putExtra("IS_TUN_MODE_ENABLED", isTunModeEnabled)
                putExtra("SOCKS_PORT", socksPort)
                putStringArrayListExtra("DISALLOWED_PACKAGES", disallowedPackages)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var coreController: CoreController? = null

    @Volatile
    private var logcatProcess: Process? = null

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    @Volatile private var isCurrentlyWifiBypassed = false
    @Volatile private var lastRawConfig = ""
    @Volatile private var lastVpnParams: VpnParams? = null

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == VpnService.SERVICE_INTERFACE) {
            return super.onBind(intent)
        }
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
        observeWifiExclusionSettings()
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

    private fun observeWifiExclusionSettings() {
        serviceScope.launch {
            var isFirstEmission = true
            var lastBypassJson = ""
            var lastPrimaryDns = ""
            var lastSecondaryDns = ""

            combine(
                wifiExclusionRepository.isWifiExclusionEnabledFlow,
                wifiExclusionRepository.excludedSsidsFlow,
                wifiExclusionRepository.customBypassConfigJsonFlow,
                wifiExclusionRepository.bypassDnsPrimaryFlow,
                wifiExclusionRepository.bypassDnsSecondaryFlow
            ) { _, _, customJson, primaryDns, secondaryDns ->
                val configChanged = !isFirstEmission && 
                    ((customJson != lastBypassJson) || (primaryDns != lastPrimaryDns) || (secondaryDns != lastSecondaryDns))
                lastBypassJson = customJson
                lastPrimaryDns = primaryDns
                lastSecondaryDns = secondaryDns
                isFirstEmission = false
                configChanged
            }
                .collect { configChanged ->
                    if (!isUserStopped && _isRunning.value) {
                        onNetworkStateChanged(currentActiveNetwork, null, configChanged)
                    }
                }
        }
    }

    @Volatile private var currentActiveNetwork: android.net.Network? = null

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            val callback = try {
                object : android.net.ConnectivityManager.NetworkCallback(
                    android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
                ) {
                    override fun onAvailable(network: android.net.Network) {
                        onNetworkStateChanged(network, null)
                    }

                    override fun onLost(network: android.net.Network) {
                        onNetworkStateChanged(null, null)
                    }

                    override fun onCapabilitiesChanged(
                        network: android.net.Network,
                        networkCapabilities: android.net.NetworkCapabilities
                    ) {
                        onNetworkStateChanged(network, networkCapabilities)
                    }
                }
            } catch (e: SecurityException) {
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        onNetworkStateChanged(network, null)
                    }

                    override fun onLost(network: android.net.Network) {
                        onNetworkStateChanged(null, null)
                    }

                    override fun onCapabilitiesChanged(
                        network: android.net.Network,
                        networkCapabilities: android.net.NetworkCapabilities
                    ) {
                        onNetworkStateChanged(network, networkCapabilities)
                    }
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback", e)
        }
    }

    private fun getPhysicalActiveNetwork(networkHint: android.net.Network? = null): android.net.Network? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return null

        // 1. If networkHint is provided and has INTERNET and NOT_VPN, just use it
        if (networkHint != null) {
            val caps = cm.getNetworkCapabilities(networkHint)
            if (caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return networkHint
            }
        }

        // 2. Otherwise find any physical network with Wi-Fi first, then Cellular
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            ) {
                return net
            }
        }

        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            ) {
                return net
            }
        }

        return null
    }

    private fun updateUnderlyingNetwork(network: android.net.Network?) {
        try {
            if (network != null) {
                setUnderlyingNetworks(arrayOf(network))
            } else {
                setUnderlyingNetworks(null)
            }
        } catch (e: Exception) {
            try {
                setUnderlyingNetworks(null)
            } catch (_: Exception) {}
        }
    }

    @Volatile private var isUserStopped = false
    @Volatile private var networkUpdateJob: kotlinx.coroutines.Job? = null

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null
    }

    private fun onNetworkStateChanged(
        network: android.net.Network?,
        networkCapabilities: android.net.NetworkCapabilities? = null,
        forceConfigUpdate: Boolean = false
    ) {
        if (isUserStopped || !_isRunning.value) return

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        
        // If network is null (lost), we want to use the fallback logic to find another active network
        // If a new network is provided, getPhysicalActiveNetwork will prioritize it if valid
        val activePhysicalNetwork = getPhysicalActiveNetwork(network)

        updateUnderlyingNetwork(activePhysicalNetwork)
        currentActiveNetwork = activePhysicalNetwork

        networkUpdateJob?.cancel()
        networkUpdateJob = serviceScope.launch(Dispatchers.IO) {
            if (isUserStopped || !_isRunning.value) return@launch

            val isEnabled = try {
                wifiExclusionRepository.isWifiExclusionEnabledFlow.first()
            } catch (_: Exception) { false }

            val params = lastVpnParams ?: return@launch
            val rawConfig = lastRawConfig.ifEmpty { return@launch }

            val capsToUse = networkCapabilities ?: (activePhysicalNetwork?.let { cm?.getNetworkCapabilities(it) })
            val isWifi = activePhysicalNetwork != null &&
                    capsToUse?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true

            val targetBypassState: Boolean
            var currentSsid: String? = null
            var excludedSsids: Set<String> = emptySet()

            if (isEnabled && isWifi) {
                currentSsid = wifiExclusionRepository.getCurrentWifiSsid(capsToUse)
                if (currentSsid == null) {
                    for (retry in 1..10) {
                        kotlinx.coroutines.delay(300)
                        if (isUserStopped || !_isRunning.value) return@launch
                        val currentCaps = activePhysicalNetwork?.let { cm?.getNetworkCapabilities(it) } ?: capsToUse
                        currentSsid = wifiExclusionRepository.getCurrentWifiSsid(currentCaps)
                        if (currentSsid != null) break
                    }
                }
                
                excludedSsids = try {
                    wifiExclusionRepository.excludedSsidsFlow.first()
                } catch (_: Exception) { emptySet() }
                
                targetBypassState = currentSsid != null && excludedSsids.contains(currentSsid)
            } else {
                targetBypassState = false
            }

            if (targetBypassState != isCurrentlyWifiBypassed || (forceConfigUpdate && isCurrentlyWifiBypassed)) {
                val wasBypassed = isCurrentlyWifiBypassed

                if (targetBypassState != wasBypassed) {
                    if (isWifi && isEnabled) {
                        LogManager.addLog("[Service] Wi-Fi network active. SSID: '${currentSsid ?: "<unknown>"}' | Target Bypass State: $targetBypassState")
                    } else if (!isEnabled) {
                        LogManager.addLog("[Service] Wi-Fi exclusion disabled -> Target Bypass State: false")
                    } else {
                        LogManager.addLog("[Service] Mobile/Cellular network active -> Target Bypass State: false")
                    }
                } else if (forceConfigUpdate && isCurrentlyWifiBypassed) {
                    LogManager.addLog("[Service] Wi-Fi bypass configuration updated. Restarting VPN with new config.")
                }

                isCurrentlyWifiBypassed = targetBypassState
                val activeConfig = if (targetBypassState) {
                    if (!wasBypassed) {
                        LogManager.addLog("[Service] Connected to excluded Wi-Fi '${currentSsid ?: ""}'. Switching to Wi-Fi bypass config.")
                    }
                    wifiExclusionRepository.getActiveBypassConfigJson()
                } else {
                    if (wasBypassed) {
                        val netType = if (isWifi) "Wi-Fi" else "Cellular/Mobile"
                        LogManager.addLog("[Service] Left excluded Wi-Fi ($netType). Restoring original VPN profile config.")
                    }
                    rawConfig
                }

                lifecycleLock.withLock {
                    if (isUserStopped || !_isRunning.value) return@withLock
                    startVpnLocked(activeConfig, params)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        isUserStopped = false
        if (networkCallback == null) {
            registerNetworkCallback()
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

        val configJson = intent?.getStringExtra("CONFIG_JSON") ?: ""
        val profileId = intent?.getStringExtra("PROFILE_ID")

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                copyAssetsToFilesDir()
                try {
                    val key = generateRandom32ByteKey()
                    Libv2ray.initCoreEnv(filesDir.absolutePath, key)
                } catch (_: Exception) {}
            }

            val rawConfig = configJson.ifEmpty {
                withContext(Dispatchers.IO) { loadConfigFromAssets() }
            }
            // Configuration is already prepared in the viewmodel/usecase when sent via intent.
            // If it came from assets (default config), we should prepare it.
            val preparedConfig = if (configJson.isEmpty()) {
                val isTunModeEnabled = intent?.getBooleanExtra("IS_TUN_MODE_ENABLED", true) ?: true
                val socksPort = intent?.getIntExtra("SOCKS_PORT", 10808) ?: 10808
                com.oklookat.spectra.model.XrayConfigBuild.prepareConfigForAndroid(rawConfig, isTunModeEnabled, socksPort)
            } else {
                rawConfig
            }

            if (preparedConfig.isEmpty()) {
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
                vpnMtu = intent?.getIntExtra("VPN_MTU", 9000) ?: 9000,
                disallowedPackages = intent?.getStringArrayListExtra("DISALLOWED_PACKAGES") ?: emptyList()
            )

            lastRawConfig = preparedConfig
            lastVpnParams = vpnParams

            val isEnabled = try {
                wifiExclusionRepository.isWifiExclusionEnabledFlow.first()
            } catch (_: Exception) { false }

            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activePhysicalNet = getPhysicalActiveNetwork(currentActiveNetwork)
            val currentCaps = activePhysicalNet?.let { cm?.getNetworkCapabilities(it) }
            val currentSsid = wifiExclusionRepository.getCurrentWifiSsid(currentCaps)
            val excludedSsids = try {
                wifiExclusionRepository.excludedSsidsFlow.first()
            } catch (_: Exception) { emptySet() }

            val shouldBypass = isEnabled && currentSsid != null && excludedSsids.contains(currentSsid)
            isCurrentlyWifiBypassed = shouldBypass

            val finalConfig = if (shouldBypass) {
                LogManager.addLog("[Service] Connected to excluded Wi-Fi '$currentSsid'. Using Wi-Fi bypass config.")
                wifiExclusionRepository.getActiveBypassConfigJson()
            } else {
                preparedConfig
            }

            withContext(Dispatchers.IO) {
                lifecycleLock.withLock {
                    runningProfileId = profileId
                    startVpnLocked(finalConfig, vpnParams)
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
        val vpnMtu: Int,
        val disallowedPackages: List<String>
    )

    private fun startVpnLocked(configJson: String, params: VpnParams) {
        activeConfigJson = configJson
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

            for (pkg in params.disallowedPackages) {
                if (pkg != packageName && pkg.isNotBlank()) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        Log.w(TAG, "Package $pkg not found or failed to disallow: ${e.message}")
                    }
                }
            }

            if (params.enableIpv6) {
                builder.addAddress(params.vpnAddressIpv6, 128)
                builder.addRoute("::", 0)
                builder.addDnsServer(params.vpnDnsIpv6)
            }

            vpnInterface = builder.establish()
            updateUnderlyingNetwork(currentActiveNetwork)

            val pfd = vpnInterface
            if (pfd == null) {
                val errorMsg = "Failed to establish VPN interface: Permission denied or system busy"
                Log.e(TAG, errorMsg)
                LogManager.addLog("[Service] $errorMsg")
                return
            }

            val fd = pfd.fd
            try {
                android.system.Os.setenv("xray.tun.fd", fd.toString(), true)
                android.system.Os.setenv("XRAY_TUN_FD", fd.toString(), true)
                Log.d(TAG, "Successfully set xray.tun.fd=$fd and XRAY_TUN_FD=$fd")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set TUN FD environment variable", e)
            }

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
            
            val t = thread(start = true, name = "XrayThread") {
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
            xrayThread = t
            
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
                try {
                    val clearProcess = Runtime.getRuntime().exec("logcat -c")
                    clearProcess.waitFor()
                } catch (_: Exception) {}

                val process = Runtime.getRuntime().exec("logcat GoLog:I *:S")
                logcatProcess = process
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val message = line.substringAfter("GoLog   : ").trim()
                        if (message.isNotEmpty() && message != line) {
                            LogManager.addLog(message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat capture error", e)
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

    @Volatile
    private var xrayThread: Thread? = null

    private fun stopCoreLocked() {
        try {
            val controller = coreController
            if (controller != null) {
                LogManager.addLog("[Service] Stopping Xray core instance...")
                controller.stopLoop()
                coreController = null
            }
            xrayThread?.let {
                try {
                    it.join(300)
                } catch (_: Exception) {}
            }
            xrayThread = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping core loop", e)
        }
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
        isUserStopped = true
        lastVpnParams = null
        lastRawConfig = ""
        activeConfigJson = null
        networkUpdateJob?.cancel()
        networkUpdateJob = null
        unregisterNetworkCallback()

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
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
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
        isUserStopped = true
        lastVpnParams = null
        lastRawConfig = ""
        activeConfigJson = null
        networkUpdateJob?.cancel()
        networkUpdateJob = null
        unregisterNetworkCallback()

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
