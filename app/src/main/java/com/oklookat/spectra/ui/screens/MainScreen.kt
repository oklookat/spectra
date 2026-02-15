package com.oklookat.spectra.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.LinkItem
import com.oklookat.spectra.util.TvUtils
import libv2ray.Libv2ray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isVpnEnabled: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)

    if (isTv) {
        TvMainScreen(isVpnEnabled = isVpnEnabled, onToggleVpn = onToggleVpn)
    } else {
        MobileMainScreen(isVpnEnabled = isVpnEnabled, onToggleVpn = onToggleVpn)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileMainScreen(
    isVpnEnabled: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            if (!isLandscape) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            if (isLandscape) {
                VpnToggleButton(
                    isVpnEnabled = isVpnEnabled,
                    onToggleVpn = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleVpn(it)
                    },
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    ) { padding ->
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AppVersionInfo()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                VpnToggleButton(
                    isVpnEnabled = isVpnEnabled,
                    onToggleVpn = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleVpn(it)
                    },
                    modifier = Modifier.size(240.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                AppVersionInfo()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TvMainScreen(
    isVpnEnabled: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        VpnToggleButton(
            isVpnEnabled = isVpnEnabled,
            onToggleVpn = onToggleVpn,
            modifier = Modifier.size(260.dp)
        )
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            AppVersionInfo()
        }
    }
}

@Composable
private fun VpnToggleButton(
    isVpnEnabled: Boolean,
    onToggleVpn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor by animateColorAsState(
        if (isVpnEnabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "buttonColor"
    )

    val iconColor by animateColorAsState(
        if (isVpnEnabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "iconColor"
    )

    BoxWithConstraints(modifier = modifier) {
        val maxButtonSize = minOf(this.maxWidth, this.maxHeight)
        val iconSize = maxButtonSize * 0.4f

        FilledIconButton(
            onClick = { onToggleVpn(!isVpnEnabled) },
            modifier = Modifier.size(maxButtonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = buttonColor,
                contentColor = iconColor
            )
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = if (isVpnEnabled) stringResource(R.string.disable_vpn) else stringResource(R.string.enable_vpn),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun AppVersionInfo() {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)
    
    val appVersion = BuildConfig.VERSION_NAME

    val xrayFullVersion = try {
        Libv2ray.checkVersionX()
    } catch (e: Exception) {
        ""
    }

    val libVersion = "v" + xrayFullVersion.substringAfter("Lib v").substringBefore(",")
    val coreVersion = "v" + xrayFullVersion.substringAfter("core v").substringBefore(")")

    val links = @Composable {
        LinkItem("oklookat/spectra v$appVersion", "https://github.com/oklookat/spectra")
        LinkItem("2dust/AndroidLibXrayLite $libVersion", "https://github.com/2dust/AndroidLibXrayLite")
        LinkItem("XTLS/Xray-core $coreVersion", "https://github.com/XTLS/Xray-core")
    }

    if (isTv) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            links()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            links()
        }
    }
}
