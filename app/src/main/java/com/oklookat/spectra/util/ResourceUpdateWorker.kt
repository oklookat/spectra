package com.oklookat.spectra.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.oklookat.spectra.data.repository.ResourceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ResourceUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val resourceRepository: ResourceRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val resources = try {
            resourceRepository.getResources().filter { it.url != null && it.autoUpdateEnabled }
        } catch (_: Exception) {
            return Result.retry()
        }
        
        val currentTime = System.currentTimeMillis()
        var hasError = false
        val updatedResources = resourceRepository.getResources().toMutableList()
        
        for (resource in resources) {
            val url = resource.url ?: continue
            val intervalMillis = resource.autoUpdateIntervalHours * 60 * 60 * 1000L
            if (currentTime - resource.lastUpdated >= intervalMillis) {
                try {
                    val newEtag = resourceRepository.downloadResource(url, resource.name, resource.etag) {}
                    val index = updatedResources.indexOfFirst { it.name == resource.name }
                    if (index != -1) {
                        updatedResources[index] = updatedResources[index].copy(
                            etag = newEtag,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                } catch (_: Exception) {
                    hasError = true
                }
            }
        }
        resourceRepository.saveResources(updatedResources)
        
        return if (hasError) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "resource_update_work"

        fun setupPeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ResourceUpdateWorker>(1, TimeUnit.HOURS)
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
