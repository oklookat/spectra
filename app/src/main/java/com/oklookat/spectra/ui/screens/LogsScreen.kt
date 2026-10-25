package com.oklookat.spectra.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.components.AppButton
import com.oklookat.spectra.ui.components.AppSwitch
import com.oklookat.spectra.ui.components.AppTextButton
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.ui.viewmodel.LogsViewModel
import com.oklookat.spectra.util.LogManager
import com.oklookat.spectra.util.TvUtils
import kotlinx.coroutines.launch

private enum class DisplayLevelFilter {
    ALL, ERROR, WARNING, INFO, DEBUG
}

private enum class LogSeverity {
    ERROR, WARNING, INFO, DEBUG, DNS
}

private data class ParsedLog(
    val raw: String,
    val timestamp: String?,
    val severity: LogSeverity,
    val message: String
)

private fun parseLogEntry(rawLog: String): ParsedLog {
    var text = rawLog.trim()
    var timestamp: String? = null

    val timeRegex = Regex("""^(\d{4}/\d{2}/\d{2}\s+\d{2}:\d{2}:\d{2}(\.\d+)?|\d{2}:\d{2}:\d{2})\s+""")
    val timeMatch = timeRegex.find(text)
    if (timeMatch != null) {
        timestamp = timeMatch.groupValues[1]
        text = text.substring(timeMatch.value.length)
    }

    val lower = rawLog.lowercase()
    val severity = when {
        lower.contains("[error]") || lower.contains(" error:") || lower.contains("failed") || lower.contains("fatal") -> LogSeverity.ERROR
        lower.contains("[warning]") || lower.contains(" warning:") || lower.contains("warn:") -> LogSeverity.WARNING
        lower.contains("[debug]") || lower.contains(" debug:") -> LogSeverity.DEBUG
        lower.contains("[dns]") || lower.contains("dns:") -> LogSeverity.DNS
        else -> LogSeverity.INFO
    }

    return ParsedLog(
        raw = rawLog,
        timestamp = timestamp,
        severity = severity,
        message = text
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
    onRequestRestartVpn: () -> Unit = {}
) {
    val logs = LogManager.logs
    val isPaused = LogManager.isPaused
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedLevelFilter by remember { mutableStateOf(DisplayLevelFilter.ALL) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLogSettingsDialog by remember { mutableStateOf(false) }

    val parsedLogs = remember(logs.toList()) {
        logs.map { parseLogEntry(it) }
    }

    val filteredLogs = remember(parsedLogs, searchQuery, selectedLevelFilter) {
        parsedLogs.filter { item ->
            val matchesLevel = when (selectedLevelFilter) {
                DisplayLevelFilter.ALL -> true
                DisplayLevelFilter.ERROR -> item.severity == LogSeverity.ERROR
                DisplayLevelFilter.WARNING -> item.severity == LogSeverity.WARNING || item.severity == LogSeverity.ERROR
                DisplayLevelFilter.INFO -> item.severity == LogSeverity.INFO || item.severity == LogSeverity.WARNING || item.severity == LogSeverity.ERROR || item.severity == LogSeverity.DNS
                DisplayLevelFilter.DEBUG -> item.severity == LogSeverity.DEBUG
            }
            val matchesSearch = if (searchQuery.isBlank()) true else item.raw.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesSearch
        }
    }

    val isScrolledToBottom by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems == 0 || lastVisibleIndex >= totalItems - 2
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (!isPaused && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val copiedMessage = stringResource(R.string.logs_copied)
    val clearedMessage = stringResource(R.string.logs_cleared)
    val singleCopiedMessage = stringResource(R.string.log_copied_single)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.logs), fontWeight = FontWeight.Bold)
                        if (filteredLogs.size != logs.size) {
                            Text(
                                text = "${filteredLogs.size} / ${logs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_logs),
                            tint = if (isSearchActive || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { LogManager.isPaused = !LogManager.isPaused }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.resume_logs) else stringResource(R.string.pause_logs),
                            tint = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showLogSettingsDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.log_settings))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_logs)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    val textToCopy = filteredLogs.joinToString("\n") { it.raw }
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    showOverflowMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(copiedMessage)
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_logs)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    LogManager.clear()
                                    showOverflowMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(clearedMessage)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isScrolledToBottom && filteredLogs.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(filteredLogs.size - 1)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Expandable Search Bar & Filter Bar Surface
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Expandable Search Bar
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_logs)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = CircleShape,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = ""
                                        } else {
                                            isSearchActive = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    }

                    // Level Filter Bar & Quick Status Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedLevelFilter == DisplayLevelFilter.ALL,
                            onClick = { selectedLevelFilter = DisplayLevelFilter.ALL },
                            label = { Text(stringResource(R.string.filter_all)) },
                            leadingIcon = if (selectedLevelFilter == DisplayLevelFilter.ALL) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = selectedLevelFilter == DisplayLevelFilter.ERROR,
                            onClick = { selectedLevelFilter = DisplayLevelFilter.ERROR },
                            label = { Text(stringResource(R.string.filter_error)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            leadingIcon = if (selectedLevelFilter == DisplayLevelFilter.ERROR) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = selectedLevelFilter == DisplayLevelFilter.WARNING,
                            onClick = { selectedLevelFilter = DisplayLevelFilter.WARNING },
                            label = { Text(stringResource(R.string.filter_warning)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            leadingIcon = if (selectedLevelFilter == DisplayLevelFilter.WARNING) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = selectedLevelFilter == DisplayLevelFilter.INFO,
                            onClick = { selectedLevelFilter = DisplayLevelFilter.INFO },
                            label = { Text(stringResource(R.string.filter_info)) }
                        )

                        FilterChip(
                            selected = selectedLevelFilter == DisplayLevelFilter.DEBUG,
                            onClick = { selectedLevelFilter = DisplayLevelFilter.DEBUG },
                            label = { Text(stringResource(R.string.filter_debug)) }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Config status indicator pill
                        SuggestionChip(
                            onClick = { showLogSettingsDialog = true },
                            label = {
                                Text(
                                    text = "Engine: ${uiState.logLevel.uppercase()}${if (uiState.dnsLog) " • DNS" else ""}${if (uiState.maskAddress.isNotEmpty()) " • MASK" else ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            // Paused Banner
            AnimatedVisibility(visible = isPaused) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.logging_paused_banner),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = { LogManager.isPaused = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.resume_logs),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Log Console Surface
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = if (logs.isEmpty()) stringResource(R.string.no_logs_recorded)
                                else stringResource(R.string.no_logs_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLogs) { item ->
                            LogItemRow(
                                item = item,
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(item.raw))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(singleCopiedMessage)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogSettingsDialog) {
        LogSettingsDialog(
            currentLogLevel = uiState.logLevel,
            currentDnsLog = uiState.dnsLog,
            currentMaskAddress = uiState.maskAddress,
            isVpnEnabled = uiState.isVpnEnabled,
            onDismiss = { showLogSettingsDialog = false },
            onSave = { logLevel, dnsLog, maskAddress ->
                viewModel.saveLogSettings(logLevel, dnsLog, maskAddress) { shouldRestartVpn ->
                    showLogSettingsDialog = false
                    if (shouldRestartVpn) {
                        onRequestRestartVpn()
                    }
                }
            }
        )
    }
}

@Composable
private fun LogItemRow(
    item: ParsedLog,
    onClick: () -> Unit
) {
    val (badgeText, badgeContainerColor, badgeContentColor) = when (item.severity) {
        LogSeverity.ERROR -> Triple("ERR", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        LogSeverity.WARNING -> Triple("WARN", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        LogSeverity.DEBUG -> Triple("DBG", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        LogSeverity.DNS -> Triple("DNS", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        LogSeverity.INFO -> Triple("INFO", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeContainerColor,
            contentColor = badgeContentColor,
            modifier = Modifier.padding(top = 1.dp)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Body with optional timestamp
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (item.timestamp != null) {
                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSettingsDialog(
    currentLogLevel: String,
    currentDnsLog: Boolean,
    currentMaskAddress: String,
    isVpnEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (logLevel: String, dnsLog: Boolean, maskAddress: String) -> Unit
) {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)

    var logLevel by remember { mutableStateOf(currentLogLevel) }
    var dnsLog by remember { mutableStateOf(currentDnsLog) }
    var maskAddress by remember { mutableStateOf(currentMaskAddress) }

    val logLevels = listOf(
        "warning" to "Warning",
        "info" to "Info",
        "debug" to "Debug",
        "error" to "Error",
        "none" to "None"
    )

    val maskAddresses = listOf(
        "" to stringResource(R.string.mask_address_disabled),
        "quarter" to stringResource(R.string.mask_address_quarter),
        "half" to stringResource(R.string.mask_address_half),
        "full" to stringResource(R.string.mask_address_full)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.log_settings), fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = {
                        logLevel = "warning"
                        dnsLog = false
                        maskAddress = ""
                    }
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.reset_defaults),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Log Level Group
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.log_level),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                            logLevels.forEachIndexed { index, (value, title) ->
                                val isSelected = logLevel == value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = isSelected,
                                            onClick = { logLevel = value },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (index < logLevels.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 2. DNS Query Logging Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.dns_log),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppSwitch(
                            checked = dnsLog,
                            onCheckedChange = { dnsLog = it },
                            isTv = isTv
                        )
                    }
                }

                HorizontalDivider()

                // 3. IP Masking Group
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.mask_address),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                            maskAddresses.forEachIndexed { index, (value, label) ->
                                val isSelected = maskAddress == value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = isSelected,
                                            onClick = { maskAddress = value },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (index < maskAddresses.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }

                // 4. Status Notice Card
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isVpnEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isVpnEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isVpnEnabled) stringResource(R.string.log_settings_vpn_active_notice)
                            else stringResource(R.string.log_settings_vpn_inactive_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isVpnEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onSave(logLevel, dnsLog, maskAddress) },
                isTv = isTv
            ) {
                Text(
                    if (isVpnEnabled) stringResource(R.string.apply_and_restart)
                    else stringResource(R.string.save)
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppTextButton(
                    onClick = {
                        logLevel = "warning"
                        dnsLog = false
                        maskAddress = ""
                    },
                    isTv = isTv
                ) {
                    Text(stringResource(R.string.reset_defaults))
                }
                AppTextButton(
                    onClick = onDismiss,
                    isTv = isTv
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Preview
@Composable
fun LogsScreenPreview() {
    SpectraTheme {
        LogsScreen()
    }
}
