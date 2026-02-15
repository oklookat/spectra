package com.oklookat.spectra.util

import android.content.Context
import androidx.work.*
import com.oklookat.spectra.data.repository.ResourceRepository
import java.util.concurrent.TimeUnit

class ResourceUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val resourceRepository = ResourceRepository(applicationContext)
        val resources = resourceRepository.getResources().filter { it.url != null && it.autoUpdateEnabled }
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
                } catch (e: Exception) {
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
            val resourceRepository = ResourceRepository(context)
            val autoUpdateResources = resourceRepository.getResources().filter { it.url != null && it.autoUpdateEnabled }

            if (autoUpdateResources.isEmpty()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val minIntervalHours = autoUpdateResources.minOf { it.autoUpdateIntervalHours }.toLong().coerceAtLeast(1)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ResourceUpdateWorker>(minIntervalHours, TimeUnit.HOURS)
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
