package com.oklookat.spectra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oklookat.spectra.R
import com.oklookat.spectra.util.TvUtils

@Composable
fun AppUpdateDialog(
    versionName: String,
    changelog: String?,
    isDownloading: Boolean,
    progress: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = TvUtils.isTv(LocalContext.current)
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text(text = stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.update_dialog_message, versionName))
                if (!changelog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(R.string.downloading_update))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                AppButton(onClick = onConfirm, isTv = isTv) {
                    Text(text = stringResource(R.string.update_now))
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                AppTextButton(onClick = onDismiss, isTv = isTv) {
                    Text(text = stringResource(R.string.later))
                }
            }
        }
    )
}

@Composable
fun ReplaceConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = TvUtils.isTv(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            AppButton(onClick = onConfirm, isTv = isTv) {
                Text(text = stringResource(R.string.replace))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, isTv = isTv) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EasyImportVerifyDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = TvUtils.isTv(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.enable_easy_import)) },
        text = { Text(text = stringResource(R.string.easy_import_desc)) },
        confirmButton = {
            AppButton(onClick = onConfirm, isTv = isTv) {
                Text(text = stringResource(R.string.configure))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, isTv = isTv) {
                Text(text = stringResource(R.string.later))
            }
        }
    )
}
