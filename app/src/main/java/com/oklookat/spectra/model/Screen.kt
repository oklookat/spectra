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

enum class Screen(val icon: ImageVector, @StringRes val labelRes: Int, val showInNav: Boolean = true) {
    Main(Icons.Default.Home, R.string.nav_home),
    Profiles(Icons.Default.Person, R.string.nav_profiles),
    Logs(Icons.AutoMirrored.Filled.List, R.string.nav_logs),
    Settings(Icons.Default.Settings, R.string.nav_settings),
    Resources(Icons.Default.Folder, R.string.nav_resources, showInNav = false)
}
