package com.oklookat.spectra.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

class ResourceRepository(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("resources_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient()
    private val mutex = Mutex()

    private val resourcesDir = context.filesDir

    private val reservedNames = setOf("_geoip.dat", "_geosite.dat")
    private val defaultGeoFiles = setOf("geoip.dat", "geosite.dat")

    fun getResources(): List<Resource> {
        val json = sharedPreferences.getString("resources_list", null) ?: return getDefaultResources()
        val type = object : TypeToken<List<Resource>>() {}.type
        val stored: List<Resource> = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        val current = stored.toMutableList()
        defaultGeoFiles.forEach { name ->
            if (current.none { it.name == name }) {
                current.add(Resource(name = name, isDefault = true, size = getFileSize(name)))
            }
        }

        return current.map { it.copy(size = getFileSize(it.name)) }
    }

    private fun getDefaultResources(): List<Resource> {
        return defaultGeoFiles.map { Resource(name = it, isDefault = true, size = getFileSize(it)) }
    }

    private fun getFileSize(name: String): Long {
        val file = File(resourcesDir, name)
        if (file.exists()) return file.length()
        
        return try {
            context.assets.openFd(name).use { it.length }
        } catch (e: Exception) {
            0
        }
    }

    private fun saveResourcesInternal(resources: List<Resource>) {
        val json = gson.toJson(resources.filter { !it.isDefault || it.url != null })
        sharedPreferences.edit {
            putString("resources_list", json)
        }
    }

    suspend fun saveResources(resources: List<Resource>) = mutex.withLock {
        saveResourcesInternal(resources)
    }

    /**
     * @return Result with true if file was downloaded, false if not modified (304)
     */
    suspend fun addOrUpdateResource(
        name: String,
        url: String?,
        autoUpdate: Boolean,
        interval: Int,
        fileBytes: ByteArray? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (name in reservedNames) {
            return@withContext Result.failure(Exception("Name '$name' is reserved"))
        }

        if (name in defaultGeoFiles) {
            val defaultFile = File(resourcesDir, name)
            val backupFile = File(resourcesDir, "_$name")
            if (defaultFile.exists() && !backupFile.exists()) {
                defaultFile.renameTo(backupFile)
            }
        }

        try {
            var wasDownloaded = true
            var newEtag: String? = null
            if (url != null) {
                val existing = getResources().find { it.name == name }
                val etagToUse = if (existing?.url == url) existing.etag else null
                
                newEtag = downloadResource(url, name, etagToUse, onProgress)
                wasDownloaded = newEtag != etagToUse || etagToUse == null
            } else if (fileBytes != null) {
                File(resourcesDir, name).writeBytes(fileBytes)
            }

            mutex.withLock {
                val resources = getResources().toMutableList()
                val existingIndex = resources.indexOfFirst { it.name == name }
                val newResource = Resource(
                    name = name,
                    url = url,
                    autoUpdateEnabled = autoUpdate,
                    autoUpdateIntervalHours = interval.coerceAtLeast(1),
                    lastUpdated = System.currentTimeMillis(),
                    isDefault = name in defaultGeoFiles,
                    etag = newEtag ?: (resources.getOrNull(existingIndex)?.etag)
                )

                if (existingIndex != -1) {
                    resources[existingIndex] = newResource
                } else {
                    resources.add(newResource)
                }

                saveResourcesInternal(resources)
            }
            Result.success(wasDownloaded)
        } catch (e: Exception) {
            if (url != null) {
                val file = File(resourcesDir, name)
                if (file.exists() && getFileSize(name) == 0L) {
                    file.delete()
                }
            }
            Result.failure(e)
        }
    }

    suspend fun downloadResource(
        url: String,
        name: String,
        currentEtag: String? = null,
        onProgress: (Float) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        if (currentEtag != null) {
            requestBuilder.header("If-None-Match", currentEtag)
        }
        
        val request = requestBuilder.build()
        val file = File(resourcesDir, name)

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 304) {
                    LogManager.addLog("[Resource] $name: Not modified (304, ETag matched: $currentEtag)")
                    return@withContext currentEtag
                }
                
                if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")
                
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                val etag = response.header("ETag")
                var totalBytesRead: Long = 0

                LogManager.addLog("[Resource] $name: Downloading... ETag: $etag")

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalBytesRead.toFloat() / contentLength)
                            }
                        }
                        output.flush()
                    }
                }
                LogManager.addLog("[Resource] $name: Download complete")
                return@withContext etag
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteResource(name: String) = mutex.withLock {
        val file = File(resourcesDir, name)
        if (file.exists()) file.delete()

        if (name in defaultGeoFiles) {
            val backupFile = File(resourcesDir, "_$name")
            if (backupFile.exists()) {
                backupFile.renameTo(File(resourcesDir, name))
            }
        }

        val resources = getResources().toMutableList()
        resources.removeAll { it.name == name }
        saveResourcesInternal(resources)
    }

    suspend fun reloadAll(onProgress: (Float) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val resourcesToReload = getResources().filter { it.url != null }
        if (resourcesToReload.isEmpty()) return@withContext Result.success(Unit)
        
        var hasError = false
        
        resourcesToReload.forEach { res ->
            try {
                val newEtag = downloadResource(res.url!!, res.name, res.etag, onProgress)
                mutex.withLock {
                    val updatedResources = getResources().toMutableList()
                    val index = updatedResources.indexOfFirst { it.name == res.name }
                    if (index != -1) {
                        updatedResources[index] = updatedResources[index].copy(
                            etag = newEtag,
                            lastUpdated = System.currentTimeMillis()
                        )
                        saveResourcesInternal(updatedResources)
                    }
                }
            } catch (e: Exception) {
                hasError = true
                LogManager.addLog("[Resource] Reload error for ${res.name}: ${e.message}")
            }
        }
        if (hasError) Result.failure(Exception("Some files failed to download")) else Result.success(Unit)
    }
}
