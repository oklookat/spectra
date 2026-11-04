package com.oklookat.spectra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.unit.sp
import com.oklookat.spectra.R
import com.oklookat.spectra.model.Profile

private fun extractProtocolAndTransportFromJson(jsonStr: String): Pair<String, String>? {
    try {
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null

        val element = com.google.gson.JsonParser.parseString(trimmed)

        fun parseOutboundObj(outbound: com.google.gson.JsonObject): Pair<String, String>? {
            val proto = outbound.get("protocol")?.asString?.uppercase() ?: return null
            if (proto in listOf("FREEDOM", "BLACKHOLE", "DIRECT", "BYPASS", "DNS")) return null

            var transport = "TCP"
            if (outbound.has("streamSettings") && outbound.get("streamSettings").isJsonObject) {
                val stream = outbound.getAsJsonObject("streamSettings")
                val net = stream.get("network")?.asString?.uppercase() ?: "TCP"
                val sec = stream.get("security")?.asString?.uppercase()
                transport = if (!sec.isNullOrBlank() && sec != "NONE" && sec != "ASIS") {
                    "$net+$sec"
                } else {
                    net
                }
            }
            return proto to transport
        }

        // 1. If element is array of outbounds
        if (element.isJsonArray) {
            val array = element.asJsonArray
            for (i in 0 until array.size()) {
                val item = array.get(i)
                if (item.isJsonObject) {
                    val res = parseOutboundObj(item.asJsonObject)
                    if (res != null) return res
                }
            }
            return null
        }

        if (!element.isJsonObject) return null
        val root = element.asJsonObject

        // 2. Check "outbounds" array
        if (root.has("outbounds") && root.get("outbounds").isJsonArray) {
            val outbounds = root.getAsJsonArray("outbounds")
            for (i in 0 until outbounds.size()) {
                val item = outbounds.get(i)
                if (item.isJsonObject) {
                    val res = parseOutboundObj(item.asJsonObject)
                    if (res != null) return res
                }
            }
        }

        // 3. Check "outbound" object
        if (root.has("outbound") && root.get("outbound").isJsonObject) {
            val res = parseOutboundObj(root.getAsJsonObject("outbound"))
            if (res != null) return res
        }

        // 4. Check root itself
        val res = parseOutboundObj(root)
        if (res != null) return res

    } catch (_: Exception) {
    }
    return null
}

private fun getProfileProtocolAndTransport(profile: Profile, profileContent: String?): Pair<String, String>? {
    val candidateText = sequenceOf(profile.url, profileContent)
        .filterNotNull()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() } ?: return null

    // 1. Try parsing proxy share link
    if (com.oklookat.spectra.model.ShareLinkParser.isShareLink(candidateText)) {
        return try {
            val node = com.oklookat.spectra.model.ShareLinkParser.parseNode(candidateText)
            val protocol = node.protocol.uppercase().ifBlank { "PROXY" }
            val transport = buildString {
                if (node.network.isNotBlank()) {
                    append(node.network.uppercase())
                } else {
                    append("TCP")
                }
                val sec = node.security?.uppercase()
                if (!sec.isNullOrBlank() && sec != "NONE" && sec != "ASIS") {
                    append("+$sec")
                }
            }
            protocol to transport
        } catch (_: Exception) {
            null
        }
    }

    // 2. Try extracting from candidateText JSON
    val jsonRes = extractProtocolAndTransportFromJson(candidateText)
    if (jsonRes != null) return jsonRes

    // 3. Try extracting from profileContent JSON if distinct from candidateText
    if (profileContent != null && profileContent != candidateText) {
        val contentJsonRes = extractProtocolAndTransportFromJson(profileContent)
        if (contentJsonRes != null) return contentJsonRes
    }

    // 4. Fallback for JSON files
    if (candidateText.startsWith("{") || profile.fileName?.endsWith(".json", ignoreCase = true) == true) {
        return "JSON" to "XRAY"
    }

    return null
}

fun maskPrivacyText(text: String): String {
    if (text.isBlank()) return text

    if (text.contains("://")) {
        val scheme = text.substringBefore("://")
        val rest = text.substringAfter("://")
        return "$scheme://" + maskMiddle(rest)
    }

    val ipRegex = Regex("""^(\d{1,3}\.\d{1,3})\.\d{1,3}\.\d{1,3}(:\d+)?$""")
    val ipMatch = ipRegex.find(text)
    if (ipMatch != null) {
        val prefix = ipMatch.groupValues[1]
        val hasPort = ipMatch.groupValues[2]
        return "$prefix.*.*" + if (hasPort.isNotEmpty()) ":***" else ""
    }

    return maskMiddle(text)
}

private fun maskMiddle(str: String): String {
    if (str.length <= 4) return "****"
    val keepStart = (str.length * 0.25).toInt().coerceIn(1, 4)
    val keepEnd = (str.length * 0.25).toInt().coerceIn(1, 4)
    if (keepStart + keepEnd >= str.length) {
        return str.take(2) + "***"
    }
    return str.take(keepStart) + "***" + str.takeLast(keepEnd)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileItem(
    profile: Profile,
    isSelectedForDeletion: Boolean,
    isActive: Boolean,
    isTv: Boolean,
    profileContent: String? = null,
    isPrivacyModeEnabled: Boolean = false,
    groupName: String? = null,
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
    
    val (protocol, transport) = remember(profile.id, profile.url, profileContent) {
        getProfileProtocolAndTransport(profile, profileContent) ?: (null to null)
    }

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
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        animationMode = MarqueeAnimationMode.Immediately
                    )
                )
            },
            supportingContent = {
                val rawSubtitle = if (profile.isRemote && !profile.url.isNullOrEmpty()) {
                    profile.url.removePrefix("https://").removePrefix("http://")
                } else {
                    stringResource(if (profile.isRemote) R.string.remote_profile else R.string.local_profile)
                }
                val subtitle = if (isPrivacyModeEnabled && profile.isRemote && !profile.url.isNullOrEmpty()) {
                    maskPrivacyText(rawSubtitle)
                } else {
                    rawSubtitle
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!groupName.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = if (isActive) MaterialTheme.colorScheme.secondary
                                               else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = if (isActive) MaterialTheme.colorScheme.secondary
                                               else MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 120.dp)
                                    )
                                }
                            }
                        }
                        if (protocol != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = protocol,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        if (!transport.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = transport,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                    color = contentColor.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
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
