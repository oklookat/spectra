package com.oklookat.spectra.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oklookat.spectra.MainViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.util.TvUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTv(context) }
    val uiState = viewModel.uiState
    
    var showMenu by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var importedContent by remember { mutableStateOf("") }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    // P2P State
    var showScanner by remember { mutableStateOf(false) }
    var profileToSend by remember { mutableStateOf<Profile?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                    importedContent = r.readText()
                    editingProfile = null
                    showLocalDialog = true
                }
            } catch (_: Exception) {
                // Error handling is managed by the ViewModel where possible
            }
        }
    }

    BackHandler(isSelectionMode) {
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.deleteProfiles(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_profile))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.profiles, key = { it.id }) { profile ->
                    ProfileItem(
                        profile = profile,
                        isSelectedForDeletion = selectedIds.contains(profile.id),
                        isActive = uiState.selectedProfileId == profile.id,
                        isTv = isTv,
                        onLongClick = {
                            if (!isSelectionMode) selectedIds = setOf(profile.id)
                        },
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (selectedIds.contains(profile.id)) {
                                    selectedIds - profile.id
                                } else {
                                    selectedIds + profile.id
                                }
                            } else {
                                viewModel.selectProfile(profile.id)
                            }
                        },
                        onEditClick = {
                            editingProfile = profile
                            importedContent = ""
                            if (profile.isRemote) {
                                showRemoteDialog = true
                            } else {
                                showLocalDialog = true
                            }
                        },
                        onRefreshClick = {
                            viewModel.refreshRemoteProfiles(setOf(profile.id))
                        },
                        onShareP2PClick = {
                            profileToSend = profile
                            showScanner = true
                        },
                        onDeleteClick = {
                            viewModel.deleteProfiles(setOf(profile.id))
                        }
                    )
                }
            }

            if (uiState.profiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_profiles_yet),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Dropdown Menu for Adding Profiles
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.refresh_all_remote)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            viewModel.refreshRemoteProfiles()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p2p_receive)) },
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            viewModel.startP2PServer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remote)) },
                        leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            editingProfile = null
                            showRemoteDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_from_file)) },
                        leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            filePicker.launch("application/json")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manual_local)) },
                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            editingProfile = null
                            importedContent = ""
                            showLocalDialog = true
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.isP2PServerRunning && uiState.p2pServerUrl != null && uiState.p2pServerToken != null) {
        P2PReceiveDialog(
            url = uiState.p2pServerUrl,
            token = uiState.p2pServerToken,
            isTv = isTv,
            onDismiss = { viewModel.stopP2PServer() }
        )
    }

    uiState.p2pPayloadToAccept?.let { payload ->
        P2PConfirmDialog(
            deviceName = payload.deviceName,
            payloadName = payload.name,
            isReplace = uiState.showP2PReplaceDialog,
            onAccept = { viewModel.acceptP2PPayload() },
            onReject = { viewModel.rejectP2PPayload() }
        )
    }

    if (showScanner && !isTv) {
        P2PScannerDialog(
            onDismiss = { 
                showScanner = false
                profileToSend = null
            },
            onQrScanned = { url, token ->
                showScanner = false
                profileToSend?.let { 
                    viewModel.sendProfileP2P(it, url, token)
                    profileToSend = null
                }
            }
        )
    }

    if (showRemoteDialog) {
        RemoteProfileDialog(
            profile = editingProfile,
            onDismiss = { 
                showRemoteDialog = false
                editingProfile = null
            },
            onConfirm = { name, url, autoUpdate, interval ->
                viewModel.saveRemoteProfile(editingProfile, name, url, autoUpdate, interval) {
                    showRemoteDialog = false
                    editingProfile = null
                }
            },
            isNameUnique = { name -> 
                uiState.profiles.none { it.name == name && it.id != editingProfile?.id } 
            }
        )
    }

    if (showLocalDialog) {
        val initialContent = importedContent.ifEmpty {
            editingProfile?.let { viewModel.getProfileContent(it) } ?: ""
        }
        
        LocalProfileDialog(
            profile = editingProfile,
            initialContent = initialContent,
            onDismiss = { 
                showLocalDialog = false
                editingProfile = null
                importedContent = ""
            },
            onConfirm = { name, content ->
                viewModel.saveLocalProfile(editingProfile, name, content) {
                    showLocalDialog = false
                    editingProfile = null
                    importedContent = ""
                }
            },
            isNameUnique = { name -> 
                uiState.profiles.none { it.name == name && it.id != editingProfile?.id } 
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileItem(
    profile: Profile,
    isSelectedForDeletion: Boolean,
    isActive: Boolean,
    isTv: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onShareP2PClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showItemMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelectedForDeletion -> MaterialTheme.colorScheme.primaryContainer
                isActive -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isActive) CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (profile.isRemote) Icons.Default.Cloud else Icons.Default.Description, 
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name, 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                if (profile.isRemote && !profile.url.isNullOrEmpty()) {
                    Text(
                        text = profile.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle, 
                    contentDescription = stringResource(R.string.active),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            Box {
                IconButton(onClick = { showItemMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showItemMenu,
                    onDismissRequest = { showItemMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showItemMenu = false
                            onEditClick()
                        }
                    )
                    if (profile.isRemote) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showItemMenu = false
                                onRefreshClick()
                            }
                        )
                    }
                    if (!isTv) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.p2p_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showItemMenu = false
                                onShareP2PClick()
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showItemMenu = false
                            onDeleteClick()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteProfileDialog(
    profile: Profile? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Int) -> Unit,
    isNameUnique: (String) -> Boolean
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var url by remember { mutableStateOf(profile?.url ?: "") }
    var autoUpdate by remember { mutableStateOf(profile?.autoUpdateEnabled ?: false) }
    var interval by remember { mutableStateOf(profile?.autoUpdateIntervalMinutes?.toString() ?: "15") }

    val isNameValid = name.isNotBlank() && isNameUnique(name)
    val isUrlValid = url.isNotBlank()
    val isIntervalValid = (interval.toIntOrNull() ?: 0) >= 15

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) stringResource(R.string.add_remote_profile) else stringResource(R.string.edit_remote_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    isError = !isNameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    isError = !isUrlValid && url.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.auto_update))
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = autoUpdate, onCheckedChange = { autoUpdate = it })
                }
                if (autoUpdate) {
                    TextField(
                        value = interval,
                        onValueChange = { if (it.all { char -> char.isDigit() }) interval = it },
                        label = { Text(stringResource(R.string.interval_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isIntervalValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url, autoUpdate, interval.toIntOrNull() ?: 15) },
                enabled = isNameValid && isUrlValid && (!autoUpdate || isIntervalValid)
            ) {
                Text(if (profile == null) stringResource(R.string.add) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun LocalProfileDialog(
    profile: Profile? = null,
    initialContent: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    isNameUnique: (String) -> Boolean
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var content by remember { mutableStateOf(initialContent) }

    LaunchedEffect(initialContent) {
        if (initialContent.isNotEmpty()) content = initialContent
    }

    val isNameValid = name.isNotBlank() && isNameUnique(name)
    val isContentValid = content.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) stringResource(R.string.add_local_profile) else stringResource(R.string.edit_local_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    isError = !isNameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.configuration_json)) },
                    isError = !isContentValid && content.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, content) },
                enabled = isNameValid && isContentValid
            ) {
                Text(if (profile == null) stringResource(R.string.add) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
