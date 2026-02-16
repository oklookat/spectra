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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    val scale by animateFloatAsState(if (isFocused && isTv) 1.03f else 1.0f, label = "scale")
    
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused && isTv -> MaterialTheme.colorScheme.primaryContainer
            isSelectedForDeletion -> MaterialTheme.colorScheme.errorContainer
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isFocused && isTv) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isActive -> Icons.Default.CheckCircle
                    profile.isRemote -> Icons.Default.Cloud
                    else -> Icons.Default.Description
                }, 
                contentDescription = if (isActive) stringResource(R.string.active) else null,
                tint = if (isActive || isFocused) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name, 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive || isFocused) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                if (profile.isRemote && !profile.url.isNullOrEmpty()) {
                    Text(
                        text = profile.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (profile.lastPing >= 0) {
                val pingColor = when {
                    profile.lastPing < 200 -> Color(0xFF4CAF50)
                    profile.lastPing < 500 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                Text(
                    text = "${profile.lastPing} ms",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = pingColor,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            Box {
                IconButton(onClick = { showItemMenu = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert, 
                        contentDescription = "More",
                        tint = if (isFocused) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                DropdownMenu(
                    expanded = showItemMenu,
                    onDismissRequest = { showItemMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ping)) },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        onClick = {
                            showItemMenu = false
                            onPingClick()
                        }
                    )
                    if (!profile.isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showItemMenu = false
                                onEditClick()
                            }
                        )
                    }
                    if (profile.isRemote && !profile.isImported) {
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
