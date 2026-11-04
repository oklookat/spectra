package com.oklookat.spectra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.oklookat.spectra.R
import kotlinx.coroutines.delay

@Composable
fun ProfilesFab(
    onShowMenu: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onP2PReceive: () -> Unit,
    onAddGroup: () -> Unit,
    onAddProfile: () -> Unit
) {
    Box {
        FloatingActionButton(onClick = onShowMenu) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.p2p_receive)) },
                onClick = {
                    onDismissMenu()
                    onP2PReceive()
                },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_group)) },
                onClick = {
                    onDismissMenu()
                    onAddGroup()
                },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_profile)) },
                onClick = {
                    onDismissMenu()
                    onAddProfile()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) }
            )
        }
    }
}

@Composable
fun AddProfileMethodDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onAddRemote: () -> Unit,
    onImportFile: () -> Unit,
    onAddManual: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    if (isTv) {
        Dialog(onDismissRequest = onDismiss) {
            LaunchedEffect(Unit) {
                delay(300)
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.add_profile),
                        style = MaterialTheme.typography.titleLarge, // Уменьшено до размера TopAppBar
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileMethodItem(
                            title = stringResource(R.string.remote),
                            subtitle = stringResource(R.string.remote_desc),
                            icon = Icons.Default.CloudDownload,
                            isTv = true,
                            focusRequester = firstItemFocusRequester,
                            onClick = onAddRemote
                        )
                        ProfileMethodItem(
                            title = stringResource(R.string.import_from_file),
                            subtitle = stringResource(R.string.import_file_desc),
                            icon = Icons.Default.FileOpen,
                            isTv = true,
                            onClick = onImportFile
                        )
                        ProfileMethodItem(
                            title = stringResource(R.string.manual_local),
                            subtitle = stringResource(R.string.manual_local_desc),
                            icon = Icons.Default.EditNote,
                            isTv = true,
                            onClick = onAddManual
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.add_profile),
                    style = MaterialTheme.typography.titleLarge // Унифицировано с TopAppBar
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileMethodItem(
                        title = stringResource(R.string.remote),
                        subtitle = stringResource(R.string.remote_desc),
                        icon = Icons.Default.CloudDownload,
                        isTv = false,
                        onClick = onAddRemote
                    )
                    ProfileMethodItem(
                        title = stringResource(R.string.import_from_file),
                        subtitle = stringResource(R.string.import_file_desc),
                        icon = Icons.Default.FileOpen,
                        isTv = false,
                        onClick = onImportFile
                    )
                    ProfileMethodItem(
                        title = stringResource(R.string.manual_local),
                        subtitle = stringResource(R.string.manual_local_desc),
                        icon = Icons.Default.EditNote,
                        isTv = false,
                        onClick = onAddManual
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TvAddMenu(
    onDismiss: () -> Unit,
    onP2PReceive: () -> Unit,
    onAddGroup: () -> Unit,
    onAddProfile: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(300)
            firstItemFocusRequester.requestFocus()
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.add),
                    style = MaterialTheme.typography.titleLarge, // Уменьшено
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogMenuItem(
                        title = stringResource(R.string.p2p_receive),
                        icon = Icons.Default.QrCodeScanner,
                        focusRequester = firstItemFocusRequester,
                        onClick = { onP2PReceive(); onDismiss() }
                    )
                    DialogMenuItem(
                        title = stringResource(R.string.add_group),
                        icon = Icons.Default.CreateNewFolder,
                        onClick = { onAddGroup(); onDismiss() }
                    )
                    DialogMenuItem(
                        title = stringResource(R.string.add_profile),
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        onClick = { onAddProfile(); onDismiss() }
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogMenuItem(
    title: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val containerColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    )
    val contentColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProfileMethodItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(if (isFocused && isTv) 1.05f else 1.0f, label = "scale")
    
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused && isTv -> MaterialTheme.colorScheme.primaryContainer
            isTv -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "containerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isFocused && isTv) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (isTv) 8.dp else 12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(if (isTv) 16.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 44.dp else 40.dp)
                    .background(
                        color = if (isFocused && isTv) MaterialTheme.colorScheme.primary 
                                else if (isTv) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused && isTv) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isTv) 26.dp else 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = if (isTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = if (isFocused && isTv) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isFocused && isTv) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}
