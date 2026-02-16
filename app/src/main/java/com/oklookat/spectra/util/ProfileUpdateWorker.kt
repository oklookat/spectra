package com.oklookat.spectra.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.oklookat.spectra.data.repository.ProfileRepository
import com.oklookat.spectra.service.XrayVpnService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ProfileUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val profileRepository: ProfileRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val currentTime = System.currentTimeMillis()
        
        // 1. Update remote groups
        val groups = try {
            profileRepository.getGroups().filter { 
                it.isRemote && it.autoUpdateEnabled && 
                (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        for (group in groups) {
            try {
                profileRepository.updateGroupProfiles(group)
                profileRepository.saveGroup(group.copy(lastUpdated = System.currentTimeMillis()))
            } catch (_: Exception) { }
        }

        // 2. Update individual remote profiles
        val profiles = try {
            profileRepository.getProfiles().filter {
                it.isRemote && !it.isImported && it.autoUpdateEnabled &&
                (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        var hasError = false
        for (profile in profiles) {
            val url = profile.url ?: continue
            val fileName = profile.fileName ?: continue

            try {
                val contentChanged = profileRepository.downloadProfile(url, fileName)
                val updatedProfile = profile.copy(lastUpdated = System.currentTimeMillis())
                profileRepository.updateProfile(updatedProfile)
            } catch (_: Exception) {
                hasError = true
            }
        }
        
        return if (hasError) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "profile_update_work"

        fun setupPeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ProfileUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
