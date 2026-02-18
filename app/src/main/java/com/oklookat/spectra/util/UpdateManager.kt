package com.oklookat.spectra.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.model.AppUpdate
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()

    private val apkFile get() = File(context.externalCacheDir, "update.apk")
    private val etagFile get() = File(context.externalCacheDir, "update.etag")

    // --- Cache helpers ---

    private fun readCachedEtag(): String? =
        etagFile.takeIf { it.exists() }?.readText()?.trim()

    private fun writeCachedEtag(etag: String) =
        etagFile.writeText(etag)

    private fun readApkVersionCode(): Long? {
        if (!apkFile.exists()) return null
        val info = context.packageManager.getPackageArchiveInfo(apkFile.path, 0)
        return info?.longVersionCode
    }

    private fun clearCache() {
        apkFile.takeIf { it.exists() }?.delete()
        etagFile.takeIf { it.exists() }?.delete()
        LogManager.addLog("[Update] Cache cleared")
    }

    // --- Update check ---

    suspend fun checkForUpdates(updateUrl: String): AppUpdate? = withContext(Dispatchers.IO) {
        if (updateUrl.isBlank()) {
            LogManager.addLog("[Update] Check skipped: update URL is blank")
            return@withContext null
        }

        LogManager.addLog("[Update] Device ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        LogManager.addLog("[Update] Checking for updates at $updateUrl")

        val request = try {
            Request.Builder().url(updateUrl).build()
        } catch (e: IllegalArgumentException) {
            LogManager.addLog("[Update] Invalid URL format: $updateUrl")
            return@withContext null
        }

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = when (response.code) {
                        404 -> "File not found (404)"
                        403 -> "Access denied (403)"
                        500 -> "Server error (500)"
                        else -> "HTTP error ${response.code}"
                    }
                    LogManager.addLog("[Update] Check failed: $errorMsg")
                    return@withContext null
                }

                val body = response.body?.string() ?: run {
                    LogManager.addLog("[Update] Check failed: empty response body")
                    return@withContext null
                }

                val update = try {
                    gson.fromJson(body, AppUpdate::class.java)
                } catch (e: Exception) {
                    LogManager.addLog("[Update] Failed to parse update JSON: ${e.localizedMessage ?: "Invalid format"}")
                    return@withContext null
                }

                if (update.versionCode <= BuildConfig.VERSION_CODE) {
                    // Already on latest version — wipe cache if it exists
                    LogManager.addLog("[Update] App is up to date (current: ${BuildConfig.VERSION_NAME})")
                    clearCache()
                    return@withContext null
                }

                // New version available — check if cached APK is still useful
                val cachedApkVersion = readApkVersionCode()
                if (cachedApkVersion != null && cachedApkVersion < BuildConfig.VERSION_CODE) {
                    // Cached APK is older than currently installed app — useless, drop it
                    LogManager.addLog("[Update] Cached APK (versionCode=$cachedApkVersion) is older than installed app, clearing cache")
                    clearCache()
                }

                LogManager.addLog("[Update] New version available: ${update.versionName} (${update.versionCode})")
                update
            }
        } catch (e: UnknownHostException) {
            LogManager.addLog("[Update] Error: Host not found. Check internet connection or URL.")
            null
        } catch (e: Exception) {
            LogManager.addLog("[Update] Error checking for updates: ${e.localizedMessage ?: e.javaClass.simpleName}")
            null
        }
    }

    // --- Download & install ---

    suspend fun downloadAndInstallApk(update: AppUpdate, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val apkUrl = getBestApkUrl(update) ?: run {
            LogManager.addLog("[Update] No compatible APK found for device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            return@withContext false
        }

        val request = try {
            Request.Builder()
                .url(apkUrl)
                .apply {
                    // If we have a cached APK with a matching versionCode, send its ETag.
                    // The server will return 304 Not Modified if the file hasn't changed,
                    // saving a full re-download.
                    val cachedEtag = readCachedEtag()
                    val cachedApkVersion = readApkVersionCode()
                    if (cachedEtag != null && cachedApkVersion == update.versionCode.toLong()) {
                        header("If-None-Match", cachedEtag)
                        LogManager.addLog("[Update] Sending If-None-Match: $cachedEtag")
                    }
                }
                .build()
        } catch (e: IllegalArgumentException) {
            LogManager.addLog("[Update] Invalid APK URL: $apkUrl")
            return@withContext false
        }

        LogManager.addLog("[Update] Starting download: $apkUrl")

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> {
                        // Server confirmed our cached APK is still current — install it directly
                        LogManager.addLog("[Update] Server returned 304 Not Modified — using cached APK")
                        withContext(Dispatchers.Main) { installApk(apkFile) }
                        return@withContext true
                    }

                    !response.isSuccessful -> {
                        LogManager.addLog("[Update] Download failed: HTTP ${response.code}")
                        return@withContext false
                    }
                }

                val body = response.body ?: run {
                    LogManager.addLog("[Update] Download failed: empty response body")
                    return@withContext false
                }

                val totalBytes = body.contentLength()

                // Stream APK to disk
                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }

                // Verify the downloaded APK reports the expected versionCode
                val downloadedVersion = readApkVersionCode()
                if (downloadedVersion != update.versionCode.toLong()) {
                    LogManager.addLog("[Update] APK versionCode mismatch: expected ${update.versionCode}, got $downloadedVersion — aborting")
                    clearCache()
                    return@withContext false
                }

                // Save ETag for future requests (skip caching if server didn't provide one)
                val newEtag = response.header("ETag")
                if (newEtag != null) {
                    writeCachedEtag(newEtag)
                    LogManager.addLog("[Update] ETag cached: $newEtag")
                } else {
                    etagFile.takeIf { it.exists() }?.delete()
                    LogManager.addLog("[Update] Server did not provide ETag — skipping ETag cache")
                }

                LogManager.addLog("[Update] Download complete (versionCode=$downloadedVersion), launching installer")
                withContext(Dispatchers.Main) { installApk(apkFile) }
                return@withContext true
            }
        } catch (e: Exception) {
            LogManager.addLog("[Update] Error during download/install: ${e.localizedMessage ?: e.javaClass.simpleName}")
            false
        }
    }

    // --- Helpers ---

    private fun getBestApkUrl(update: AppUpdate): String? {
        update.apkUrls?.let { urls ->
            for (abi in Build.SUPPORTED_ABIS) {
                if (urls.containsKey(abi)) {
                    LogManager.addLog("[Update] Found matching APK for ABI: $abi")
                    return urls[abi]
                }
            }
        }
        // Fall back to the universal APK URL if no ABI-specific one matched
        return update.apkUrl
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
