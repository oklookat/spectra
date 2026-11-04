package com.oklookat.spectra.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.ui.components.AppUpdateDialog
import com.oklookat.spectra.ui.screens.*
import com.oklookat.spectra.ui.viewmodel.*
import com.oklookat.spectra.util.TvUtils

@Composable
fun SpectraApp(
    viewModel: MainViewModel = hiltViewModel(),
    onToggleVpn: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onRequestDeepLinkSettings: () -> Unit = {}
) {
    val uiState = viewModel.uiState
    val navController = rememberNavController()
    val isTv = TvUtils.isTv(LocalContext.current)

    // Dialogs
    uiState.availableUpdate?.let { update ->
        AppUpdateDialog(
            versionName = update.versionName,
            changelog = update.changelog,
            isDownloading = uiState.isDownloadingUpdate,
            progress = uiState.updateDownloadProgress,
            onConfirm = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { viewModel.setAvailableUpdate(null) }
        )
    }

    if (isTv) {
        TvAppStructure(
            navController = navController,
            onToggleVpn = onToggleVpn,
            onRequestBatteryOptimization = onRequestBatteryOptimization,
            onRequestLocationPermission = onRequestLocationPermission,
            onRequestDeepLinkSettings = onRequestDeepLinkSettings
        )
    } else {
        MobileAppStructure(
            navController = navController,
            onToggleVpn = onToggleVpn,
            onRequestBatteryOptimization = onRequestBatteryOptimization,
            onRequestLocationPermission = onRequestLocationPermission,
            onRequestDeepLinkSettings = onRequestDeepLinkSettings
        )
    }
}

@Composable
private fun MobileAppStructure(
    navController: NavHostController,
    onToggleVpn: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestDeepLinkSettings: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = Screen.fromRoute(currentRoute)

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.filter { it.showInNav }.forEach { screen ->
                    val label = stringResource(screen.labelRes)
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Main.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(text = label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            AppNavigation(
                navController = navController,
                onToggleVpn = onToggleVpn,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestDeepLinkSettings = onRequestDeepLinkSettings
            )
        }
    }
}

@Composable
private fun TvAppStructure(
    navController: NavHostController,
    onToggleVpn: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestDeepLinkSettings: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = Screen.fromRoute(currentRoute)

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Screen.entries.filter { it.showInNav }.forEach { screen ->
                val label = stringResource(screen.labelRes)
                NavigationRailItem(
                    selected = currentScreen == screen,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Main.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(screen.icon, contentDescription = label) },
                    label = { Text(text = label) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize().weight(1f),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                AppNavigation(
                    navController = navController,
                    onToggleVpn = onToggleVpn,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onRequestDeepLinkSettings = onRequestDeepLinkSettings
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    onToggleVpn: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestDeepLinkSettings: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        enterTransition = { fadeIn(tween(400)) },
        exitTransition = { fadeOut(tween(400)) }
    ) {
        composable(Screen.Main.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            MainScreen(
                uiState = homeViewModel.uiState,
                onToggleVpn = onToggleVpn,
                onOpenProfiles = {
                    navController.navigate(Screen.Profiles.route) {
                        popUpTo(Screen.Main.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.Profiles.route) {
            val profilesViewModel: ProfilesViewModel = hiltViewModel()
            ProfilesScreen(viewModel = profilesViewModel)
        }
        composable(Screen.Logs.route) {
            LogsScreen(
                onRequestRestartVpn = { onToggleVpn(true) }
            )
        }
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val homeViewModel: HomeViewModel = hiltViewModel()
            val profilesViewModel: ProfilesViewModel = hiltViewModel()
            val isTv = TvUtils.isTv(LocalContext.current)
            
            SettingsScreen(
                uiState = settingsViewModel.uiState,
                isVpnEnabled = homeViewModel.uiState.isVpnEnabled,
                isDeepLinkVerified = profilesViewModel.uiState.isDeepLinkVerified,
                isTv = isTv,
                onToggleIpv6 = { settingsViewModel.toggleIpv6(it) },
                onToggleTunMode = { settingsViewModel.toggleTunMode(it) },
                onUpdateSocksPort = { settingsViewModel.updateSocksPort(it) },
                onUpdateTunnel = { addr, dns, addr6, dns6, mtu ->
                    settingsViewModel.updateTunnelSettings(addr, dns, addr6, dns6, mtu)
                },
                onOpenResources = { navController.navigate(Screen.Resources.route) },
                onOpenAppFilter = { navController.navigate(Screen.AppFilter.route) },
                onOpenWifiExclusions = { navController.navigate(Screen.WifiExclusions.route) },
                onOpenDnsRouting = { navController.navigate(Screen.DnsRouting.route) },
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onOpenDeepLinkSettings = onRequestDeepLinkSettings,
                onToggleWifiExclusion = { settingsViewModel.toggleWifiExclusion(it) },
                onAddWifiSsid = { settingsViewModel.addWifiSsid(it) },
                onRemoveWifiSsid = { settingsViewModel.removeWifiSsid(it) },
                onRequestLocationPermission = onRequestLocationPermission,
                onRefreshStatus = {
                    settingsViewModel.refreshBatteryOptimizationStatus()
                    settingsViewModel.refreshWifiStatus()
                },
                onUpdateP2PShareMode = { settingsViewModel.updateP2PShareMode(it) },
                onOpenP2PDebug = { navController.navigate(Screen.P2PDebug.route) },
                onGetActiveConfig = { callback -> settingsViewModel.getActiveConfigJson(callback) }
            )
        }
        composable(Screen.Resources.route) {
            val resourcesViewModel: ResourcesViewModel = hiltViewModel()
            ResourcesScreen(
                viewModel = resourcesViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AppFilter.route) {
            val appFilterViewModel: AppFilterViewModel = hiltViewModel()
            AppFilterScreen(
                viewModel = appFilterViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.WifiExclusions.route) {
            val wifiExclusionsViewModel: WifiExclusionsViewModel = hiltViewModel()
            WifiExclusionsScreen(
                viewModel = wifiExclusionsViewModel,
                onBack = { navController.popBackStack() },
                onRequestLocationPermission = onRequestLocationPermission
            )
        }
        composable(Screen.DnsRouting.route) {
            val dnsRoutingViewModel: DnsRoutingViewModel = hiltViewModel()
            DnsRoutingScreen(
                viewModel = dnsRoutingViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.P2PDebug.route) {
            val isTv = TvUtils.isTv(LocalContext.current)
            P2PDebugScreen(
                onBack = { navController.popBackStack() },
                isTv = isTv
            )
        }
    }
}
