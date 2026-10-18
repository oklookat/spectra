package com.oklookat.spectra.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.AppButton
import com.oklookat.spectra.ui.components.AppListItem
import com.oklookat.spectra.ui.components.AppSwitch
import com.oklookat.spectra.ui.components.AppTextButton
import com.oklookat.spectra.ui.components.SettingsSectionTitle
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.ui.viewmodel.MainUiEvent
import com.oklookat.spectra.ui.viewmodel.WifiExclusionsUiState
import com.oklookat.spectra.ui.viewmodel.WifiExclusionsViewModel
import com.oklookat.spectra.util.TvUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiExclusionsScreen(
    viewModel: WifiExclusionsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onRequestLocationPermission: () -> Unit = {}
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTv(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWifiStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainUiEvent.ShowToast -> {
                    val message = event.messageResId?.let { context.getString(it, *event.formatArgs.toTypedArray()) }
                        ?: event.message ?: ""
                    snackbarHostState.showSnackbar(message)
                }
                else -> {}
            }
        }
    }

    var customSsid by remember { mutableStateOf("") }
    var primaryDnsText by remember(uiState.bypassDnsPrimary) { mutableStateOf(uiState.bypassDnsPrimary) }
    var secondaryDnsText by remember(uiState.bypassDnsSecondary) { mutableStateOf(uiState.bypassDnsSecondary) }
    var showEditBypassJsonDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wifi_exclusions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // 1. Master Toggle
            SettingsSectionTitle(stringResource(R.string.wifi_exclusions))
            AppListItem(
                headlineContent = { Text(stringResource(R.string.wifi_exclusions)) },
                supportingContent = { Text(stringResource(R.string.wifi_exclusions_desc)) },
                leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                trailingContent = {
                    AppSwitch(
                        checked = uiState.isWifiExclusionEnabled,
                        onCheckedChange = { viewModel.toggleWifiExclusion(it) },
                        isTv = isTv
                    )
                },
                isTv = isTv
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 2. Wi-Fi SSIDs List
            SettingsSectionTitle(stringResource(R.string.add_wifi_ssid))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.hasLocationPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.location_permission_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.location_permission_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            AppButton(onClick = onRequestLocationPermission) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                } else if (uiState.currentWifiSsid != null) {
                    OutlinedCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.current_wifi_ssid, uiState.currentWifiSsid),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            AppButton(onClick = { viewModel.addWifiSsid(uiState.currentWifiSsid) }) {
                                Text(stringResource(R.string.add_current_wifi))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customSsid,
                        onValueChange = { customSsid = it },
                        placeholder = { Text(stringResource(R.string.wifi_ssid_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (customSsid.isNotBlank()) {
                                viewModel.addWifiSsid(customSsid)
                                customSsid = ""
                            }
                        },
                        enabled = customSsid.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                if (uiState.excludedWifiSsids.isEmpty()) {
                    Text(
                        stringResource(R.string.no_wifi_ssids),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.excludedWifiSsids.forEach { ssid ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(ssid, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { viewModel.removeWifiSsid(ssid) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 3. Bypass DNS Settings
            SettingsSectionTitle(stringResource(R.string.dns_server))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = primaryDnsText,
                    onValueChange = { primaryDnsText = it },
                    label = { Text("Primary Bypass DNS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = secondaryDnsText,
                    onValueChange = { secondaryDnsText = it },
                    label = { Text("Secondary Bypass DNS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppButton(
                        onClick = { viewModel.saveBypassDns(primaryDnsText, secondaryDnsText) },
                        enabled = primaryDnsText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 4. Custom Wi-Fi Bypass Config Override
            SettingsSectionTitle(stringResource(R.string.custom_wifi_bypass_config))
            AppListItem(
                headlineContent = { Text(stringResource(R.string.custom_wifi_bypass_config)) },
                supportingContent = { Text(stringResource(R.string.custom_wifi_bypass_config_desc)) },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                trailingContent = {
                    Button(onClick = { showEditBypassJsonDialog = true }) {
                        Text(stringResource(R.string.configure))
                    }
                },
                onClick = { showEditBypassJsonDialog = true },
                isTv = isTv
            )

            OutlinedButton(
                onClick = { viewModel.resetBypassConfigToDefault() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.reset_to_all_vpn))
            }
        }
    }

    if (showEditBypassJsonDialog) {
        EditBypassConfigDialog(
            initialConfigJson = viewModel.getActiveBypassConfigJson(),
            onDismiss = { showEditBypassJsonDialog = false },
            onSave = { json ->
                viewModel.saveCustomBypassConfigJson(json)
                showEditBypassJsonDialog = false
            },
            onReset = {
                viewModel.resetBypassConfigToDefault()
                showEditBypassJsonDialog = false
            }
        )
    }
}

@Composable
private fun EditBypassConfigDialog(
    initialConfigJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var jsonText by remember(initialConfigJson) { mutableStateOf(initialConfigJson) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.active_config_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(jsonText))
                    Toast.makeText(context, context.getString(R.string.config_copied), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
                AppButton(onClick = { onSave(jsonText) }) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppTextButton(onClick = onReset) {
                    Text("Reset")
                }
                AppTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Preview
@Composable
fun WifiExclusionsScreenPreview() {
    SpectraTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.wifi_exclusions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Home_WiFi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
