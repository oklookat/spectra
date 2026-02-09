package com.oklookat.spectra.util

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oklookat.spectra.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID

class ProfileManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("profiles_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient()

    private val profilesDir = File(context.filesDir, "profiles").apply {
        if (!exists()) mkdirs()
    }

    fun getProfiles(): List<Profile> {
        val json = sharedPreferences.getString("profiles_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Profile>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveProfiles(profiles: List<Profile>) {
        val json = gson.toJson(profiles)
        sharedPreferences.edit {
            putString("profiles_list", json)
        }
    }

    fun getSelectedProfileId(): String? {
        return sharedPreferences.getString("selected_profile_id", null)
    }

    fun setSelectedProfileId(id: String?) {
        sharedPreferences.edit {
            putString("selected_profile_id", id)
        }
    }

    suspend fun addRemoteProfile(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int
    ): Result<Profile> = withContext(Dispatchers.IO) {
        val profiles = getProfiles().toMutableList()
        if (profiles.any { it.name == name }) {
            return@withContext Result.failure(Exception("Profile with this name already exists"))
        }

        val profileId = UUID.randomUUID().toString()
        val fileName = "$profileId.json"
        
        try {
            downloadProfile(url, fileName)
            val newProfile = Profile(
                id = profileId,
                name = name,
                url = url,
                isRemote = true,
                autoUpdateEnabled = autoUpdate,
                autoUpdateIntervalMinutes = interval,
                lastUpdated = System.currentTimeMillis(),
                fileName = fileName
            )
            profiles.add(newProfile)
            saveProfiles(profiles)
            Result.success(newProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(updatedProfile: Profile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == updatedProfile.id }
        if (index != -1) {
            profiles[index] = updatedProfile
            saveProfiles(profiles)
        }
    }

    /**
     * Downloads profile and returns true if content has changed.
     */
    suspend fun downloadProfile(url: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val file = File(profilesDir, fileName)
        val oldContent = if (file.exists()) file.readText() else null
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")
            val newBytes = response.body?.bytes() ?: throw Exception("Empty response body")
            val newContent = String(newBytes)
            
            if (oldContent == newContent) {
                return@withContext false
            }
            
            FileOutputStream(file).use { output ->
                output.write(newBytes)
            }
            return@withContext true
        }
    }

    fun deleteProfiles(ids: Set<String>) {
        val profiles = getProfiles().toMutableList()
        val toDelete = profiles.filter { it.id in ids }
        
        toDelete.forEach { profile ->
            profile.fileName?.let {
                File(profilesDir, it).delete()
            }
        }
        
        profiles.removeAll(toDelete)
        saveProfiles(profiles)
        
        val selectedId = getSelectedProfileId()
        if (selectedId in ids) {
            setSelectedProfileId(null)
        }
    }
    
    fun getProfileFile(profile: Profile): File? {
        return profile.fileName?.let { File(profilesDir, it) }
    }

    fun getProfileContent(profile: Profile): String? {
        val file = getProfileFile(profile) ?: return null
        return if (file.exists()) file.readText(Charset.defaultCharset()) else null
    }

    fun saveLocalProfile(name: String, content: String): Profile {
        val profiles = getProfiles().toMutableList()
        val profileId = UUID.randomUUID().toString()
        val fileName = "$profileId.json"
        
        val file = File(profilesDir, fileName)
        file.writeText(content)

        val newProfile = Profile(
            id = profileId,
            name = name,
            isRemote = false,
            fileName = fileName
        )
        profiles.add(newProfile)
        saveProfiles(profiles)
        return newProfile
    }

    fun updateLocalProfileContent(profile: Profile, content: String): Boolean {
        val file = getProfileFile(profile) ?: return false
        val oldContent = if (file.exists()) file.readText() else null
        if (oldContent == content) return false
        file.writeText(content)
        return true
    }
}
