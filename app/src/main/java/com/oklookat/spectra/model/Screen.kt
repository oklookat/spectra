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
    Main("main", Icons.Default.Home, R.string.home),
    Profiles("profiles", Icons.Default.Person, R.string.profiles),
    Logs("logs", Icons.AutoMirrored.Filled.List, R.string.logs),
    Settings("settings", Icons.Default.Settings, R.string.settings),
    Resources("resources", Icons.Default.Folder, R.string.resources, showInNav = false);

    companion object {
        fun fromRoute(route: String?): Screen = entries.find { it.route == route } ?: Main
    }
}
