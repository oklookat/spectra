package com.oklookat.spectra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oklookat.spectra.R
import com.oklookat.spectra.ui.theme.SpectraTheme
import com.oklookat.spectra.util.LogManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val logs = LogManager.logs
    val isPaused = LogManager.isPaused
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs.toList(), searchQuery) {
        if (searchQuery.isBlank()) logs.toList()
        else logs.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(filteredLogs.size) {
        if (!isPaused && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_logs)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        )
                    } else {
                        Text(stringResource(R.string.logs))
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_logs))
                        }
                    }
                    IconButton(onClick = { LogManager.isPaused = !LogManager.isPaused }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = stringResource(R.string.toggle_pause)
                        )
                    }
                    IconButton(onClick = {
                        val textToCopy = filteredLogs.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_logs))
                    }
                    IconButton(onClick = { LogManager.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_logs))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredLogs) { log ->
                LogItem(log)
            }
        }
    }
}

@Composable
fun LogItem(log: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = log,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview
@Composable
fun LogsScreenPreview() {
    SpectraTheme {
        LogsScreen()
    }
}
