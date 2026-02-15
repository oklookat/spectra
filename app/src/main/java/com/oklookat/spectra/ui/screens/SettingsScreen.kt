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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.R
import com.oklookat.spectra.util.TvUtils

@Composable
fun SettingsScreen(
    useDebugConfig: Boolean,
    isIpv6Enabled: Boolean,
    isVpnEnabled: Boolean,
    vpnAddress: String,
    vpnDns: String,
    vpnAddressIpv6: String,
    vpnDnsIpv6: String,
    vpnMtu: Int,
    isDeepLinkVerified: Boolean,
    isTv: Boolean,
    onToggleDebug: (Boolean) -> Unit,
    onToggleIpv6: (Boolean) -> Unit,
    onUpdateTunnel: (String, String, String, String, Int) -> Unit,
    onOpenDeepLinkSettings: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        SettingsSectionTitle(stringResource(R.string.connection))

        ListItem(
            headlineContent = { Text(stringResource(R.string.ipv6_support)) },
            supportingContent = { Text(stringResource(R.string.enable_ipv6_tunneling)) },
            leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = isIpv6Enabled,
                    onCheckedChange = onToggleIpv6,
                    enabled = !isVpnEnabled
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSectionTitle(stringResource(R.string.tunnel_ipv4))

        TunnelSettings(
            address = vpnAddress,
            dns = vpnDns,
            enabled = !isVpnEnabled,
            onUpdate = { addr, dns -> onUpdateTunnel(addr, dns, vpnAddressIpv6, vpnDnsIpv6, vpnMtu) }
        )

        if (isIpv6Enabled) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(R.string.tunnel_ipv6))
            TunnelSettings(
                address = vpnAddressIpv6,
                dns = vpnDnsIpv6,
                enabled = !isVpnEnabled,
                onUpdate = { addr, dns -> onUpdateTunnel(vpnAddress, vpnDns, addr, dns, vpnMtu) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        SettingsSectionTitle(stringResource(R.string.advanced))

        // MTU Setting
        var mtuText by remember(vpnMtu) { mutableStateOf(vpnMtu.toString()) }
        OutlinedTextField(
            value = mtuText,
            onValueChange = { 
                mtuText = it
                it.toIntOrNull()?.let { mtu ->
                    onUpdateTunnel(vpnAddress, vpnDns, vpnAddressIpv6, vpnDnsIpv6, mtu)
                }
            },
            label = { Text(stringResource(R.string.mtu)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            enabled = !isVpnEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        if(!isTv) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(R.string.easy_import_title))
            ListItem(
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
                        Button(onClick = onOpenDeepLinkSettings) {
                            Text(stringResource(R.string.configure))
                        }
                    }
                }
            )
        }

        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(R.string.app_updates))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_updates)) },
                supportingContent = { Text(stringResource(R.string.check_for_updates)) },
                leadingContent = { Icon(Icons.Default.Update, contentDescription = null) },
                trailingContent = {
                    Button(onClick = onCheckUpdates) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            )
        }

        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsSectionTitle(stringResource(R.string.debug_mode))
            ListItem(
                headlineContent = { Text(stringResource(R.string.debug_mode)) },
                supportingContent = { Text(stringResource(R.string.use_test_config)) },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = useDebugConfig,
                        onCheckedChange = onToggleDebug,
                        enabled = !isVpnEnabled
                    )
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
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

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
