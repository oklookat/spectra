package com.oklookat.spectra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Profile

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
    onDeleteClick: () -> Unit,
    onPingClick: () -> Unit = {}
) {
    var showItemMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(if (isFocused && isTv) 1.04f else 1.0f, label = "scale")
    
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused && isTv -> MaterialTheme.colorScheme.primary
            isSelectedForDeletion -> MaterialTheme.colorScheme.errorContainer
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && isTv -> MaterialTheme.colorScheme.onPrimary
            isSelectedForDeletion -> MaterialTheme.colorScheme.onErrorContainer
            isActive -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "contentColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (isTv) showItemMenu = true
                    onLongClick()
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (isActive || (isFocused && isTv)) {
            BorderStroke(
                width = 2.dp,
                color = if (isFocused && isTv) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.secondary
            )
        } else if (isSelectedForDeletion) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive || isFocused) 2.dp else 0.dp
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(vertical = 4.dp),
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) MaterialTheme.colorScheme.secondary 
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(if (isTv) 48.dp else 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isActive -> Icons.Default.CheckCircle
                                profile.isRemote -> Icons.Default.Cloud
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onSecondary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (isTv) 28.dp else 24.dp)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = profile.name,
                    style = if (isTv) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            },
            supportingContent = {
                val subtitle = if (profile.isRemote && !profile.url.isNullOrEmpty()) {
                    profile.url.removePrefix("https://").removePrefix("http://")
                } else {
                    stringResource(if (profile.isRemote) R.string.remote_profile else R.string.local_profile)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.8f)
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (profile.lastPing != 0L) {
                        PingBadge(ping = profile.lastPing, isFocused = isFocused && isTv)
                    }
                    
                    if (!isTv) {
                        IconButton(
                            onClick = { showItemMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More",
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    ProfileDropdownMenu(
                        expanded = showItemMenu,
                        profile = profile,
                        isTv = isTv,
                        onDismiss = { showItemMenu = false },
                        onPingClick = onPingClick,
                        onEditClick = onEditClick,
                        onRefreshClick = onRefreshClick,
                        onShareP2PClick = onShareP2PClick,
                        onDeleteClick = onDeleteClick
                    )
                }
            }
        )
    }
}

@Composable
private fun PingBadge(ping: Long, isFocused: Boolean) {
    val color = when {
        ping < 0 -> Color(0xFFF44336)
        ping < 100 -> Color(0xFF4CAF50)
        ping < 200 -> Color(0xFFFFB300)
        else -> Color(0xFFF44336)
    }
    
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isFocused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) 
                else color.copy(alpha = 0.15f),
        border = if (isFocused) BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary) else null
    ) {
        Text(
            text = "$ping ms",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ProfileDropdownMenu(
    expanded: Boolean,
    profile: Profile,
    isTv: Boolean,
    onDismiss: () -> Unit,
    onPingClick: () -> Unit,
    onEditClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onShareP2PClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ping)) },
            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
            onClick = {
                onDismiss()
                onPingClick()
            }
        )
        if (!profile.isImported) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onEditClick()
                }
            )
        }
        if (profile.isRemote && !profile.isImported) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onRefreshClick()
                }
            )
        }
        if (!isTv) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.p2p_share)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onShareP2PClick()
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onDeleteClick()
            },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error
            )
        )
    }
}
