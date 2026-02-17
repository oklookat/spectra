package com.oklookat.spectra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    selectedGroup: Group?,
    groups: List<Group>,
    selectedGroupIndex: Int,
    isTv: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
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
    onPingAll: () -> Unit,
    onShowSortMenu: () -> Unit,
    onAddProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (scrollBehavior.state.contentOffset < 0f) 3.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedCount))
                    } else {
                        Text(stringResource(R.string.profiles))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = onDeleteSelection) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    } else {
                        if (isTv) {
                            IconButton(onClick = onAddProfileClick) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }

                        IconButton(onClick = onShowSortMenu) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                        }

                        Box {
                            IconButton(onClick = onGroupMenuClick) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = onShowGroupMenu,
                                onDismissRequest = onDismissGroupMenu
                            ) {
                                if (selectedGroup != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.refresh)) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onRefresh()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.refresh_all_remote)) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onRefresh()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ping)) },
                                    onClick = {
                                        onDismissGroupMenu()
                                        onPingAll()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
                                )

                                if (selectedGroup != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share_group)) },
                                        onClick = {
                                            onDismissGroupMenu()
                                            onShareGroup()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                    )
                                    if (selectedGroup.id != Group.DEFAULT_GROUP_ID) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.edit)) },
                                            onClick = {
                                                onDismissGroupMenu()
                                                onEditGroup()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                        )
                                    }

                                    if (selectedGroup.id != Group.DEFAULT_GROUP_ID) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            onClick = {
                                                onDismissGroupMenu()
                                                onDeleteGroup()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
            
            if (!isSelectionMode) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedGroupIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
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
                                    Text(group.name)
                                    if (group.isRemote) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Cloud,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
