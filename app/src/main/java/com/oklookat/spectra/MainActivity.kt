package com.oklookat.spectra

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.service.XrayVpnService
import com.oklookat.spectra.ui.SpectraApp
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.ui.viewmodel.MainUiEvent
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.util.DeepLinkHandler
import com.oklookat.spectra.util.ProfileUpdateWorker
import com.oklookat.spectra.util.TvUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var isBound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ProfileUpdateWorker.setupPeriodicWork(this)
        handleIntent(intent)

        setContent {
            SpectraTheme {
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        handleUiEvent(event)
                    }
                }

                SpectraApp(
                    onToggleVpn = { enabled ->
                        if (enabled) tryStartVpn() else stopVpnService()
                    },
                    onRequestBatteryOptimization = { requestBatteryOptimization() },
                    onRequestLocationPermission = { requestLocationPermission() }
                )
            }
        }
    }

    private val requestBatteryOpt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBatteryOptimizationStatus()
    }

    private val requestLocationPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshWifiStatus()
    }

    private fun handleUiEvent(event: MainUiEvent) {
        when (event) {
            is MainUiEvent.ShowToast -> {
                val message = event.messageResId?.let { getString(it, *event.formatArgs.toTypedArray()) } 
                    ?: event.message ?: ""
                Toast.makeText(this, message, if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
            is MainUiEvent.RestartVpn -> startVpnService()
            is MainUiEvent.OpenDeepLinkSettings -> openDeepLinkSettings()
            is MainUiEvent.RequestBatteryOptimization -> requestBatteryOptimization()
            is MainUiEvent.RequestLocationPermission -> requestLocationPermission()
        }
    }

    fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:$packageName".toUri()
                )
                try {
                    requestBatteryOpt.launch(intent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    try {
                        requestBatteryOpt.launch(fallbackIntent)
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.could_not_open_settings), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.battery_opt_status_disabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestLocationPermission() {
        requestLocationPerms.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
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
        if (intent?.getBooleanExtra("show_update_dialog", false) == true) {
            viewModel.checkForUpdatesManually()
        }

        when (val result = DeepLinkHandler.handle(intent)) {
            is DeepLinkHandler.DeepLinkResult.Profile -> {
                viewModel.setDeepLinkProfile(result.pending)
                // Now handled by global state for backward compat or we could use navController
                viewModel.setScreen(Screen.Profiles)
            }
            is DeepLinkHandler.DeepLinkResult.Group -> {
                viewModel.setDeepLinkGroup(result.pending)
                viewModel.setScreen(Screen.Profiles)
            }
            is DeepLinkHandler.DeepLinkResult.Invalid -> {
                Toast.makeText(this, getString(R.string.invalid_link), Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkDeepLinkStatus()
    }

    private fun tryStartVpn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) prepareVpn.launch(vpnIntent) else startVpnService()
    }

    private fun startVpnService() {
        lifecycleScope.launch {
            val config = viewModel.prepareVpnConfig()
            val uiState = viewModel.uiState
            val settings = uiState.settings
            val disallowedPackages = viewModel.getDisallowedPackages()

            if (config.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_profiles_yet), Toast.LENGTH_LONG).show()
                return@launch
            }

            XrayVpnService.startOrRestart(
                context = this@MainActivity,
                configJson = config,
                profileId = uiState.selectedProfileId,
                enableIpv6 = settings.isIpv6Enabled,
                vpnAddress = settings.vpnAddress,
                vpnDns = settings.vpnDns,
                vpnAddressIpv6 = settings.vpnAddressIpv6,
                vpnDnsIpv6 = settings.vpnDnsIpv6,
                vpnMtu = settings.vpnMtu,
                disallowedPackages = ArrayList(disallowedPackages)
            )
            
            val bindIntent = Intent(this@MainActivity, XrayVpnService::class.java)
            if (bindService(bindIntent, connection, BIND_AUTO_CREATE)) {
                isBound = true
            }
        }
    }

    private fun stopVpnService() {
        if (isBound) {
            try { unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
        val intent = Intent(this, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onDestroy() {
        if (isBound) {
            try { unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
        super.onDestroy()
    }
}
