package com.oklookat.spectra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.ui.components.AppUpdateDialog
import com.oklookat.spectra.ui.components.EasyImportVerifyDialog
import com.oklookat.spectra.ui.components.ReplaceConfirmDialog
import com.oklookat.spectra.ui.screens.LogsScreen
import com.oklookat.spectra.ui.screens.MainScreen
import com.oklookat.spectra.ui.screens.ProfilesScreen
import com.oklookat.spectra.ui.screens.ResourcesScreen
import com.oklookat.spectra.ui.screens.SettingsScreen
import com.oklookat.spectra.ui.viewmodel.MainUiState
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.util.TvUtils

@Composable
fun SpectraApp(
    viewModel: MainViewModel,
    onToggleVpn: (Boolean) -> Unit
) {
    val uiState = viewModel.uiState
    val isTv = TvUtils.isTv(LocalContext.current)

    // Handle Deep Link Profile logic
    LaunchedEffect(uiState.deepLinkProfile) {
        uiState.deepLinkProfile?.let { dp ->
            val existing = uiState.profiles.find { it.name == dp.name }
            if (existing != null) {
                viewModel.setPendingProfileToReplace(dp)
            } else {
                viewModel.saveRemoteProfile(null, dp.name, dp.url, dp.autoUpdate, dp.interval) {
                    // Success handled by ViewModel events/toasts
                }
                viewModel.setDeepLinkProfile(null)
            }
        }
    }

    // Handle Deep Link Group logic
    LaunchedEffect(uiState.deepLinkGroup) {
        uiState.deepLinkGroup?.let { dg ->
            val existing = uiState.groups.find { it.name == dg.name }
            if (existing != null) {
                viewModel.setPendingGroupToReplace(dg)
            } else {
                viewModel.saveGroup(null, dg.name, dg.url, dg.autoUpdate, dg.interval) {
                    // Success handled by ViewModel events/toasts
                }
                viewModel.setDeepLinkGroup(null)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.triggerDeepLinkVerifyDialogIfNeeded()
    }

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

    uiState.pendingProfileToReplace?.let { dp ->
        ReplaceConfirmDialog(
            title = stringResource(R.string.replace_profile_q),
            text = stringResource(R.string.profile_exists_replace_q, dp.name),
            onConfirm = {
                uiState.profiles.find { it.name == dp.name }?.let { existing ->
                    viewModel.replaceRemoteProfile(existing, dp.url, dp.autoUpdate, dp.interval)
                }
                viewModel.setPendingProfileToReplace(null)
                viewModel.setDeepLinkProfile(null)
            },
            onDismiss = {
                viewModel.setPendingProfileToReplace(null)
                viewModel.setDeepLinkProfile(null)
            }
        )
    }

    uiState.pendingGroupToReplace?.let { dg ->
        ReplaceConfirmDialog(
            title = stringResource(R.string.p2p_replace_title),
            text = stringResource(R.string.p2p_replace_msg),
            onConfirm = {
                uiState.groups.find { it.name == dg.name }?.let { existing ->
                    viewModel.replaceRemoteGroup(existing, dg.url, dg.autoUpdate, dg.interval)
                }
                viewModel.setPendingGroupToReplace(null)
                viewModel.setDeepLinkGroup(null)
            },
            onDismiss = {
                viewModel.setPendingGroupToReplace(null)
                viewModel.setDeepLinkGroup(null)
            }
        )
    }

    if (uiState.showDeepLinkVerifyDialog) {
        EasyImportVerifyDialog(
            onConfirm = {
                viewModel.setShowDeepLinkVerifyDialog(false)
                viewModel.openDeepLinkSettings()
            },
            onDismiss = { viewModel.setShowDeepLinkVerifyDialog(false) }
        )
    }

    if (isTv) {
        TvAppStructure(
            viewModel = viewModel,
            uiState = uiState,
            onToggleVpn = onToggleVpn
        )
    } else {
        MobileAppStructure(
            viewModel = viewModel,
            uiState = uiState,
            onToggleVpn = onToggleVpn
        )
    }
}

@Composable
private fun MobileAppStructure(
    viewModel: MainViewModel,
    uiState: MainUiState,
    onToggleVpn: (Boolean) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.filter { it.showInNav }.forEach { screen ->
                    val label = stringResource(screen.labelRes)
                    NavigationBarItem(
                        selected = uiState.currentScreen == screen,
                        onClick = { viewModel.setScreen(screen) },
                        icon = { Icon(screen.icon, contentDescription = label) },
                        label = { Text(text = label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            AppNavigation(
                viewModel = viewModel,
                uiState = uiState,
                isTv = false,
                onToggleVpn = onToggleVpn
            )
        }
    }
}

@Composable
private fun TvAppStructure(
    viewModel: MainViewModel,
    uiState: MainUiState,
    onToggleVpn: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            Screen.entries.filter { it.showInNav }.forEach { screen ->
                val label = stringResource(screen.labelRes)
                NavigationRailItem(
                    selected = uiState.currentScreen == screen,
                    onClick = { viewModel.setScreen(screen) },
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
                AppNavigation(
                    viewModel = viewModel,
                    uiState = uiState,
                    isTv = true,
                    onToggleVpn = onToggleVpn
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    viewModel: MainViewModel,
    uiState: MainUiState,
    isTv: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val settings = uiState.settings
    
    AnimatedContent(
        targetState = uiState.currentScreen,
        label = "ScreenSwitch",
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
    ) { targetScreen ->
        when (targetScreen) {
            Screen.Main -> MainScreen(isVpnEnabled = uiState.isVpnEnabled, onToggleVpn = onToggleVpn)
            Screen.Profiles -> ProfilesScreen(viewModel = viewModel)
            Screen.Settings -> SettingsScreen(
                useDebugConfig = settings.useDebugConfig,
                isIpv6Enabled = settings.isIpv6Enabled,
                isVpnEnabled = uiState.isVpnEnabled,
                vpnAddress = settings.vpnAddress,
                vpnDns = settings.vpnDns,
                vpnAddressIpv6 = settings.vpnAddressIpv6,
                vpnDnsIpv6 = settings.vpnDnsIpv6,
                vpnMtu = settings.vpnMtu,
                isDeepLinkVerified = uiState.isDeepLinkVerified,
                isTv = isTv,
                onToggleDebug = { viewModel.toggleDebugConfig(it) },
                onToggleIpv6 = { viewModel.toggleIpv6(it) },
                onUpdateTunnel = { addr, dns, addr6, dns6, mtu ->
                    viewModel.updateTunnelSettings(addr, dns, addr6, dns6, mtu)
                },
                onOpenDeepLinkSettings = { viewModel.openDeepLinkSettings() },
                onCheckUpdates = { viewModel.checkForUpdatesManually() },
                onOpenResources = { viewModel.setScreen(Screen.Resources) }
            )
            Screen.Logs -> LogsScreen()
            Screen.Resources -> ResourcesScreen(viewModel = viewModel)
        }
    }
}
