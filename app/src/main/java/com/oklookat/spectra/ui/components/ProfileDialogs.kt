package com.oklookat.spectra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.util.TvUtils

@Composable
fun GroupDialog(
    group: Group? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Boolean, Int) -> Unit,
    isNameUnique: (String) -> Boolean
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var url by remember { mutableStateOf(group?.url ?: "") }
    var autoUpdate by remember { mutableStateOf(group?.autoUpdateEnabled ?: false) }
    var interval by remember { mutableStateOf(group?.autoUpdateIntervalMinutes?.toString() ?: "60") }

    val isNameValid = name.isNotBlank() && (group != null || isNameUnique(name))
    val isUrlValid = url.isBlank() || url.startsWith("http")
    val isIntervalValid = (interval.toIntOrNull() ?: 0) >= 15

    val isTv = TvUtils.isTv(LocalContext.current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (group == null) R.string.add_group else R.string.edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    isError = !isNameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url_optional_remote)) },
                    isError = !isUrlValid && url.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (url.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.auto_update),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        AppSwitch(
                            checked = autoUpdate,
                            onCheckedChange = { autoUpdate = it },
                            isTv = isTv
                        )
                    }

                    if (autoUpdate) {
                        OutlinedTextField(
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
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(name, url.ifBlank { null }, autoUpdate, interval.toIntOrNull() ?: 60) },
                enabled = isNameValid && isUrlValid && (!autoUpdate || isIntervalValid),
                isTv = isTv
            ) {
                Text(stringResource(if (group == null) R.string.add else R.string.save))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, isTv = isTv) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
    var interval by remember { mutableStateOf(profile?.autoUpdateIntervalMinutes?.toString() ?: "60") }

    val isNameValid = name.isNotBlank() && (profile != null || isNameUnique(name))
    val isUrlValid = url.isNotBlank() && url.startsWith("http")
    val isIntervalValid = (interval.toIntOrNull() ?: 0) >= 15

    val isTv = TvUtils.isTv(LocalContext.current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (profile == null) R.string.add_profile else R.string.edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    isError = !isNameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    isError = !isUrlValid && url.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.auto_update),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AppSwitch(
                        checked = autoUpdate,
                        onCheckedChange = { autoUpdate = it },
                        isTv = isTv
                    )
                }

                if (autoUpdate) {
                    OutlinedTextField(
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
            AppButton(
                onClick = { onConfirm(name, url, autoUpdate, interval.toIntOrNull() ?: 60) },
                enabled = isNameValid && isUrlValid && (!autoUpdate || isIntervalValid),
                isTv = isTv
            ) {
                Text(stringResource(if (profile == null) R.string.add else R.string.save))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, isTv = isTv) {
                Text(stringResource(R.string.cancel))
            }
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

    val isNameValid = name.isNotBlank() && (profile != null || isNameUnique(name))
    val isContentValid = content.isNotBlank()

    val isTv = TvUtils.isTv(LocalContext.current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (profile == null) R.string.add_profile else R.string.edit))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    isError = !isNameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.configuration_json)) },
                    isError = !isContentValid && content.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(name, content) },
                enabled = isNameValid && isContentValid,
                isTv = isTv
            ) {
                Text(stringResource(if (profile == null) R.string.add else R.string.save))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, isTv = isTv) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
