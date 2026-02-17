package com.oklookat.spectra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LinkItem(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var isFocused by remember { mutableStateOf(false) }

    Text(
        text = text,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { uriHandler.openUri(url) }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
            letterSpacing = 0.1.sp
        )
    )
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isTv: Boolean = false
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = if (isTv) {
            SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SwitchDefaults.colors()
        }
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isTv: Boolean = false,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        enabled = enabled,
        colors = if (isTv) {
            ButtonDefaults.buttonColors(
                containerColor = if (isFocused) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        content()
    }
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isTv: Boolean = false,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    TextButton(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        enabled = enabled,
        colors = if (isTv) {
            ButtonDefaults.textButtonColors(
                containerColor = if (isFocused) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
                contentColor = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.secondary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        } else {
            ButtonDefaults.textButtonColors()
        }
    ) {
        content()
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun AppListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    isTv: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val containerColor = if (isTv && isFocused) {
        MaterialTheme.colorScheme.inverseSurface
    } else {
        Color.Transparent
    }

    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = if (isTv && isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
            supportingColor = if (isTv && isFocused) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = if (isTv && isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = if (isTv && isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
    )
}
