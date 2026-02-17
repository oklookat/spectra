package com.oklookat.spectra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.AppListItem
import com.oklookat.spectra.ui.components.AppSwitch
import com.oklookat.spectra.ui.components.SettingsSectionTitle
import com.oklookat.spectra.ui.viewmodel.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsState,
    isVpnEnabled: Boolean,
    isDeepLinkVerified: Boolean,
    isTv: Boolean,
    onToggleIpv6: (Boolean) -> Unit,
    onUpdateTunnel: (String, String, String, String, Int) -> Unit,
    onOpenResources: () -> Unit
) {
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
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Resources
            SettingsSectionTitle(stringResource(R.string.resources))
            AppListItem(
                headlineContent = { Text(stringResource(R.string.resources)) },
                supportingContent = { Text(stringResource(R.string.manage_geo_files)) },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingContent = {
                    Button(onClick = onOpenResources) {
                        Text(stringResource(R.string.configure))
                    }
                },
                onClick = onOpenResources,
                isTv = isTv
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 2. Connection & IPv6
            SettingsSectionTitle(stringResource(R.string.connection))
            AppListItem(
                headlineContent = { Text(stringResource(R.string.ipv6_support)) },
                supportingContent = { Text(stringResource(R.string.enable_ipv6_tunneling)) },
                leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 3. Tunnel (IPv4/IPv6)
            SettingsSectionTitle(stringResource(R.string.tunnel_ipv4))
            TunnelSettings(
                address = uiState.vpnAddress,
                dns = uiState.vpnDns,
                enabled = !isVpnEnabled,
                onUpdate = { addr, dns -> onUpdateTunnel(addr, dns, uiState.vpnAddressIpv6, uiState.vpnDnsIpv6, uiState.vpnMtu) }
            )

            if (uiState.isIpv6Enabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SettingsSectionTitle(stringResource(R.string.tunnel_ipv6))
                TunnelSettings(
                    address = uiState.vpnAddressIpv6,
                    dns = uiState.vpnDnsIpv6,
                    enabled = !isVpnEnabled,
                    onUpdate = { addr, dns -> onUpdateTunnel(uiState.vpnAddress, uiState.vpnDns, addr, dns, uiState.vpnMtu) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 4. Advanced & Import
            SettingsSectionTitle(stringResource(R.string.advanced))
            
            // MTU Setting
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = !isVpnEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            if(!isTv) {
                Spacer(modifier = Modifier.height(16.dp))
                AppListItem(
                    headlineContent = { Text(stringResource(R.string.supported_links)) },
                    supportingContent = {
                        Text(
                            if (isDeepLinkVerified) stringResource(R.string.supported_links_enabled)
                            else stringResource(R.string.supported_links_disabled)
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                    trailingContent = {
                        if (!isDeepLinkVerified) {
                            Button(onClick = { /* Handled via ProfilesViewModel events */ }) {
                                Text(stringResource(R.string.configure))
                            }
                        }
                    }
                )
            }

            // 5. App (Updates)
            if (BuildConfig.UPDATE_URL.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SettingsSectionTitle(stringResource(R.string.app_updates))
                AppListItem(
                    headlineContent = { Text(stringResource(R.string.app_updates)) },
                    supportingContent = { Text(stringResource(R.string.check_for_updates)) },
                    leadingContent = { Icon(Icons.Default.Update, contentDescription = null) },
                    trailingContent = {
                        Button(onClick = { /* Handled via MainViewModel via global events */ }) {
                            Text(stringResource(R.string.refresh))
                        }
                    },
                    isTv = isTv
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TunnelSettings(
    address: String,
    dns: String,
    enabled: Boolean,
    onUpdate: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = { onUpdate(it, dns) },
            label = { Text(stringResource(R.string.address)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )
        OutlinedTextField(
            value = dns,
            onValueChange = { onUpdate(address, it) },
            label = { Text(stringResource(R.string.dns_server)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )
    }
}
