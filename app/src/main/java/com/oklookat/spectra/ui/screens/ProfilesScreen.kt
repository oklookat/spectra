package com.oklookat.spectra.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.ui.components.*
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.util.TvUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTv(context) }
    val uiState = viewModel.uiState
    
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    val selectedGroup = remember(selectedGroupIndex, uiState.groups) {
        if (selectedGroupIndex == 0) null 
        else uiState.groups.getOrNull(selectedGroupIndex - 1)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var showGroupMenu by remember { mutableStateOf(false) }
    
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var importedContent by remember { mutableStateOf("") }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    // P2P State
    var showScanner by remember { mutableStateOf(false) }
    var profileToSend by remember { mutableStateOf<Profile?>(null) }
    var groupToSend by remember { mutableStateOf<Group?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                    importedContent = r.readText()
                    editingProfile = null
                    showLocalDialog = true
                }
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(isSelectionMode) {
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            ProfilesTopBar(
                isSelectionMode = isSelectionMode,
                selectedCount = selectedIds.size,
                selectedGroup = selectedGroup,
                groups = uiState.groups,
                selectedGroupIndex = selectedGroupIndex,
                isTv = isTv,
                onClearSelection = { selectedIds = emptySet() },
                onDeleteSelection = {
                    viewModel.deleteProfiles(selectedIds)
                    selectedIds = emptySet()
                },
                onGroupClick = { selectedGroupIndex = it },
                onAddGroupClick = {
                    editingGroup = null
                    showGroupDialog = true
                },
                onGroupMenuClick = { showGroupMenu = true },
                onShowGroupMenu = showGroupMenu,
                onDismissGroupMenu = { showGroupMenu = false },
                onShareGroup = {
                    groupToSend = selectedGroup
                    showScanner = true
                },
                onEditGroup = {
                    editingGroup = selectedGroup
                    showGroupDialog = true
                },
                onRefreshGroup = { viewModel.refreshGroup(selectedGroup!!) },
                onDeleteGroup = { viewModel.deleteGroup(selectedGroup!!.id) }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode && (selectedGroup == null || !selectedGroup.isRemote)) {
                ProfilesFab(
                    onShowMenu = { showMenu = true },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onRefreshAll = { viewModel.refreshRemoteProfiles() },
                    onP2PReceive = { viewModel.startP2PServer() },
                    onAddRemote = {
                        editingProfile = null
                        showRemoteDialog = true
                    },
                    onImportFile = { filePicker.launch("application/json") },
                    onAddManual = {
                        editingProfile = null
                        importedContent = ""
                        showLocalDialog = true
                    }
                )
            }
        }
    ) { padding ->
        val filteredProfiles = remember(selectedGroup, uiState.profiles) {
            if (selectedGroup == null) uiState.profiles
            else uiState.profiles.filter { it.groupId == selectedGroup.id }
        }

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
                items(filteredProfiles, key = { it.id }) { profile ->
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

            if (filteredProfiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_profiles_yet),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    // Dialogs
    if (showGroupDialog) {
        GroupDialog(
            group = editingGroup,
            onDismiss = { 
                showGroupDialog = false
                editingGroup = null
            },
            onConfirm = { name, url, autoUpdate, interval ->
                viewModel.saveGroup(editingGroup, name, url, autoUpdate, interval) {
                    showGroupDialog = false
                    editingGroup = null
                }
            },
            isNameUnique = { name -> 
                uiState.groups.none { it.name == name && it.id != editingGroup?.id } 
            }
        )
    }

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
            payloadName = if (payload.profile != null) payload.profile.name else payload.group?.name ?: "Unknown",
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
                groupToSend = null
            },
            onQrScanned = { url, token ->
                showScanner = false
                profileToSend?.let { 
                    viewModel.sendProfileP2P(it, url, token)
                    profileToSend = null
                }
                groupToSend?.let {
                    viewModel.sendGroupP2P(it, url, token)
                    groupToSend = null
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
                viewModel.saveRemoteProfile(editingProfile, name, url, autoUpdate, interval, selectedGroup?.id ?: Group.DEFAULT_GROUP_ID) {
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
                viewModel.saveLocalProfile(editingProfile, name, content, selectedGroup?.id ?: Group.DEFAULT_GROUP_ID) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    selectedGroup: Group?,
    groups: List<Group>,
    selectedGroupIndex: Int,
    isTv: Boolean,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onAddGroupClick: () -> Unit,
    onGroupMenuClick: () -> Unit,
    onShowGroupMenu: Boolean,
    onDismissGroupMenu: () -> Unit,
    onShareGroup: () -> Unit,
    onEditGroup: () -> Unit,
    onRefreshGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    val context = LocalContext.current
    
    Column {
        if (isSelectionMode) {
            TopAppBar(
                title = { Text(stringResource(R.string.selected_count, selectedCount)) },
                navigationIcon = {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onDeleteSelection) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        } else {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles)) },
                actions = {
                    if (selectedGroup != null) {
                        Box {
                            IconButton(onClick = onGroupMenuClick) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Group Actions")
                            }
                            DropdownMenu(
                                expanded = onShowGroupMenu,
                                onDismissRequest = onDismissGroupMenu
                            ) {
                                if (!isTv) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share_group)) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onShareGroup()
                                        }
                                    )
                                    if (!selectedGroup.url.isNullOrBlank()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.share_source_url)) },
                                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                            onClick = {
                                                onDismissGroupMenu()
                                                val sendIntent: Intent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, selectedGroup.url)
                                                    type = "text/plain"
                                                }
                                                val shareIntent = Intent.createChooser(sendIntent, null)
                                                context.startActivity(shareIntent)
                                            }
                                        )
                                    }
                                }
                                if (selectedGroup.id != Group.DEFAULT_GROUP_ID) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_group)) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onEditGroup()
                                        }
                                    )
                                    if (selectedGroup.isRemote) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.refresh)) },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                            onClick = {
                                                onDismissGroupMenu()
                                                onRefreshGroup()
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_group)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onDeleteGroup()
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
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedGroupIndex,
            edgePadding = 16.dp,
            divider = {},
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedGroupIndex == 0,
                onClick = { onGroupClick(0) },
                text = { Text(stringResource(R.string.all)) }
            )
            groups.forEachIndexed { index, group ->
                Tab(
                    selected = selectedGroupIndex == index + 1,
                    onClick = { onGroupClick(index + 1) },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (group.isRemote) {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(group.name)
                        }
                    }
                )
            }
            IconButton(onClick = onAddGroupClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group))
            }
        }
    }
}

@Composable
private fun ProfilesFab(
    onShowMenu: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onRefreshAll: () -> Unit,
    onP2PReceive: () -> Unit,
    onAddRemote: () -> Unit,
    onImportFile: () -> Unit,
    onAddManual: () -> Unit
) {
    Box {
        FloatingActionButton(onClick = onShowMenu) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_profile))
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh_all_remote)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onRefreshAll()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.p2p_receive)) },
                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onP2PReceive()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remote)) },
                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onAddRemote()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_from_file)) },
                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onImportFile()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manual_local)) },
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onAddManual()
                }
            )
        }
    }
}
