package com.oklookat.spectra.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.ui.components.LinkItem
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.ui.viewmodel.HomeUiState
import com.oklookat.spectra.util.TvUtils
import libv2ray.Libv2ray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: HomeUiState,
    onToggleVpn: (Boolean) -> Unit,
    onOpenProfiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)

    if (isTv) {
        TvMainScreen(isVpnEnabled = uiState.isVpnEnabled, onToggleVpn = onToggleVpn)
    } else {
        MobileMainScreen(
            uiState = uiState,
            onToggleVpn = onToggleVpn,
            onOpenProfiles = onOpenProfiles
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileMainScreen(
    uiState: HomeUiState,
    onToggleVpn: (Boolean) -> Unit,
    onOpenProfiles: () -> Unit = {}
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
                    isVpnEnabled = uiState.isVpnEnabled,
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
                    isVpnEnabled = uiState.isVpnEnabled,
                    onToggleVpn = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleVpn(it)
                    },
                    modifier = Modifier.size(240.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                val activeProfile = uiState.profiles.find { it.id == uiState.selectedProfileId }
                val activeGroup = uiState.groups.find { it.id == activeProfile?.groupId }

                ActiveProfileCard(
                    profile = activeProfile,
                    groupName = activeGroup?.name,
                    isVpnEnabled = uiState.isVpnEnabled,
                    onClick = onOpenProfiles
                )

                Spacer(modifier = Modifier.weight(1f))

                AppVersionInfo()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profile: Profile?,
    groupName: String?,
    isVpnEnabled: Boolean,
    onClick: () -> Unit = {}
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            1.dp,
            if (isVpnEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isVpnEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isVpnEnabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isVpnEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isVpnEnabled) stringResource(R.string.notification_content_active) else stringResource(R.string.disconnected),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isVpnEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!groupName.isNullOrBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = profile?.name ?: stringResource(R.string.no_profile_selected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        animationMode = MarqueeAnimationMode.Immediately
                    )
                )
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
            .padding(24.dp),
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

        TvVpnToggleButton(
            isVpnEnabled = isVpnEnabled,
            onToggleVpn = onToggleVpn,
            modifier = Modifier.size(280.dp)
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
private fun TvVpnToggleButton(
    isVpnEnabled: Boolean,
    onToggleVpn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )

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

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onToggleVpn(!isVpnEnabled) },
        contentAlignment = Alignment.Center
    ) {
        // Focus ring/glow
        if (isFocused) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
            ) {}
        }

        Surface(
            modifier = Modifier.fillMaxSize(0.85f),
            shape = CircleShape,
            color = buttonColor,
            tonalElevation = if (isFocused) 8.dp else 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isVpnEnabled) stringResource(R.string.disable_vpn) else stringResource(R.string.enable_vpn),
                    modifier = Modifier.fillMaxSize(0.5f),
                    tint = iconColor
                )
            }
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

    Surface(
        onClick = { onToggleVpn(!isVpnEnabled) },
        modifier = modifier,
        shape = CircleShape,
        color = buttonColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = if (isVpnEnabled)
                    stringResource(R.string.disable_vpn)
                else
                    stringResource(R.string.enable_vpn),
                modifier = Modifier.fillMaxSize(0.4f),
                tint = iconColor
            )
        }
    }
}

@Composable
private fun AppVersionInfo() {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current

    val appVersion = BuildConfig.VERSION_NAME

    val xrayFullVersion = if (isPreview) "v26.10.10 (Lib v26.10.10, core v26.10.10)" else try {
        Libv2ray.checkVersionX()
    } catch (_: Throwable) {
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

@Preview
@Composable
fun MainScreenPreview() {
    SpectraTheme {
        MainScreen(
            uiState = HomeUiState(isVpnEnabled = true),
            onToggleVpn = {}
        )
    }
}
