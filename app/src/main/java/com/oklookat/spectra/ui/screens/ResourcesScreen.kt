package com.oklookat.spectra.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.model.Screen
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.ui.viewmodel.ResourcePresetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(viewModel: MainViewModel) {
    val uiState = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var showPresetsMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.resources)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setScreen(Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showPresetsMenu = true }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.presets))
                        }
                        DropdownMenu(
                            expanded = showPresetsMenu,
                            onDismissRequest = { showPresetsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.preset_system)) },
                                onClick = {
                                    showPresetsMenu = false
                                    viewModel.applyResourcePreset(ResourcePresetType.SYSTEM)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.preset_runetfreedom)) },
                                onClick = {
                                    showPresetsMenu = false
                                    viewModel.applyResourcePreset(ResourcePresetType.RUNETFREEDOM)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.preset_loyalsoldier)) },
                                onClick = {
                                    showPresetsMenu = false
                                    viewModel.applyResourcePreset(ResourcePresetType.LOYAL_SOLDIER)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.reloadAllResources() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload_all))
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_resource))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isDownloadingResource) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.downloading_update),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.cancelResourceDownload() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
            }
            
            if (uiState.resources.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.resources, key = { it.name }) { resource ->
                        ResourceItem(
                            resource = resource,
                            downloadProgress = uiState.downloadingResources[resource.name],
                            onUpdate = { viewModel.updateResource(resource) },
                            onDelete = { viewModel.deleteResource(resource.name) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        // Find if the resource being added is currently downloading
        val addingResourceName = remember(showAddDialog) { "" } // This would need better tracking if we wanted it in dialog
        
        AddResourceDialog(
            isDownloading = uiState.isDownloadingResource,
            downloadProgress = uiState.downloadingResources.values.firstOrNull() ?: 0f,
            resourceName = uiState.downloadingResources.keys.firstOrNull(),
            onDismiss = { 
                viewModel.cancelResourceDownload()
                showAddDialog = false 
            },
            onAddRemote = { name, url, autoUpdate, interval ->
                viewModel.addRemoteResource(name, url, autoUpdate, interval)
            },
            onAddLocal = { name, uri ->
                viewModel.addLocalResource(name, uri)
                showAddDialog = false
            },
            existingNames = uiState.resources.map { it.name }
        )
    }
    
    LaunchedEffect(uiState.isDownloadingResource) {
        if (!uiState.isDownloadingResource && showAddDialog) {
            showAddDialog = false
        }
    }
}

@Composable
fun ResourceItem(resource: Resource, downloadProgress: Float?, onUpdate: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isDeletable = !resource.isDefault || resource.url != null
    val hasActions = resource.url != null || isDeletable

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (resource.url != null) Icons.Default.CloudDownload else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = resource.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatFileSize(resource.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (resource.isDefault && resource.url == null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                              ) {
                                Text(
                                    text = stringResource(R.string.system_resource),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (resource.url != null) {
                        Text(
                            text = resource.url.replace("https://", "").replace("http://", ""),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (hasActions) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (resource.url != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.refresh)) },
                                    onClick = {
                                        expanded = false
                                        onUpdate()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                            }
                            if (isDeletable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        expanded = false
                                        onDelete()
                                    },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.DeleteOutline, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.error 
                                        ) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddResourceDialog(
    isDownloading: Boolean,
    downloadProgress: Float,
    resourceName: String?,
    onDismiss: () -> Unit,
    onAddRemote: (String, String, Boolean, Int) -> Unit,
    onAddLocal: (String, Uri) -> Unit,
    existingNames: List<String>
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 for URL, 1 for Local
    var autoUpdate by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf("1") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        if (uri != null && name.isBlank()) {
            uri.path?.substringAfterLast('/')?.let { name = it }
        }
    }

    val isNameReserved = name in listOf("_geoip.dat", "_geosite.dat")
    val isNameValid = name.isNotBlank() && !isNameReserved
    val isUnique = name !in existingNames || name in listOf("geoip.dat", "geosite.dat")

    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        title = { Text(stringResource(R.string.add_resource)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (resourceName != null) stringResource(R.string.downloading_file, resourceName) 
                                   else stringResource(R.string.downloading_update), 
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                inactiveContainerColor = Color.Transparent,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(stringResource(R.string.file_from_url))
                        }
                        SegmentedButton(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                inactiveContainerColor = Color.Transparent,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(stringResource(R.string.file_from_storage))
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        placeholder = { Text("geoip.dat") },
                        isError = !isNameValid || !isUnique,
                        supportingText = {
                            if (!isUnique) Text(stringResource(R.string.unique_name_required))
                            else if (isNameReserved) Text(stringResource(R.string.name_reserved))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (selectedTabIndex == 0) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.url)) },
                            placeholder = { Text("https://example.com/geoip.dat") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.auto_update)) },
                            trailingContent = {
                                Switch(checked = autoUpdate, onCheckedChange = { autoUpdate = it })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                        
                        if (autoUpdate) {
                            OutlinedTextField(
                                value = interval,
                                onValueChange = { interval = it },
                                label = { Text(stringResource(R.string.interval_minutes_hourly)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    } else {
                        OutlinedCard(
                            onClick = { launcher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (selectedUri == null) stringResource(R.string.import_from_file) 
                                           else selectedUri?.lastPathSegment ?: "File selected",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        if (selectedTabIndex == 0) onAddRemote(name, url, autoUpdate, interval.toIntOrNull() ?: 1)
                        else selectedUri?.let { onAddLocal(name, it) }
                    },
                    enabled = isNameValid && isUnique && (if (selectedTabIndex == 0) url.isNotBlank() else selectedUri != null)
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.cancel)) 
            }
        }
    )
}

@Composable
fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        stringResource(R.string.size_mb, mb)
    } else {
        stringResource(R.string.size_kb, kb)
    }
}
