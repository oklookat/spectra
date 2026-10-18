package com.oklookat.spectra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.DnsServerConfig
import com.oklookat.spectra.model.RoutingRuleConfig
import com.oklookat.spectra.ui.components.AppSwitch
import com.oklookat.spectra.ui.viewmodel.DnsRoutingViewModel
import com.oklookat.spectra.ui.viewmodel.RoutingPreset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRoutingScreen(
    viewModel: DnsRoutingViewModel,
    onBack: () -> Unit
) {
    val uiState = viewModel.uiState
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_defaults)) },
            text = { Text(stringResource(R.string.reset_rules_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dns_routing_rules)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.reset_defaults))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.selectedTab == 0) viewModel.openAddDnsServerDialog()
                    else viewModel.openAddRoutingRuleDialog()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (uiState.selectedTab == 0) stringResource(R.string.add_dns_server)
                        else stringResource(R.string.add_routing_rule)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.dns_settings)) },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(stringResource(R.string.routing_rules)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null) }
                )
            }

            when (uiState.selectedTab) {
                0 -> DnsTabContent(viewModel = viewModel)
                1 -> RoutingTabContent(viewModel = viewModel)
            }
        }
    }
}

// region DNS Tab

@Composable
private fun DnsTabContent(
    viewModel: DnsRoutingViewModel
) {
    val uiState = viewModel.uiState
    val config = uiState.customDnsConfig

    if (uiState.isDnsDialogVisible) {
        DnsServerDialog(
            server = uiState.editingDnsServer,
            onDismiss = { viewModel.closeDnsServerDialog() },
            onSave = { address -> viewModel.saveDnsServer(address) }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.query_strategy),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val strategies = listOf("UseIPv4", "UseIPv6", "UseIP", "PreferIPv4", "PreferIPv6")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                strategies.forEach { strategy ->
                    FilterChip(
                        selected = config.queryStrategy == strategy,
                        onClick = { viewModel.setDnsQueryStrategy(strategy) },
                        label = { Text(strategy) }
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.dns_servers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(config.servers, key = { it.id }) { server ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppSwitch(
                        checked = server.enabled,
                        onCheckedChange = { viewModel.toggleDnsServer(server.id, it) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.address,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { viewModel.openEditDnsServerDialog(server) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { viewModel.deleteDnsServer(server.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DnsServerDialog(
    server: DnsServerConfig?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var address by remember { mutableStateOf(server?.address ?: "") }
    val quickPresets = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
        "8.8.8.8",
        "1.1.1.1",
        "fakedns",
        "https://dns.adguard-dns.com/dns-query"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (server == null) stringResource(R.string.add_dns_server) else stringResource(R.string.edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.dns_server)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.preset_templates),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickPresets) { preset ->
                        SuggestionChip(
                            onClick = { address = preset },
                            label = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (address.isNotBlank()) onSave(address) },
                enabled = address.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// endregion

// region Routing Tab

@Composable
private fun RoutingTabContent(
    viewModel: DnsRoutingViewModel
) {
    val uiState = viewModel.uiState
    val config = uiState.customRoutingConfig

    if (uiState.isRoutingRuleDialogVisible) {
        RoutingRuleDialog(
            rule = uiState.editingRoutingRule,
            onDismiss = { viewModel.closeRoutingRuleDialog() },
            onSave = { rule -> viewModel.saveRoutingRule(rule) }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.domain_strategy),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val strategies = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                strategies.forEach { strategy ->
                    FilterChip(
                        selected = config.domainStrategy == strategy,
                        onClick = { viewModel.setRoutingDomainStrategy(strategy) },
                        label = { Text(strategy) }
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.preset_templates),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RoutingPreset.entries.toTypedArray()) { preset ->
                    AssistChip(
                        onClick = { viewModel.applyPresetTemplate(preset) },
                        label = { Text(preset.titleRu) },
                        leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null) }
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.routing_rules),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        itemsIndexed(config.rules, key = { _, rule -> rule.id }) { index, rule ->
            RoutingRuleCard(
                rule = rule,
                isFirst = index == 0,
                isLast = index == config.rules.lastIndex,
                onToggle = { enabled -> viewModel.toggleRoutingRule(rule.id, enabled) },
                onEdit = { viewModel.openEditRoutingRuleDialog(rule) },
                onDelete = { viewModel.deleteRoutingRule(rule.id) },
                onMoveUp = { viewModel.moveRoutingRule(rule.id, moveUp = true) },
                onMoveDown = { viewModel.moveRoutingRule(rule.id, moveUp = false) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun RoutingRuleCard(
    rule: RoutingRuleConfig,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSwitch(checked = rule.enabled, onCheckedChange = onToggle)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = rule.name.ifBlank { stringResource(R.string.routing_rules) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val (badgeBg, badgeText) = when (rule.outboundTag) {
                    "proxy-out" -> MaterialTheme.colorScheme.primaryContainer to stringResource(R.string.action_proxy)
                    "direct-out" -> MaterialTheme.colorScheme.secondaryContainer to stringResource(R.string.action_direct)
                    "block-out" -> MaterialTheme.colorScheme.errorContainer to stringResource(R.string.action_block)
                    else -> MaterialTheme.colorScheme.surfaceVariant to rule.outboundTag
                }

                Surface(
                    color = badgeBg,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val details = mutableListOf<String>()
            if (rule.domains.isNotEmpty()) details.add("Домены: ${rule.domains.joinToString(", ")}")
            if (rule.ip.isNotEmpty()) details.add("IP: ${rule.ip.joinToString(", ")}")
            if (rule.port.isNotBlank()) details.add("Порты: ${rule.port}")
            if (rule.protocols.isNotEmpty()) details.add("Протоколы: ${rule.protocols.joinToString(", ")}")
            if (rule.inboundTag.isNotEmpty()) details.add("Inbound: ${rule.inboundTag.joinToString(", ")}")

            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Все соединения (Match All)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RoutingRuleDialog(
    rule: RoutingRuleConfig?,
    onDismiss: () -> Unit,
    onSave: (RoutingRuleConfig) -> Unit
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var outboundTag by remember { mutableStateOf(rule?.outboundTag ?: "proxy-out") }
    var domainsStr by remember { mutableStateOf(rule?.domains?.joinToString(", ") ?: "") }
    var ipStr by remember { mutableStateOf(rule?.ip?.joinToString(", ") ?: "") }
    var port by remember { mutableStateOf(rule?.port ?: "") }
    var protocolsStr by remember { mutableStateOf(rule?.protocols?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (rule == null) stringResource(R.string.add_routing_rule) else stringResource(R.string.edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.action_outbound),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = outboundTag == "proxy-out",
                        onClick = { outboundTag = "proxy-out" },
                        label = { Text(stringResource(R.string.action_proxy)) }
                    )
                    FilterChip(
                        selected = outboundTag == "direct-out",
                        onClick = { outboundTag = "direct-out" },
                        label = { Text(stringResource(R.string.action_direct)) }
                    )
                    FilterChip(
                        selected = outboundTag == "block-out",
                        onClick = { outboundTag = "block-out" },
                        label = { Text(stringResource(R.string.action_block)) }
                    )
                }

                OutlinedTextField(
                    value = domainsStr,
                    onValueChange = { domainsStr = it },
                    label = { Text(stringResource(R.string.domains)) },
                    placeholder = { Text("geosite:ru, domain:google.com") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ipStr,
                    onValueChange = { ipStr = it },
                    label = { Text(stringResource(R.string.ip_addresses)) },
                    placeholder = { Text("geoip:private, geoip:ru, 192.168.1.0/24") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.ports)) },
                    placeholder = { Text("53, 80, 443, 1000-2000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = protocolsStr,
                    onValueChange = { protocolsStr = it },
                    label = { Text(stringResource(R.string.protocols)) },
                    placeholder = { Text("bittorrent, http, tls") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDomains = domainsStr.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    val parsedIp = ipStr.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    val parsedProtocols = protocolsStr.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }

                    val newRule = RoutingRuleConfig(
                        id = rule?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        outboundTag = outboundTag,
                        domains = parsedDomains,
                        ip = parsedIp,
                        port = port.trim(),
                        protocols = parsedProtocols,
                        enabled = rule?.enabled ?: true
                    )
                    onSave(newRule)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// endregion
