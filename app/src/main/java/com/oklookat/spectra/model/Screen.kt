package com.oklookat.spectra.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.oklookat.spectra.R

enum class Screen(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val showInNav: Boolean = true
) {
    Main("main", Icons.Default.Home, R.string.home),
    Profiles("profiles", Icons.Default.Person, R.string.profiles),
    Logs("logs", Icons.AutoMirrored.Filled.List, R.string.logs),
    Settings("settings", Icons.Default.Settings, R.string.settings),
    Resources("resources", Icons.Default.Folder, R.string.resources, showInNav = false),
    AppFilter("app_filter", Icons.Default.Apps, R.string.app_filter, showInNav = false),
    WifiExclusions("wifi_exclusions", Icons.Default.Wifi, R.string.wifi_exclusions, showInNav = false),
    DnsRouting("dns_routing", Icons.AutoMirrored.Filled.AltRoute, R.string.dns_routing_rules, showInNav = false),
    P2PDebug("p2p_debug", Icons.Default.BugReport, R.string.p2p_debug, showInNav = false);

    companion object {
        fun fromRoute(route: String?): Screen = entries.find { it.route == route } ?: Main
    }
}
