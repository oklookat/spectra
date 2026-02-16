package com.oklookat.spectra.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.ui.components.*
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.ui.viewmodel.ProfileSort
import com.oklookat.spectra.ui.viewmodel.ProfilesViewModel
import com.oklookat.spectra.util.TvUtils
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTv(context) }
    val uiState = viewModel.uiState
    val mainUiState = mainViewModel.uiState
    val listState = rememberLazyListState()
    
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    val selectedGroup = remember(selectedGroupIndex, uiState.groups) {
        if (selectedGroupIndex == 0) null 
        else uiState.groups.getOrNull(selectedGroupIndex - 1)
    }

    // Scroll to top when group changes
    LaunchedEffect(selectedGroupIndex) {
        listState.scrollToItem(0)
    }

    // Scroll to top when sort order changes
    LaunchedEffect(uiState.sortOrder) {
        listState.scrollToItem(0)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var importedContent by remember { mutableStateOf("") }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

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
                    // TODO: Implement P2P Sharing
                },
                onEditGroup = {
                    editingGroup = selectedGroup
                    showGroupDialog = true
                },
                onRefresh = { viewModel.refresh(selectedGroup?.id) },
                onDeleteGroup = { viewModel.deleteGroup(selectedGroup!!.id) },
                onPingAll = { viewModel.measureAllPings(selectedGroup?.id) },
                onShowSortMenu = { showSortMenu = true },
                // TV Actions
                onAddProfileClick = { showMenu = true }
            )
        },
        floatingActionButton = {
            if (!isTv && !isSelectionMode && (selectedGroup == null || !selectedGroup.isRemote)) {
                ProfilesFab(
                    onShowMenu = { showMenu = true },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onP2PReceive = { mainViewModel.startP2PServer() },
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
        val filteredProfiles = remember(selectedGroup, uiState.profiles, uiState.sortOrder) {
            val baseList = if (selectedGroup == null) uiState.profiles
            else uiState.profiles.filter { it.groupId == selectedGroup.id }

            when (uiState.sortOrder) {
                ProfileSort.AS_IS -> baseList
                ProfileSort.BY_NAME_ASC -> baseList.sortedBy { it.name.lowercase() }
                ProfileSort.BY_NAME_DESC -> baseList.sortedByDescending { it.name.lowercase() }
                ProfileSort.BY_PING_ASC -> baseList.sortedWith(compareBy<Profile> { 
                    if (it.lastPing < 0) Long.MAX_VALUE else it.lastPing 
                }.thenBy { it.name.lowercase() })
                ProfileSort.BY_PING_DESC -> baseList.sortedWith(compareByDescending<Profile> { 
                    if (it.lastPing < 0) -1L else it.lastPing 
                }.thenBy { it.name.lowercase() })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
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
                            // TODO: Implement P2P Sharing
                        },
                        onDeleteClick = {
                            viewModel.deleteProfiles(setOf(profile.id))
                        },
                        onPingClick = {
                            viewModel.measurePing(profile)
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

    if (showSortMenu) {
        if (isTv) {
            SortDialog(
                selectedSort = uiState.sortOrder,
                onSortSelected = { viewModel.setSortOrder(it) },
                onDismiss = { showSortMenu = false }
            )
        } else {
            SortBottomSheet(
                selectedSort = uiState.sortOrder,
                onSortSelected = { viewModel.setSortOrder(it) },
                onDismiss = { showSortMenu = false }
            )
        }
    }

    // Reuse ProfilesFab logic for TV menu (shows when showMenu is true)
    if (isTv && showMenu) {
        TvAddProfileMenu(
            onDismiss = { showMenu = false },
            onP2PReceive = { 
                showMenu = false
                mainViewModel.startP2PServer() 
            },
            onAddRemote = {
                showMenu = false
                editingProfile = null
                showRemoteDialog = true
            },
            onAddManual = {
                showMenu = false
                editingProfile = null
                importedContent = ""
                showLocalDialog = true
            }
        )
    }

    // Dialogs...
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

    if (mainUiState.isP2PServerRunning && mainUiState.p2pServerUrl != null && mainUiState.p2pServerToken != null) {
        P2PReceiveDialog(
            url = mainUiState.p2pServerUrl,
            token = mainUiState.p2pServerToken,
            isTv = isTv,
            onDismiss = { mainViewModel.stopP2PServer() }
        )
    }

    mainUiState.p2pPayloadToAccept?.let { payload ->
        P2PConfirmDialog(
            deviceName = payload.deviceName,
            payloadName = if (payload.profile != null) payload.profile.name else payload.group?.name ?: "Unknown",
            isReplace = mainUiState.showP2PReplaceDialog,
            onAccept = { mainViewModel.acceptP2PPayload() },
            onReject = { mainViewModel.rejectP2PPayload() }
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
private fun SortBottomSheet(
    selectedSort: ProfileSort,
    onSortSelected: (ProfileSort) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.sort),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            SortOptionsList(selectedSort, onSortSelected, onDismiss)
        }
    }
}

@Composable
private fun SortDialog(
    selectedSort: ProfileSort,
    onSortSelected: (ProfileSort) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                SortOptionsList(selectedSort, onSortSelected, onDismiss)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun SortOptionsList(
    selectedSort: ProfileSort,
    onSortSelected: (ProfileSort) -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        SortItem(
            sort = ProfileSort.AS_IS,
            isSelected = selectedSort == ProfileSort.AS_IS,
            onClick = { onSortSelected(ProfileSort.AS_IS); onDismiss() }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        SortItem(
            sort = ProfileSort.BY_NAME_ASC,
            isSelected = selectedSort == ProfileSort.BY_NAME_ASC,
            onClick = { onSortSelected(ProfileSort.BY_NAME_ASC); onDismiss() }
        )
        SortItem(
            sort = ProfileSort.BY_NAME_DESC,
            isSelected = selectedSort == ProfileSort.BY_NAME_DESC,
            onClick = { onSortSelected(ProfileSort.BY_NAME_DESC); onDismiss() }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        SortItem(
            sort = ProfileSort.BY_PING_ASC,
            isSelected = selectedSort == ProfileSort.BY_PING_ASC,
            onClick = { onSortSelected(ProfileSort.BY_PING_ASC); onDismiss() }
        )
        SortItem(
            sort = ProfileSort.BY_PING_DESC,
            isSelected = selectedSort == ProfileSort.BY_PING_DESC,
            onClick = { onSortSelected(ProfileSort.BY_PING_DESC); onDismiss() }
        )
    }
}

@Composable
private fun SortItem(
    sort: ProfileSort,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val label = when (sort) {
        ProfileSort.AS_IS -> stringResource(R.string.sort_as_is)
        ProfileSort.BY_NAME_ASC -> stringResource(R.string.sort_by_name_asc)
        ProfileSort.BY_NAME_DESC -> stringResource(R.string.sort_by_name_desc)
        ProfileSort.BY_PING_ASC -> stringResource(R.string.sort_by_ping_asc)
        ProfileSort.BY_PING_DESC -> stringResource(R.string.sort_by_ping_desc)
    }
    val icon = when (sort) {
        ProfileSort.AS_IS -> Icons.AutoMirrored.Filled.FormatListBulleted
        ProfileSort.BY_NAME_ASC -> Icons.Default.SortByAlpha
        ProfileSort.BY_NAME_DESC -> Icons.Default.SortByAlpha
        ProfileSort.BY_PING_ASC -> Icons.Default.Speed
        ProfileSort.BY_PING_DESC -> Icons.Default.Speed
    }

    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    ListItem(
        headlineContent = { 
            Text(
                text = label,
                color = color,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ) 
        },
        leadingContent = { 
            Icon(
                imageVector = icon, 
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        trailingContent = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
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
    onRefresh: () -> Unit,
    onDeleteGroup: () -> Unit,
    onPingAll: () -> Unit = {},
    onShowSortMenu: () -> Unit = {},
    // TV Actions
    onAddProfileClick: () -> Unit = {}
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
                    if (isTv) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = onAddProfileClick) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_profile))
                        }
                    }

                    IconButton(onClick = onShowSortMenu) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                    }

                    Box {
                        IconButton(onClick = onGroupMenuClick) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                        }
                        DropdownMenu(
                            expanded = onShowGroupMenu,
                            onDismissRequest = onDismissGroupMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    onDismissGroupMenu()
                                    onRefresh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ping)) },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                onClick = {
                                    onDismissGroupMenu()
                                    onPingAll()
                                }
                            )
                            
                            if (selectedGroup != null) {
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
private fun TvAddProfileMenu(
    onDismiss: () -> Unit,
    onP2PReceive: () -> Unit,
    onAddRemote: () -> Unit,
    onAddManual: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onP2PReceive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.p2p_receive))
                }
                Button(
                    onClick = onAddRemote,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remote))
                }
                Button(
                    onClick = onAddManual,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.manual_local))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ProfilesFab(
    onShowMenu: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
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
