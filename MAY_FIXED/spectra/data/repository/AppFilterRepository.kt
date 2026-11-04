package com.oklookat.spectra.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oklookat.spectra.model.*
import com.oklookat.spectra.util.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appFilterDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_filter_settings")

@Singleton
class AppFilterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private object PreferencesKeys {
        val SELECTED_PRESET_ID = stringPreferencesKey("selected_preset_id")
        val CUSTOM_PRESETS_JSON = stringPreferencesKey("custom_presets_json")
        val CACHED_RU_PACKAGES = stringPreferencesKey("cached_ru_packages")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
    }

    val selectedPresetIdFlow: Flow<String> = context.appFilterDataStore.data.map { prefs ->
        prefs[PreferencesKeys.SELECTED_PRESET_ID] ?: AppRoutingPreset.PRESET_ALL_VPN
    }

    val showSystemAppsFlow: Flow<Boolean> = context.appFilterDataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHOW_SYSTEM_APPS] ?: false
    }

    private val customPresetsFlow: Flow<List<AppRoutingPreset>> = context.appFilterDataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
        if (json.isBlank()) emptyList()
        else {
            try {
                val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
                gson.fromJson<List<AppRoutingPreset>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                LogManager.addLog("[AppFilter] Failed to parse custom presets: ${e.message}")
                emptyList()
            }
        }
    }

    private val cachedRuPackagesFlow: Flow<Set<String>> = context.appFilterDataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.CACHED_RU_PACKAGES] ?: ""
        if (json.isBlank()) emptySet()
        else {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson<Set<String>>(json, type) ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }

    val presetsFlow: Flow<List<AppRoutingPreset>> = kotlinx.coroutines.flow.combine(
        customPresetsFlow,
        cachedRuPackagesFlow
    ) { customPresets, ruPackages ->
        val defaults = AppRoutingPreset.defaultPresets().map { preset ->
            if (preset.id == AppRoutingPreset.PRESET_EXCLUDE_RU) {
                preset.copy(packages = ruPackages)
            } else preset
        }
        defaults + customPresets
    }

    val activeDisallowedPackagesFlow: Flow<Set<String>> = kotlinx.coroutines.flow.combine(
        selectedPresetIdFlow,
        presetsFlow
    ) { selectedId, presets ->
        val selectedPreset = presets.find { it.id == selectedId } ?: presets.firstOrNull()
        selectedPreset?.packages ?: emptySet()
    }

    suspend fun selectPreset(presetId: String) {
        context.appFilterDataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_PRESET_ID] = presetId
        }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.appFilterDataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOW_SYSTEM_APPS] = show
        }
    }

    suspend fun createCustomPreset(name: String, packages: Set<String>): String = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        val newPreset = AppRoutingPreset(
            id = newId,
            name = name,
            isBuiltIn = false,
            mode = AppRoutingMode.BYPASS,
            packages = packages
        )
        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            list.add(newPreset)
            prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            prefs[PreferencesKeys.SELECTED_PRESET_ID] = newId
        }
        newId
    }

    suspend fun updatePresetPackages(presetId: String, packages: Set<String>) = withContext(Dispatchers.IO) {
        if (presetId == AppRoutingPreset.PRESET_ALL_VPN) return@withContext

        if (presetId == AppRoutingPreset.PRESET_EXCLUDE_RU) {
            context.appFilterDataStore.edit { prefs ->
                prefs[PreferencesKeys.CACHED_RU_PACKAGES] = gson.toJson(packages)
            }
            return@withContext
        }

        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            val index = list.indexOfFirst { it.id == presetId }
            if (index != -1) {
                list[index] = list[index].copy(packages = packages)
                prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            }
        }
    }

    suspend fun updateCustomPresetName(presetId: String, newName: String) = withContext(Dispatchers.IO) {
        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            val index = list.indexOfFirst { it.id == presetId }
            if (index != -1 && !list[index].isBuiltIn) {
                list[index] = list[index].copy(name = newName)
                prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            }
        }
    }

    suspend fun addRegexRuleToPreset(presetId: String, regex: String) = withContext(Dispatchers.IO) {
        val cleanRegex = regex.trim()
        if (cleanRegex.isBlank()) return@withContext

        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            val index = list.indexOfFirst { it.id == presetId }
            if (index != -1 && !list[index].isBuiltIn) {
                val updatedRegexes = list[index].packageRegexes + cleanRegex
                list[index] = list[index].copy(packageRegexes = updatedRegexes)
                prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            }
        }
    }

    suspend fun removeRegexRuleFromPreset(presetId: String, regex: String) = withContext(Dispatchers.IO) {
        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            val index = list.indexOfFirst { it.id == presetId }
            if (index != -1 && !list[index].isBuiltIn) {
                val updatedRegexes = list[index].packageRegexes - regex
                list[index] = list[index].copy(packageRegexes = updatedRegexes)
                prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            }
        }
    }

    suspend fun deleteCustomPreset(presetId: String) = withContext(Dispatchers.IO) {
        context.appFilterDataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] ?: ""
            val type = object : TypeToken<List<AppRoutingPreset>>() {}.type
            val list = if (json.isBlank()) mutableListOf() else gson.fromJson<List<AppRoutingPreset>>(json, type).toMutableList()
            list.removeAll { it.id == presetId && !it.isBuiltIn }
            prefs[PreferencesKeys.CUSTOM_PRESETS_JSON] = gson.toJson(list)
            if (prefs[PreferencesKeys.SELECTED_PRESET_ID] == presetId) {
                prefs[PreferencesKeys.SELECTED_PRESET_ID] = AppRoutingPreset.PRESET_ALL_VPN
            }
        }
    }

    suspend fun fetchAndApplyRussianAppRules(): Result<Set<String>> = withContext(Dispatchers.IO) {
        val preset = AppRoutingPreset.defaultPresets().find { it.id == AppRoutingPreset.PRESET_EXCLUDE_RU }
        val url = preset?.remoteUrl ?: return@withContext Result.failure(Exception("Remote URL missing"))

        LogManager.addLog("[AppFilter] Fetching Russian app rules from $url")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw Exception("Empty body")
                val ruleData = gson.fromJson(body, PackageCategoryRule::class.java)
                
                val regexStrings = ruleData.rules?.flatMap { it.packageNameRegex ?: emptyList() } ?: emptyList()
                if (regexStrings.isEmpty()) {
                    throw Exception("No regex rules found in JSON")
                }

                val compiledRegexes = regexStrings.mapNotNull {
                    try { Regex(it) } catch (_: Exception) { null }
                }

                val installedApps = getInstalledApps()
                val matchedPackages = installedApps.filter { app ->
                    compiledRegexes.any { regex -> regex.containsMatchIn(app.packageName) }
                }.map { it.packageName }.toSet()

                context.appFilterDataStore.edit { prefs ->
                    prefs[PreferencesKeys.CACHED_RU_PACKAGES] = gson.toJson(matchedPackages)
                }

                LogManager.addLog("[AppFilter] Matched ${matchedPackages.size} Russian packages out of ${installedApps.size} installed apps")
                Result.success(matchedPackages)
            }
        } catch (e: Exception) {
            LogManager.addLog("[AppFilter] Error fetching Russian app rules: ${e.message}")
            Result.failure(e)
        }
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.filter { it.packageName != myPackage }
            .map { appInfo ->
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }
}
