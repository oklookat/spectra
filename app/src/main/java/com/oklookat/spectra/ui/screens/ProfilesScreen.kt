package com.oklookat.spectra.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.P2PPayload
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.ui.components.*
import com.oklookat.spectra.ui.viewmodel.MainViewModel
import com.oklookat.spectra.ui.viewmodel.ProfileSort
import com.oklookat.spectra.ui.viewmodel.ProfilesViewModel
import com.oklookat.spectra.util.TvUtils

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
    val gridState = rememberLazyGridState()
    
    var selectedGroupIndex by remember { mutableIntStateOf(0) }
    val selectedGroup = remember(selectedGroupIndex, uiState.groups) {
        if (selectedGroupIndex == 0) null 
        else uiState.groups.getOrNull(selectedGroupIndex - 1)
    }

    val targetGroupId = remember(selectedGroup) {
        if (selectedGroup == null || selectedGroup.isRemote) {
            Group.DEFAULT_GROUP_ID
        } else {
            selectedGroup.id
        }
    }

    LaunchedEffect(selectedGroupIndex) {
        if (isTv) gridState.scrollToItem(0) else listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.sortOrder) {
        if (isTv) gridState.scrollToItem(0) else listState.scrollToItem(0)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var showAddProfileMethodDialog by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showLocalDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var importedContent by remember { mutableStateOf("") }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    var showP2PScanner by remember { mutableStateOf(false) }
    var p2pPayloadToSend by remember { mutableStateOf<P2PPayload?>(null) }

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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ProfilesTopBar(
                isSelectionMode = isSelectionMode,
                selectedCount = selectedIds.size,
                selectedGroup = selectedGroup,
                groups = uiState.groups,
                selectedGroupIndex = selectedGroupIndex,
                isTv = isTv,
                scrollBehavior = scrollBehavior,
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
                    selectedGroup?.let { group ->
                        p2pPayloadToSend = if (group.isRemote) {
                            P2PPayload(
                                group = group,
                                groupProfiles = null
                            )
                        } else {
                            val groupProfiles = uiState.profiles
                                .filter { it.groupId == group.id }
                                .map { it to viewModel.getProfileContent(it) }
                            
                            P2PPayload(
                                group = group,
                                groupProfiles = groupProfiles
                            )
                        }
                        showP2PScanner = true
                    }
                },
                onEditGroup = {
                    editingGroup = selectedGroup
                    showGroupDialog = true
                },
                onRefresh = { viewModel.refresh(selectedGroup?.id) },
                onDeleteGroup = { viewModel.deleteGroup(selectedGroup!!.id) },
                onPingAll = { viewModel.measureAllPings(selectedGroup?.id) },
                onShowSortMenu = { showSortMenu = true },
                onAddProfileClick = { showMenu = true }
            )
        },
        floatingActionButton = {
            if (!isTv && !isSelectionMode) {
                ProfilesFab(
                    onShowMenu = { showMenu = true },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onP2PReceive = { mainViewModel.startP2PServer() },
                    onAddGroup = {
                        editingGroup = null
                        showGroupDialog = true
                    },
                    onAddProfile = {
                        showAddProfileMethodDialog = true
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

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh(selectedGroup?.id) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (filteredProfiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.no_profiles_yet),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Add a new profile or group to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAddProfileMethodDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Profile")
                        }
                    }
                }
            } else {
                if (isTv) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items = filteredProfiles, key = { it.id }) { profile ->
                            ProfileItem(
                                profile = profile,
                                isSelectedForDeletion = selectedIds.contains(profile.id),
                                isActive = uiState.selectedProfileId == profile.id,
                                isTv = true,
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
                                    p2pPayloadToSend = P2PPayload(
                                        profile = profile,
                                        profileContent = viewModel.getProfileContent(profile)
                                    )
                                    showP2PScanner = true
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
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = filteredProfiles, key = { it.id }) { profile ->
                            ProfileItem(
                                profile = profile,
                                isSelectedForDeletion = selectedIds.contains(profile.id),
                                isActive = uiState.selectedProfileId == profile.id,
                                isTv = false,
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
                                    p2pPayloadToSend = P2PPayload(
                                        profile = profile,
                                        profileContent = viewModel.getProfileContent(profile)
                                    )
                                    showP2PScanner = true
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
                }
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

    if (isTv && showMenu) {
        TvAddMenu(
            onDismiss = { showMenu = false },
            onP2PReceive = { 
                showMenu = false
                mainViewModel.startP2PServer() 
            },
            onAddGroup = {
                showMenu = false
                editingGroup = null
                showGroupDialog = true
            },
            onAddProfile = {
                showMenu = false
                showAddProfileMethodDialog = true
            }
        )
    }

    if (showAddProfileMethodDialog) {
        AddProfileMethodDialog(
            isTv = isTv,
            onDismiss = { showAddProfileMethodDialog = false },
            onAddRemote = {
                showAddProfileMethodDialog = false
                editingProfile = null
                showRemoteDialog = true
            },
            onImportFile = {
                showAddProfileMethodDialog = false
                filePicker.launch("application/json")
            },
            onAddManual = {
                showAddProfileMethodDialog = false
                editingProfile = null
                importedContent = ""
                showLocalDialog = true
            }
        )
    }

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

    if (showP2PScanner) {
        P2PScannerDialog(
            onDismiss = { 
                showP2PScanner = false
                p2pPayloadToSend = null
            },
            onQrScanned = { url, token ->
                p2pPayloadToSend?.let { payload ->
                    mainViewModel.sendP2PPayload(url, token, payload)
                }
                showP2PScanner = false
                p2pPayloadToSend = null
            }
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
                viewModel.saveRemoteProfile(editingProfile, name, url, autoUpdate, interval, targetGroupId) {
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
                viewModel.saveLocalProfile(editingProfile, name, content, targetGroupId) {
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

@Composable
fun SortDialog(
    selectedSort: ProfileSort,
    onSortSelected: (ProfileSort) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort)) },
        text = {
            Column {
                ProfileSort.entries.forEach { sort ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onSortSelected(sort)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedSort == sort,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(sort.labelResId))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selectedSort: ProfileSort,
    onSortSelected: (ProfileSort) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.sort),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            ProfileSort.entries.forEach { sort ->
                ListItem(
                    headlineContent = { Text(stringResource(sort.labelResId)) },
                    leadingContent = {
                        RadioButton(
                            selected = selectedSort == sort,
                            onClick = null
                        )
                    },
                    modifier = Modifier.clickable {
                        onSortSelected(sort)
                        onDismiss()
                    }
                )
            }
        }
    }
}
