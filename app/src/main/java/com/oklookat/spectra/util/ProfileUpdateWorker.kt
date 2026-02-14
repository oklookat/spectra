package com.oklookat.spectra.util

import android.content.Context
import androidx.work.*
import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.service.XrayVpnService
import java.util.concurrent.TimeUnit

class ProfileUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val profileRepository = ProfileRepository(applicationContext)
        val currentTime = System.currentTimeMillis()
        
        // 1. Update remote groups
        val groups = profileRepository.getGroups().filter { 
            it.isRemote && it.autoUpdateEnabled && 
            (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
        }
        
        for (group in groups) {
            try {
                profileRepository.updateGroupProfiles(group)
                profileRepository.saveGroups(profileRepository.getGroups().map { 
                    if (it.id == group.id) it.copy(lastUpdated = System.currentTimeMillis()) else it 
                })
            } catch (_: Exception) { }
        }

        // 2. Update individual remote profiles
        val profiles = profileRepository.getProfiles().filter { 
            it.isRemote && !it.isImported && it.autoUpdateEnabled && 
            (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
        }
        
        var hasError = false
        for (profile in profiles) {
            val url = profile.url ?: continue
            val fileName = profile.fileName ?: continue
            
            try {
                val contentChanged = profileRepository.downloadProfile(url, fileName)
                val updatedProfile = profile.copy(lastUpdated = System.currentTimeMillis())
                profileRepository.updateProfile(updatedProfile)
                
                if (contentChanged && XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id) {
                    // Logic to restart VPN if needed
                }
            } catch (_: Exception) {
                hasError = true
            }
        }
        
        return if (hasError) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "profile_update_work"

        fun setupPeriodicWork(context: Context) {
            val profileRepository = ProfileRepository(context)
            val autoUpdateProfiles = profileRepository.getProfiles().filter { it.isRemote && !it.isImported && it.autoUpdateEnabled }
            val autoUpdateGroups = profileRepository.getGroups().filter { it.isRemote && it.autoUpdateEnabled }

            if (autoUpdateProfiles.isEmpty() && autoUpdateGroups.isEmpty()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val minProfileInterval = autoUpdateProfiles.minOfOrNull { it.autoUpdateIntervalMinutes } ?: Int.MAX_VALUE
            val minGroupInterval = autoUpdateGroups.minOfOrNull { it.autoUpdateIntervalMinutes } ?: Int.MAX_VALUE
            
            val minInterval = minOf(minProfileInterval, minGroupInterval).coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ProfileUpdateWorker>(minInterval.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
