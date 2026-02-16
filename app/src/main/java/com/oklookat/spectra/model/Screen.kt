package com.oklookat.spectra.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import com.oklookat.spectra.R

enum class Screen(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val showInNav: Boolean = true
) {
    Main("main", Icons.Default.Home, R.string.nav_home),
    Profiles("profiles", Icons.Default.Person, R.string.nav_profiles),
    Logs("logs", Icons.AutoMirrored.Filled.List, R.string.nav_logs),
    Settings("settings", Icons.Default.Settings, R.string.nav_settings),
    Resources("resources", Icons.Default.Folder, R.string.nav_resources, showInNav = false);

    companion object {
        fun fromRoute(route: String?): Screen = entries.find { it.route == route } ?: Main
    }
}
