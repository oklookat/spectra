package com.oklookat.spectra.data.repository

import android.content.Context
import android.util.Base64
import com.oklookat.spectra.data.ProfileDao
import com.oklookat.spectra.model.Group
import com.oklookat.spectra.model.Profile
import com.oklookat.spectra.model.ShareLinkParser
import com.oklookat.spectra.model.XrayConfigBuild
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProfileDao
) {
    private val sharedPreferences = context.getSharedPreferences("profiles_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    private val profilesDir = File(context.filesDir, "profiles").apply {
        if (!exists()) mkdirs()
    }

    // --- Groups Logic ---

    fun getGroupsFlow(): Flow<List<Group>> = dao.getAllGroupsFlow()

    suspend fun getGroups(): List<Group> = withContext(Dispatchers.IO) {
        val groups = dao.getAllGroups()
        if (groups.isEmpty()) {
            val default = createDefaultGroup()
            dao.insertGroup(default)
            listOf(default)
        } else {
            groups
        }
    }

    private fun createDefaultGroup() = Group(id = Group.DEFAULT_GROUP_ID, name = "Default")

    suspend fun saveGroup(group: Group) = withContext(Dispatchers.IO) {
        dao.insertGroup(group)
    }

    suspend fun addRemoteGroup(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int
    ): Result<Group> = withContext(Dispatchers.IO) {
        val groups = dao.getAllGroups()
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
            dao.insertGroup(newGroup)
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

            val currentProfiles = dao.getProfilesByGroup(group.id)
            currentProfiles.forEach { profile ->
                profile.fileName?.let { File(profilesDir, it).delete() }
            }
            dao.deleteProfilesByGroup(group.id)

            val newProfiles = links.mapIndexed { index, link ->
                val shareLink = ShareLinkParser.parse(link)
                val profileId = UUID.randomUUID().toString()
                val fileName = "$profileId.txt"
                File(profilesDir, fileName).writeText(link)

                Profile(
                    id = profileId,
                    groupId = group.id,
                    name = shareLink.getDisplayName(),
                    url = link,
                    isRemote = true,
                    isImported = true,
                    fileName = fileName,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            dao.insertProfiles(newProfiles)
        }
    }

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        if (groupId == Group.DEFAULT_GROUP_ID) return@withContext
        
        val profilesToDelete = dao.getProfilesByGroup(groupId)
        profilesToDelete.forEach { profile ->
            profile.fileName?.let { File(profilesDir, it).delete() }
        }
        
        dao.deleteProfilesByGroup(groupId)
        dao.deleteGroup(groupId)
    }

    // --- Profiles Logic ---

    fun getProfilesFlow(): Flow<List<Profile>> = dao.getAllProfilesFlow()

    suspend fun getProfiles(): List<Profile> = dao.getAllProfiles()

    fun getSelectedProfileId(): String? {
        return sharedPreferences.getString("selected_profile_id", null)
    }

    fun setSelectedProfileId(id: String?) {
        sharedPreferences.edit().putString("selected_profile_id", id).apply()
    }

    suspend fun addRemoteProfile(
        name: String,
        url: String,
        autoUpdate: Boolean,
        interval: Int,
        groupId: String = Group.DEFAULT_GROUP_ID
    ): Result<Profile> = withContext(Dispatchers.IO) {
        val profiles = dao.getAllProfiles()
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
            dao.insertProfile(newProfile)
            Result.success(newProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(updatedProfile: Profile) = withContext(Dispatchers.IO) {
        dao.updateProfile(updatedProfile)
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

    suspend fun deleteProfiles(ids: Set<String>) = withContext(Dispatchers.IO) {
        val allProfiles = dao.getAllProfiles()
        val toDelete = allProfiles.filter { it.id in ids }
        
        toDelete.forEach { profile ->
            profile.fileName?.let {
                File(profilesDir, it).delete()
            }
        }
        
        dao.deleteProfiles(ids)
        
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

    suspend fun saveLocalProfile(name: String, content: String, groupId: String = Group.DEFAULT_GROUP_ID): Profile = withContext(Dispatchers.IO) {
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
        dao.insertProfile(newProfile)
        newProfile
    }

    fun updateLocalProfileContent(profile: Profile, content: String): Boolean {
        val file = getProfileFile(profile) ?: return false
        val oldContent = if (file.exists()) file.readText() else null
        if (oldContent == content) return false
        file.writeText(content)
        return true
    }
}
