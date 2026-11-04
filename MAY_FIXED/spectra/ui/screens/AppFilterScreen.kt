package com.oklookat.spectra.ui.screens

import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.model.AppRoutingPreset
import com.oklookat.spectra.model.InstalledAppInfo
import com.oklookat.spectra.ui.components.AppButton
import com.oklookat.spectra.ui.components.AppTextButton
import com.oklookat.spectra.ui.viewmodel.AppFilterViewModel
import com.oklookat.spectra.ui.viewmodel.MainUiEvent
import com.oklookat.spectra.util.TvUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterScreen(
    viewModel: AppFilterViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTv(context) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainUiEvent.ShowToast -> {
                    val message = event.messageResId?.let { context.applicationContext.getString(it, *event.formatArgs.toTypedArray()) }
                        ?: event.message ?: ""
                    snackbarHostState.showSnackbar(message)
                }
                else -> {}
            }
        }
    }

    var showCreatePresetDialog by remember { mutableStateOf(false) }
    var showEditPresetDialog by remember { mutableStateOf(false) }
    var showDeletePresetDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_filter),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.selectedPresetId == AppRoutingPreset.PRESET_ALL_VPN) {
                                stringResource(R.string.all_apps_through_vpn)
                            } else {
                                stringResource(R.string.bypass_selected, uiState.selectedPackages.size)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToAllVpn() }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.reset_to_all_vpn))
                    }
                    if (uiState.selectedPresetId == AppRoutingPreset.PRESET_EXCLUDE_RU) {
                        IconButton(onClick = { viewModel.fetchRussianAppRules() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.update_ru_rules))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Presets Horizontal Row
            PresetsHeader(
                presets = uiState.presets,
                selectedPresetId = uiState.selectedPresetId,
                isFetchingRu = uiState.isFetchingRuRules,
                onSelectPreset = { viewModel.selectPreset(it) },
                onCreatePresetClick = { showCreatePresetDialog = true },
                onEditPresetClick = { showEditPresetDialog = true },
                onDeletePresetClick = { showDeletePresetDialog = true }
            )

            HorizontalDivider()

            // 2. Search Bar & Controls
            SearchBarAndControls(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                showSystemApps = uiState.showSystemApps,
                onToggleShowSystemApps = { viewModel.toggleShowSystemApps(it) },
                onInvertSelection = { viewModel.invertSelection() },
                onSelectAll = { viewModel.selectAllVisible() },
                onDeselectAll = { viewModel.deselectAllVisible() }
            )

            // 3. App List or Loading
            if (uiState.isLoadingApps || uiState.isFetchingRuRules) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.isFetchingRuRules) stringResource(R.string.updating_ru_rules) else stringResource(R.string.downloading_update),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.filteredApps, key = { it.packageName }) { appInfo ->
                        val isChecked = uiState.selectedPackages.contains(appInfo.packageName)
                        AppItemRow(
                            appInfo = appInfo,
                            isChecked = isChecked,
                            getAppIcon = { viewModel.getAppIcon(it) },
                            onToggle = { viewModel.togglePackage(appInfo.packageName) }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreatePresetDialog) {
        CreatePresetDialog(
            onDismiss = { showCreatePresetDialog = false },
            onConfirm = { name ->
                viewModel.createCustomPreset(name)
                showCreatePresetDialog = false
            }
        )
    }

    if (showEditPresetDialog) {
        val currentPreset = uiState.selectedPreset
        if (currentPreset != null && !currentPreset.isBuiltIn) {
            EditPresetDialog(
                currentName = currentPreset.name,
                onDismiss = { showEditPresetDialog = false },
                onConfirm = { newName ->
                    viewModel.updateCustomPresetName(newName)
                    showEditPresetDialog = false
                }
            )
        }
    }

    if (showDeletePresetDialog) {
        val currentPreset = uiState.selectedPreset
        if (currentPreset != null && !currentPreset.isBuiltIn) {
            ConfirmDeletePresetDialog(
                presetName = currentPreset.name,
                onDismiss = { showDeletePresetDialog = false },
                onConfirm = {
                    viewModel.deleteCustomPreset(currentPreset.id)
                    showDeletePresetDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsHeader(
    presets: List<AppRoutingPreset>,
    selectedPresetId: String,
    isFetchingRu: Boolean,
    onSelectPreset: (String) -> Unit,
    onCreatePresetClick: () -> Unit,
    onEditPresetClick: () -> Unit,
    onDeletePresetClick: () -> Unit
) {
    val selectedPreset = presets.find { it.id == selectedPresetId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.app_filter),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedPreset != null && !selectedPreset.isBuiltIn) {
                    IconButton(onClick = onEditPresetClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_preset))
                    }
                    IconButton(onClick = onDeletePresetClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onCreatePresetClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_preset))
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                val isSelected = preset.id == selectedPresetId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset.id) },
                    label = {
                        Text(
                            if (preset.id == AppRoutingPreset.PRESET_ALL_VPN) stringResource(R.string.all_apps_through_vpn)
                            else if (preset.id == AppRoutingPreset.PRESET_EXCLUDE_RU) "Кроме российских"
                            else preset.name
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        if (selectedPreset != null && !selectedPreset.isBuiltIn && selectedPreset.packageRegexes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(selectedPreset.packageRegexes.toList()) { regex ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(regex, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clickable { }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarAndControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSystemApps: Boolean,
    onToggleShowSystemApps: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onInvertSelection,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.invert_selection),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            OutlinedButton(
                onClick = onSelectAll,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_all),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            OutlinedButton(
                onClick = onDeselectAll,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.deselect_all),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleShowSystemApps(!showSystemApps) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.show_system_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = showSystemApps,
                onCheckedChange = { onToggleShowSystemApps(it) }
            )
        }
    }
}

@Composable
private fun AppItemRow(
    appInfo: InstalledAppInfo,
    isChecked: Boolean,
    getAppIcon: (String) -> Drawable?,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(
            packageName = appInfo.packageName,
            getAppIcon = getAppIcon,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    getAppIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier
) {
    var iconDrawable by remember(packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            iconDrawable = getAppIcon(packageName)
        }
    }

    if (iconDrawable != null) {
        val bitmap = remember(iconDrawable) {
            try {
                iconDrawable?.toBitmap()?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun CreatePresetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preset_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditPresetDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preset_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConfirmDeletePresetDialog(
    presetName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_preset_q, presetName)) },
        confirmButton = {
            AppButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun AppFilterScreenPreview() {
    com.oklookat.spectra.ui.theme.SpectraTheme {
        Surface {
            Column(modifier = Modifier.fillMaxSize()) {
                PresetsHeader(
                    presets = AppRoutingPreset.defaultPresets(),
                    selectedPresetId = AppRoutingPreset.PRESET_EXCLUDE_RU,
                    isFetchingRu = false,
                    onSelectPreset = {},
                    onCreatePresetClick = {},
                    onEditPresetClick = {},
                    onDeletePresetClick = {}
                )
                SearchBarAndControls(
                    searchQuery = "",
                    onSearchQueryChange = {},
                    showSystemApps = false,
                    onToggleShowSystemApps = {},
                    onInvertSelection = {},
                    onSelectAll = {},
                    onDeselectAll = {}
                )
                AppItemRow(
                    appInfo = InstalledAppInfo("ru.yandex.searchplugin", "Яндекс"),
                    isChecked = true,
                    getAppIcon = { null },
                    onToggle = {}
                )
                AppItemRow(
                    appInfo = InstalledAppInfo("com.google.android.youtube", "YouTube"),
                    isChecked = false,
                    getAppIcon = { null },
                    onToggle = {}
                )
            }
        }
    }
}
