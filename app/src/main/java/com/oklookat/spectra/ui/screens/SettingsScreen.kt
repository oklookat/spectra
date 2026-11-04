package com.oklookat.spectra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.AppButton
import com.oklookat.spectra.ui.components.AppListItem
import com.oklookat.spectra.ui.components.AppSwitch
import com.oklookat.spectra.ui.components.AppTextButton
import com.oklookat.spectra.ui.components.SettingsSectionTitle
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.ui.viewmodel.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsState,
    isVpnEnabled: Boolean,
    isDeepLinkVerified: Boolean,
    isTv: Boolean,
    onToggleIpv6: (Boolean) -> Unit,
    onToggleTunMode: (Boolean) -> Unit = {},
    onUpdateSocksPort: (Int) -> Unit = {},
    onUpdateTunnel: (String, String, String, String, Int) -> Unit,
    onOpenResources: () -> Unit,
    onOpenAppFilter: () -> Unit,
    onOpenWifiExclusions: () -> Unit = {},
    onOpenDnsRouting: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onOpenDeepLinkSettings: () -> Unit = {},
    onToggleWifiExclusion: (Boolean) -> Unit = {},
    onAddWifiSsid: (String) -> Unit = {},
    onRemoveWifiSsid: (String) -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    onRefreshStatus: () -> Unit = {},
    onUpdateP2PShareMode: (String) -> Unit = {},
    onOpenP2PDebug: () -> Unit = {},
    onGetActiveConfig: ((String) -> Unit) -> Unit = { it("") }
) {
    var showActiveConfigDialog by remember { mutableStateOf(false) }
    var activeConfigJsonState by remember { mutableStateOf("") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRefreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Rules, Filters & Routing
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.app_filter))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column {
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.app_filter)) },
                            supportingContent = { Text(stringResource(R.string.app_filter_desc)) },
                            leadingContent = { Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                            onClick = onOpenAppFilter,
                            isTv = isTv
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.dns_routing_rules)) },
                            supportingContent = { Text(stringResource(R.string.dns_routing_desc)) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                            onClick = onOpenDnsRouting,
                            isTv = isTv
                        )

                        if (!isTv) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            AppListItem(
                                headlineContent = { Text(stringResource(R.string.wifi_exclusions)) },
                                supportingContent = { Text(stringResource(R.string.wifi_exclusions_desc)) },
                                leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                                onClick = onOpenWifiExclusions,
                                isTv = false
                            )
                        }
                    }
                }
            }

            // 2. Battery & System Optimization
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.battery_optimization))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.BatterySaver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.battery_optimization),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.isIgnoringBatteryOptimizations) stringResource(R.string.battery_opt_status_disabled)
                                    else stringResource(R.string.battery_opt_status_enabled),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.isIgnoringBatteryOptimizations) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (!uiState.isIgnoringBatteryOptimizations) {
                            FilledTonalButton(
                                onClick = onRequestBatteryOptimization,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                }
            }

            // 3. Resources & Geo Files
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.resources))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    AppListItem(
                        headlineContent = { Text(stringResource(R.string.resources)) },
                        supportingContent = { Text(stringResource(R.string.manage_geo_files)) },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                        onClick = onOpenResources,
                        isTv = isTv
                    )
                }
            }

            // 4. Connection & Network Tunnel Settings
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.connection))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.ipv6_support)) },
                            supportingContent = { Text(stringResource(R.string.enable_ipv6_tunneling)) },
                            leadingContent = { Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                AppSwitch(
                                    checked = uiState.isIpv6Enabled,
                                    onCheckedChange = onToggleIpv6,
                                    enabled = !isVpnEnabled,
                                    isTv = isTv
                                )
                            },
                            isTv = isTv
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.tun_mode)) },
                            supportingContent = { Text(stringResource(R.string.tun_mode_desc)) },
                            leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                AppSwitch(
                                    checked = uiState.isTunModeEnabled,
                                    onCheckedChange = onToggleTunMode,
                                    enabled = !isVpnEnabled,
                                    isTv = isTv
                                )
                            },
                            isTv = isTv
                        )
                        
                        var socksPortText by remember(uiState.socksPort) { mutableStateOf(uiState.socksPort.toString()) }
                        OutlinedTextField(
                            value = socksPortText,
                            onValueChange = { 
                                socksPortText = it
                                it.toIntOrNull()?.let { port ->
                                    onUpdateSocksPort(port)
                                }
                            },
                            label = { Text(stringResource(R.string.socks_port)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            enabled = !isVpnEnabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                        Text(
                            text = stringResource(R.string.tunnel_ipv4),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        TunnelSettings(
                            address = uiState.vpnAddress,
                            dns = uiState.vpnDns,
                            enabled = !isVpnEnabled,
                            onUpdate = { addr, dns -> onUpdateTunnel(addr, dns, uiState.vpnAddressIpv6, uiState.vpnDnsIpv6, uiState.vpnMtu) }
                        )

                        if (uiState.isIpv6Enabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                            Text(
                                text = stringResource(R.string.tunnel_ipv6),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            TunnelSettings(
                                address = uiState.vpnAddressIpv6,
                                dns = uiState.vpnDnsIpv6,
                                enabled = !isVpnEnabled,
                                onUpdate = { addr, dns -> onUpdateTunnel(uiState.vpnAddress, uiState.vpnDns, addr, dns, uiState.vpnMtu) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

                        var mtuText by remember(uiState.vpnMtu) { mutableStateOf(uiState.vpnMtu.toString()) }
                        OutlinedTextField(
                            value = mtuText,
                            onValueChange = { 
                                mtuText = it
                                it.toIntOrNull()?.let { mtu ->
                                    onUpdateTunnel(uiState.vpnAddress, uiState.vpnDns, uiState.vpnAddressIpv6, uiState.vpnDnsIpv6, mtu)
                                }
                            },
                            label = { Text(stringResource(R.string.mtu)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            enabled = !isVpnEnabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Transfer & Sharing Mode
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.p2p_share_mode))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.p2p_mode_direct_qr)) },
                            supportingContent = { Text(stringResource(R.string.p2p_share_mode_desc)) },
                            leadingContent = {
                                RadioButton(
                                    selected = uiState.p2pShareMode == "direct_qr",
                                    onClick = { onUpdateP2PShareMode("direct_qr") }
                                )
                            },
                            onClick = { onUpdateP2PShareMode("direct_qr") },
                            isTv = isTv
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.p2p_mode_server)) },
                            supportingContent = { Text(stringResource(R.string.p2p_share_mode_desc)) },
                            leadingContent = {
                                RadioButton(
                                    selected = uiState.p2pShareMode == "server",
                                    onClick = { onUpdateP2PShareMode("server") }
                                )
                            },
                            onClick = { onUpdateP2PShareMode("server") },
                            isTv = isTv
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.p2p_debug)) },
                            supportingContent = { Text(stringResource(R.string.p2p_debug_desc)) },
                            leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                            onClick = onOpenP2PDebug,
                            isTv = isTv
                        )
                    }
                }
            }

            // 5. Advanced & Executable Active Config
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSectionTitle(stringResource(R.string.advanced))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column {
                        AppListItem(
                            headlineContent = { Text(stringResource(R.string.active_config)) },
                            supportingContent = { Text(stringResource(R.string.active_config_desc)) },
                            leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                            onClick = {
                                onGetActiveConfig { config ->
                                    activeConfigJsonState = config
                                    showActiveConfigDialog = true
                                }
                            },
                            isTv = isTv
                        )

                        if (!isTv) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            AppListItem(
                                headlineContent = { Text(stringResource(R.string.supported_links)) },
                                supportingContent = {
                                    Text(
                                        if (isDeepLinkVerified) stringResource(R.string.supported_links_enabled)
                                        else stringResource(R.string.supported_links_disabled)
                                    )
                                },
                                leadingContent = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) },
                                onClick = onOpenDeepLinkSettings,
                                isTv = isTv
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showActiveConfigDialog) {
        ActiveConfigDialog(
            configJson = activeConfigJsonState,
            onDismiss = { showActiveConfigDialog = false }
        )
    }
}

