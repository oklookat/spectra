package com.oklookat.spectra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
