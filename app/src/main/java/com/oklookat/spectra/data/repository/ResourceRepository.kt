package com.oklookat.spectra.data.repository

import android.content.Context
import com.oklookat.spectra.data.ResourceDao
import com.oklookat.spectra.model.Resource
import com.oklookat.spectra.util.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ResourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ResourceDao
) {
    private val client = OkHttpClient()

    private val resourcesDir = context.filesDir

    private val reservedNames = setOf("_geoip.dat", "_geosite.dat")
    private val defaultGeoFiles = setOf("geoip.dat", "geosite.dat")

    fun getResourcesFlow(): Flow<List<Resource>> {
        return dao.getAllResourcesFlow().map { stored ->
            val current = stored.toMutableList()
            defaultGeoFiles.forEach { name ->
                if (current.none { it.name == name }) {
                    current.add(Resource(name = name, isDefault = true, size = getFileSize(name)))
                }
            }
            current.map { it.copy(size = getFileSize(it.name)) }
        }
    }

    suspend fun getResources(): List<Resource> = withContext(Dispatchers.IO) {
        val stored = dao.getAllResources()
        val current = stored.toMutableList()
        defaultGeoFiles.forEach { name ->
            if (current.none { it.name == name }) {
                current.add(Resource(name = name, isDefault = true, size = getFileSize(name)))
            }
        }
        current.map { it.copy(size = getFileSize(it.name)) }
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

    suspend fun saveResources(resources: List<Resource>) = withContext(Dispatchers.IO) {
        dao.insertResources(resources)
    }

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
                val existing = dao.getAllResources().find { it.name == name }
                val etagToUse = if (existing?.url == url) existing.etag else null
                
                newEtag = downloadResource(url, name, etagToUse, onProgress)
                wasDownloaded = newEtag != etagToUse || etagToUse == null
            } else if (fileBytes != null) {
                File(resourcesDir, name).writeBytes(fileBytes)
            }

            val existing = dao.getAllResources().find { it.name == name }
            val newResource = Resource(
                name = name,
                url = url,
                autoUpdateEnabled = autoUpdate,
                autoUpdateIntervalHours = interval.coerceAtLeast(1),
                lastUpdated = System.currentTimeMillis(),
                isDefault = name in defaultGeoFiles,
                etag = newEtag ?: existing?.etag
            )

            dao.insertResource(newResource)
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

    suspend fun deleteResource(name: String) = withContext(Dispatchers.IO) {
        val file = File(resourcesDir, name)
        if (file.exists()) file.delete()

        if (name in defaultGeoFiles) {
            val backupFile = File(resourcesDir, "_$name")
            if (backupFile.exists()) {
                backupFile.renameTo(File(resourcesDir, name))
            }
        }

        dao.deleteResource(name)
    }

    suspend fun reloadAll(onProgress: (Float) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val resourcesToReload = getResources().filter { it.url != null }
        if (resourcesToReload.isEmpty()) return@withContext Result.success(Unit)
        
        var hasError = false
        
        resourcesToReload.forEach { res ->
            try {
                val newEtag = downloadResource(res.url!!, res.name, res.etag, onProgress)
                val updatedRes = res.copy(
                    etag = newEtag,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.insertResource(updatedRes)
            } catch (e: Exception) {
                hasError = true
                LogManager.addLog("[Resource] Reload error for ${res.name}: ${e.message}")
            }
        }
        if (hasError) Result.failure(Exception("Some files failed to download")) else Result.success(Unit)
    }
}
