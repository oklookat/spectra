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
import androidx.hilt.navigation.compose.hiltViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.ui.components.AppUpdateDialog
import com.oklookat.spectra.ui.components.EasyImportVerifyDialog
import com.oklookat.spectra.ui.components.ReplaceConfirmDialog
import com.oklookat.spectra.ui.screens.*
import com.oklookat.spectra.ui.viewmodel.*
import com.oklookat.spectra.util.TvUtils

@Composable
fun SpectraApp(
    viewModel: MainViewModel = hiltViewModel(),
    onToggleVpn: (Boolean) -> Unit
) {
    val uiState = viewModel.uiState
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
                currentScreen = uiState.currentScreen,
                onSetScreen = { viewModel.setScreen(it) },
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
                    currentScreen = uiState.currentScreen,
                    onSetScreen = { viewModel.setScreen(it) },
                    isTv = true,
                    onToggleVpn = onToggleVpn
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    currentScreen: Screen,
    onSetScreen: (Screen) -> Unit,
    isTv: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    AnimatedContent(
        targetState = currentScreen,
        label = "ScreenSwitch",
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
    ) { targetScreen ->
        when (targetScreen) {
            Screen.Main -> {
                val homeViewModel: HomeViewModel = hiltViewModel()
                MainScreen(
                    uiState = homeViewModel.uiState,
                    onToggleVpn = onToggleVpn,
                    onSelectProfile = { homeViewModel.selectProfile(it) }
                )
            }
            Screen.Profiles -> {
                val profilesViewModel: ProfilesViewModel = hiltViewModel()
                ProfilesScreen(viewModel = profilesViewModel)
            }
            Screen.Settings -> {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val homeViewModel: HomeViewModel = hiltViewModel()
                val profilesViewModel: ProfilesViewModel = hiltViewModel()
                
                SettingsScreen(
                    uiState = settingsViewModel.uiState,
                    isVpnEnabled = homeViewModel.uiState.isVpnEnabled,
                    isDeepLinkVerified = profilesViewModel.uiState.isDeepLinkVerified,
                    isTv = isTv,
                    onToggleDebug = { settingsViewModel.toggleDebugConfig(it) },
                    onToggleIpv6 = { settingsViewModel.toggleIpv6(it) },
                    onUpdateTunnel = { addr, dns, addr6, dns6, mtu ->
                        settingsViewModel.updateTunnelSettings(addr, dns, addr6, dns6, mtu)
                    },
                    onOpenDeepLinkSettings = { /* Handled in ProfilesViewModel via events */ },
                    onCheckUpdates = { /* Handled in MainViewModel via global events if needed */ },
                    onOpenResources = { onSetScreen(Screen.Resources) }
                )
            }
            Screen.Logs -> LogsScreen()
            Screen.Resources -> {
                val resourcesViewModel: ResourcesViewModel = hiltViewModel()
                ResourcesScreen(
                    viewModel = resourcesViewModel,
                    onBack = { onSetScreen(Screen.Settings) }
                )
            }
        }
    }
}
