package com.oklookat.spectra

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.oklookat.spectra.model.PendingProfile
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.ui.screens.LogsScreen
import com.oklookat.spectra.ui.screens.MainScreen
import com.oklookat.spectra.ui.screens.ProfilesScreen
import com.oklookat.spectra.ui.screens.SettingsScreen
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.util.ProfileUpdateWorker
import com.oklookat.spectra.util.TvUtils

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var vpnService: XrayVpnService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as XrayVpnService.ServiceBinder
            vpnService = binder.getService()
            isBound = true
            viewModel.updateVpnStatus()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            vpnService = null
            isBound = false
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) tryStartVpn()
    }

    private val prepareVpn = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, getString(R.string.vpn_permission_denied), Toast.LENGTH_SHORT).show()
            viewModel.updateVpnStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ProfileUpdateWorker.setupPeriodicWork(this)

        handleIntent(intent)

        setContent {
            SpectraTheme {
                val uiState = viewModel.uiState
                val settings = uiState.settings
                
                LaunchedEffect(Unit) {
                    // Trigger the verification dialog only once when the UI is ready
                    viewModel.triggerDeepLinkVerifyDialogIfNeeded()
                }

                LaunchedEffect(uiState.deepLinkProfile) {
                    uiState.deepLinkProfile?.let { dp ->
                        val existing = uiState.profiles.find { it.name == dp.name }
                        if (existing != null) {
                            viewModel.setPendingProfileToReplace(dp)
                        } else {
                            addProfileFromDeepLink(dp)
                            viewModel.setDeepLinkProfile(null)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MainUiEvent.ShowToast -> {
                                val message = event.messageResId?.let { getString(it, *event.formatArgs.toTypedArray()) } ?: event.message ?: ""
                                Toast.makeText(this@MainActivity, message, if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                            }
                            is MainUiEvent.RestartVpn -> {
                                startVpnService()
                            }
                            is MainUiEvent.OpenDeepLinkSettings -> {
                                openDeepLinkSettings()
                            }
                        }
                    }
                }

                uiState.pendingProfileToReplace?.let { dp ->
                    AlertDialog(
                        onDismissRequest = { 
                            viewModel.setPendingProfileToReplace(null)
                            viewModel.setDeepLinkProfile(null) 
                        },
                        title = { Text(stringResource(R.string.replace_profile_q)) },
                        text = { Text(stringResource(R.string.profile_exists_replace_q, dp.name)) },
                        confirmButton = {
                            Button(onClick = {
                                val existing = uiState.profiles.find { it.name == dp.name }
                                if (existing != null) {
                                    viewModel.replaceRemoteProfile(
                                        existingProfile = existing,
                                        url = dp.url,
                                        autoUpdate = dp.autoUpdate,
                                        interval = dp.interval
                                    )
                                }
                                viewModel.setPendingProfileToReplace(null)
                                viewModel.setDeepLinkProfile(null)
                            }) {
                                Text(stringResource(R.string.replace))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                viewModel.setPendingProfileToReplace(null)
                                viewModel.setDeepLinkProfile(null) 
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                if (uiState.showDeepLinkVerifyDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.setShowDeepLinkVerifyDialog(false) },
                        title = { Text(stringResource(R.string.enable_easy_import)) },
                        text = { Text(stringResource(R.string.easy_import_desc)) },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.setShowDeepLinkVerifyDialog(false)
                                openDeepLinkSettings()
                            }) {
                                Text(stringResource(R.string.configure))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.setShowDeepLinkVerifyDialog(false) }) {
                                Text(stringResource(R.string.later))
                            }
                        }
                    )
                }

                AppContent(
                    viewModel = viewModel,
                    currentScreen = uiState.currentScreen,
                    isVpnEnabled = uiState.isVpnEnabled,
                    useDebugConfig = settings.useDebugConfig,
                    isIpv6Enabled = settings.isIpv6Enabled,
                    vpnAddress = settings.vpnAddress,
                    vpnDns = settings.vpnDns,
                    vpnAddressIpv6 = settings.vpnAddressIpv6,
                    vpnDnsIpv6 = settings.vpnDnsIpv6,
                    vpnMtu = settings.vpnMtu,
                    onNavigate = { viewModel.setScreen(it) },
                    onToggleVpn = { enabled ->
                        if (enabled) tryStartVpn() else stopVpnService()
                    },
                    onToggleDebug = { viewModel.toggleDebugConfig(it) },
                    onToggleIpv6 = { viewModel.toggleIpv6(it) },
                    onUpdateTunnel = { addr, dns, addr6, dns6, mtu ->
                        viewModel.updateTunnelSettings(addr, dns, addr6, dns6, mtu)
                    }
                )
            }
        }
    }

    private fun openDeepLinkSettings() {
        if (TvUtils.isTv(this)) {
            Toast.makeText(this, getString(R.string.deep_link_settings_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                "package:$packageName".toUri()
            )
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.could_not_open_settings), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        try {
            val encodedData = if (uri.scheme == "oklspectra") {
                uri.toString().substringAfter("oklspectra://")
            } else if (uri.scheme == "https" && uri.host == "spectra.local") {
                uri.getQueryParameter("data") ?: ""
            } else {
                ""
            }

            if (encodedData.isEmpty()) return

            val decodedData = String(Base64.decode(encodedData, Base64.DEFAULT))
            val params = decodedData.split("&").associate {
                val split = it.split("=")
                split[0] to split.getOrElse(1) { "" }
            }

            val name = params["name"] ?: ""
            val url = params["url"] ?: ""
            val autoUpdate = params["autoupdate"]?.toBoolean() ?: false
            val interval = params["autoupdateinterval"]?.toIntOrNull() ?: 15

            if (name.isNotEmpty() && url.isNotEmpty()) {
                viewModel.setDeepLinkProfile(PendingProfile(name, url, autoUpdate, interval))
                viewModel.setScreen(Screen.Profiles)
            }
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.invalid_link), Toast.LENGTH_SHORT).show()
        }
    }

    private fun addProfileFromDeepLink(dp: PendingProfile) {
        viewModel.addRemoteProfile(dp.name, dp.url, dp.autoUpdate, dp.interval) { result ->
            if (result.isSuccess) {
                Toast.makeText(this, getString(R.string.profile_added, dp.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.failed_to_add_profile, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateVpnStatus()
        viewModel.checkDeepLinkStatus()
    }

    private fun tryStartVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val intent = VpnService.prepare(this)
        if (intent != null) prepareVpn.launch(intent) else startVpnService()
    }

    private fun startVpnService() {
        val uiState = viewModel.uiState
        val settings = uiState.settings
        
        if (!settings.useDebugConfig) {
            val config = viewModel.getSelectedProfileContent()
            if (config.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.select_profile_or_debug), Toast.LENGTH_LONG).show()
                viewModel.updateVpnStatus()
                return
            }
            XrayVpnService.startOrRestart(
                context = this,
                configJson = config,
                profileId = uiState.selectedProfileId,
                enableIpv6 = settings.isIpv6Enabled,
                vpnAddress = settings.vpnAddress,
                vpnDns = settings.vpnDns,
                vpnAddressIpv6 = settings.vpnAddressIpv6,
                vpnDnsIpv6 = settings.vpnDnsIpv6,
                vpnMtu = settings.vpnMtu
            )
        } else {
            val intent = Intent(this, XrayVpnService::class.java).apply {
                putExtra("ENABLE_IPV6", settings.isIpv6Enabled)
                putExtra("VPN_ADDRESS", settings.vpnAddress)
                putExtra("VPN_DNS", settings.vpnDns)
                putExtra("VPN_ADDRESS_IPV6", settings.vpnAddressIpv6)
                putExtra("VPN_DNS_IPV6", settings.vpnDnsIpv6)
                putExtra("VPN_MTU", settings.vpnMtu)
            }
            ContextCompat.startForegroundService(this, intent)
        }
        
        val bindIntent = Intent(this, XrayVpnService::class.java)
        bindService(bindIntent, connection, BIND_AUTO_CREATE)
        viewModel.updateVpnStatus()
    }

    private fun stopVpnService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val intent = Intent(this, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        startService(intent)
        viewModel.updateVpnStatus()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}

@Composable
fun AppContent(
    viewModel: MainViewModel,
    currentScreen: Screen,
    isVpnEnabled: Boolean,
    useDebugConfig: Boolean,
    isIpv6Enabled: Boolean,
    vpnAddress: String,
    vpnDns: String,
    vpnAddressIpv6: String,
    vpnDnsIpv6: String,
    vpnMtu: Int,
    onNavigate: (Screen) -> Unit,
    onToggleVpn: (Boolean) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onToggleIpv6: (Boolean) -> Unit,
    onUpdateTunnel: (String, String, String, String, Int) -> Unit
) {
    val isTv = TvUtils.isTv(LocalContext.current)

    if (isTv) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                Screen.entries.forEach { screen ->
                    val label = stringResource(screen.labelRes)
                    NavigationRailItem(
                        selected = currentScreen == screen,
                        onClick = { onNavigate(screen) },
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(text = label) }
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize().weight(1f),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                    ScreenContent(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        isVpnEnabled = isVpnEnabled,
                        useDebugConfig = useDebugConfig,
                        isIpv6Enabled = isIpv6Enabled,
                        isTv = true,
                        vpnAddress = vpnAddress,
                        vpnDns = vpnDns,
                        vpnAddressIpv6 = vpnAddressIpv6,
                        vpnDnsIpv6 = vpnDnsIpv6,
                        vpnMtu = vpnMtu,
                        onToggleVpn = onToggleVpn,
                        onToggleDebug = onToggleDebug,
                        onToggleIpv6 = onToggleIpv6,
                        onUpdateTunnel = onUpdateTunnel
                    )
                }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        val label = stringResource(screen.labelRes)
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { onNavigate(screen) },
                            icon = { Icon(screen.icon, contentDescription = label) },
                            label = { Text(text = label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                ScreenContent(
                    currentScreen = currentScreen,
                    viewModel = viewModel,
                    isVpnEnabled = isVpnEnabled,
                    useDebugConfig = useDebugConfig,
                    isIpv6Enabled = isIpv6Enabled,
                    isTv = false,
                    vpnAddress = vpnAddress,
                    vpnDns = vpnDns,
                    vpnAddressIpv6 = vpnAddressIpv6,
                    vpnDnsIpv6 = vpnDnsIpv6,
                    vpnMtu = vpnMtu,
                    onToggleVpn = onToggleVpn,
                    onToggleDebug = onToggleDebug,
                    onToggleIpv6 = onToggleIpv6,
                    onUpdateTunnel = onUpdateTunnel
                )
            }
        }
    }
}