@Composable
fun TunnelSettings(
    address: String,
    dns: String,
    enabled: Boolean,
    onUpdate: (String, String) -> Unit
) {
    var addressText by remember(address) { mutableStateOf(address) }
    var dnsText by remember(dns) { mutableStateOf(dns) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = addressText,
            onValueChange = {
                addressText = it
                onUpdate(it, dnsText)
            },
            label = { Text(stringResource(R.string.address)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )
        OutlinedTextField(
            value = dnsText,
            onValueChange = {
                dnsText = it
                onUpdate(addressText, it)
            },
            label = { Text(stringResource(R.string.dns_server)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )
    }
}

@Composable
private fun ActiveConfigDialog(
    configJson: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val copiedText = stringResource(R.string.config_copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.active_config_dialog_title)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = configJson.ifBlank { "No active config available" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            AppButton(onClick = {
                if (configJson.isNotBlank()) {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(configJson))
                    android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(stringResource(R.string.copy_logs))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun SettingsScreenPreview() {
    SpectraTheme {
        SettingsScreen(
            uiState = SettingsState(
                isIpv6Enabled = true,
                vpnAddress = "10.0.0.1",
                vpnDns = "10.0.0.2",
                excludedWifiSsids = setOf("Home_WiFi", "Office_5G"),
                currentWifiSsid = "Home_WiFi"
            ),
            isVpnEnabled = false,
            isDeepLinkVerified = true,
            isTv = false,
            onToggleIpv6 = {},
            onUpdateTunnel = { _, _, _, _, _ -> },
            onOpenResources = {},
            onOpenAppFilter = {}
        )
    }
}
