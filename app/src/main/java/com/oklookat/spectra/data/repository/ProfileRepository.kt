package com.oklookat.spectra.data.repository

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.ShareLinkParser
import com.oklookat.spectra.model.XrayConfigBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID

class ProfileRepository(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("profiles_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient()

    private val profilesDir = File(context.filesDir, "profiles").apply {
        if (!exists()) mkdirs()
    }

    // --- Groups Logic ---

    fun getGroups(): List<Group> {
        val json = sharedPreferences.getString("groups_list", null) ?: return listOf(createDefaultGroup())
        val type = object : TypeToken<List<Group>>() {}.type
        return try {
            val groups: List<Group> = gson.fromJson(json, type)
            if (groups.none { it.id == Group.DEFAULT_GROUP_ID }) {
                listOf(createDefaultGroup()) + groups
            } else groups
        } catch (e: Exception) {
            listOf(createDefaultGroup())
        }
    }

    private fun createDefaultGroup() = Group(id = Group.DEFAULT_GROUP_ID, name = "Default")

    fun saveGroups(groups: List<Group>) {
        val json = gson.toJson(groups)
        sharedPreferences.edit {
            putString("groups_list", json)
        }
    }

    suspend fun addRemoteGroup(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int
    ): Result<Group> = withContext(Dispatchers.IO) {
        val groups = getGroups().toMutableList()
        if (groups.any { it.name == name }) {
            return@withContext Result.failure(Exception("Group with this name already exists"))
        }

        val groupId = UUID.randomUUID().toString()
        val newGroup = Group(
            id = groupId,
            name = name,
            url = url,
            isRemote = true,
            autoUpdateEnabled = autoUpdate,
            autoUpdateIntervalMinutes = interval,
            lastUpdated = System.currentTimeMillis()
        )

        try {
            updateGroupProfiles(newGroup)
            groups.add(newGroup)
            saveGroups(groups)
            Result.success(newGroup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupProfiles(group: Group) = withContext(Dispatchers.IO) {
        val url = group.url ?: return@withContext
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch group: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Empty response body")
            
            val decodedBody = try {
                String(Base64.decode(body.trim(), Base64.DEFAULT))
            } catch (e: Exception) {
                body
            }

            val links = decodedBody.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && (it.startsWith("vless://") || it.startsWith("vmess://") || it.startsWith("ss://") || it.startsWith("trojan://")) }

            if (links.isEmpty()) return@withContext

            val currentProfiles = getProfiles().toMutableList()
            val toRemove = currentProfiles.filter { it.groupId == group.id }
            toRemove.forEach { profile ->
                profile.fileName?.let { File(profilesDir, it).delete() }
            }
            currentProfiles.removeAll(toRemove)

            links.forEachIndexed { index, link ->
                try {
                    val shareLink = ShareLinkParser.parse(link)
                    val profileId = UUID.randomUUID().toString()
                    val fileName = "$profileId.txt" // Store raw link
                    File(profilesDir, fileName).writeText(link)

                    val profile = Profile(
                        id = profileId,
                        groupId = group.id,
                        name = shareLink.getDisplayName(),
                        url = link,
                        isRemote = true,
                        isImported = true,
                        fileName = fileName,
                        lastUpdated = System.currentTimeMillis()
                    )
                    currentProfiles.add(profile)
                } catch (_: Exception) {}
            }
            saveProfiles(currentProfiles)
        }
    }

    fun deleteGroup(groupId: String) {
        if (groupId == Group.DEFAULT_GROUP_ID) return
        
        val groups = getGroups().toMutableList()
        groups.removeAll { it.id == groupId }
        saveGroups(groups)

        val profiles = getProfiles().toMutableList()
        val toDelete = profiles.filter { it.groupId == groupId }
        toDelete.forEach { profile ->
            profile.fileName?.let { File(profilesDir, it).delete() }
        }
        profiles.removeAll(toDelete)
        saveProfiles(profiles)
    }

    // --- Profiles Logic ---

    fun getProfiles(): List<Profile> {
        val json = sharedPreferences.getString("profiles_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Profile>>() {}.type
        return try {
            val profiles: List<Profile> = gson.fromJson(json, type)
            // Ensure groupId is not null after loading from GSON
            profiles.map { 
                if (it.groupId == null) it.copy(groupId = Group.DEFAULT_GROUP_ID) else it 
            }
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
        interval: Int,
        groupId: String = Group.DEFAULT_GROUP_ID
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
                groupId = groupId,
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
        if (!file.exists()) return null
        val content = file.readText(Charset.defaultCharset()).trim()
        
        if (content.startsWith("vless://") || content.startsWith("vmess://") || 
            content.startsWith("ss://") || content.startsWith("trojan://")) {
            return try {
                val shareLink = ShareLinkParser.parse(content)
                val outbound = XrayConfigBuild.fromShareLink(shareLink)
                if (outbound != null) {
                    XrayConfigBuild.buildFullConfig(outbound)
                } else {
                    content
                }
            } catch (e: Exception) {
                content
            }
        }
        
        return content
    }

    fun saveLocalProfile(name: String, content: String, groupId: String = Group.DEFAULT_GROUP_ID): Profile {
        val profiles = getProfiles().toMutableList()
        val profileId = UUID.randomUUID().toString()
        val fileName = "$profileId.json"
        
        val file = File(profilesDir, fileName)
        file.writeText(content)

        val newProfile = Profile(
            id = profileId,
            groupId = groupId,
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