@Composable
fun ScreenContent(
    currentScreen: Screen,
    viewModel: MainViewModel,
    isVpnEnabled: Boolean,
    useDebugConfig: Boolean,
    isIpv6Enabled: Boolean,
    isTv: Boolean,
    vpnAddress: String,
    vpnDns: String,
    vpnAddressIpv6: String,
    vpnDnsIpv6: String,
    vpnMtu: Int,
    onToggleVpn: (Boolean) -> Unit,
    onToggleDebug: (Boolean) -> Unit,
    onToggleIpv6: (Boolean) -> Unit,
    onUpdateTunnel: (String, String, String, String, Int) -> Unit
) {
    AnimatedContent(
        targetState = currentScreen,
        label = "ScreenSwitch",
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
    ) { targetScreen ->
        when (targetScreen) {
            Screen.Main -> MainScreen(isVpnEnabled = isVpnEnabled, onToggleVpn = onToggleVpn)
            Screen.Profiles -> ProfilesScreen(viewModel = viewModel)
            Screen.Settings -> SettingsScreen(
                useDebugConfig = useDebugConfig,
                isIpv6Enabled = isIpv6Enabled,
                isVpnEnabled = isVpnEnabled,
                vpnAddress = vpnAddress,
                vpnDns = vpnDns,
                vpnAddressIpv6 = vpnAddressIpv6,
                vpnDnsIpv6 = vpnDnsIpv6,
                vpnMtu = vpnMtu,
                isDeepLinkVerified = viewModel.uiState.isDeepLinkVerified,
                isTv = isTv,
                onToggleDebug = onToggleDebug,
                onToggleIpv6 = onToggleIpv6,
                onUpdateTunnel = onUpdateTunnel,
                onOpenDeepLinkSettings = { viewModel.openDeepLinkSettings() }
            )
            Screen.Logs -> LogsScreen()
        }
    }
}
