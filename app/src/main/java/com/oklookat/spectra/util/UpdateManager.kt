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

    suspend fun checkForUpdates(updateUrl: String): AppUpdate? = withContext(Dispatchers.IO) {
        if (updateUrl.isBlank()) {
            LogManager.addLog("[Update] Check skipped: update URL is blank")
            return@withContext null
        }

        val deviceAbis = Build.SUPPORTED_ABIS.joinToString(", ")
        LogManager.addLog("[Update] Device ABIs: $deviceAbis")
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
                
                if (update.versionCode > BuildConfig.VERSION_CODE) {
                    LogManager.addLog("[Update] New version available: ${update.versionName} (${update.versionCode})")
                    update
                } else {
                    LogManager.addLog("[Update] App is up to date (current: ${BuildConfig.VERSION_NAME})")
                    null
                }
            }
        } catch (e: UnknownHostException) {
            LogManager.addLog("[Update] Error: Host not found. Check your internet connection or URL.")
            null
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            LogManager.addLog("[Update] Error checking for updates: $detail")
            null
        }
    }

    suspend fun downloadAndInstallApk(update: AppUpdate, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val apkUrl = getBestApkUrl(update) ?: run {
            LogManager.addLog("[Update] No compatible APK found for device architecture: ${Build.SUPPORTED_ABIS.joinToString()}")
            return@withContext false
        }

        LogManager.addLog("[Update] Starting download: $apkUrl")
        val request = try {
            Request.Builder().url(apkUrl).build()
        } catch (e: IllegalArgumentException) {
            LogManager.addLog("[Update] Invalid APK URL: $apkUrl")
            return@withContext false
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LogManager.addLog("[Update] Download failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body ?: run {
                    LogManager.addLog("[Update] Download failed: empty response")
                    return@withContext false
                }
                
                val totalBytes = body.contentLength()
                val apkFile = File(context.externalCacheDir, "update.apk")
                
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
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                LogManager.addLog("[Update] Download complete, launching installer")
                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            LogManager.addLog("[Update] Error during download/install: $detail")
            false
        }
    }

    private fun getBestApkUrl(update: AppUpdate): String? {
        update.apkUrls?.let { urls ->
            for (abi in Build.SUPPORTED_ABIS) {
                if (urls.containsKey(abi)) {
                    LogManager.addLog("[Update] Found matching APK for ABI: $abi")
                    return urls[abi]
                }
            }
        }
        
        // Fallback to the single apkUrl field if map search failed or is empty
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
