package com.oklookat.spectra.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.oklookat.spectra.BuildConfig
import com.oklookat.spectra.MainActivity
import com.oklookat.spectra.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val updateUrl = inputData.getString(KEY_UPDATE_URL) ?: return Result.failure()
        if (updateUrl.isBlank()) return Result.success()

        val updateManager = UpdateManager(applicationContext)
        val update = try {
            updateManager.checkForUpdates(updateUrl)
        } catch (_: Exception) {
            null
        }
        
        if (update != null) {
            showUpdateNotification(update.versionName)
        }
        
        return Result.success()
    }

    private fun showUpdateNotification(versionName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.app_updates_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_dialog", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle(applicationContext.getString(R.string.update_available_title))
            .setContentText(applicationContext.getString(R.string.update_available_text, versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        private const val WORK_NAME = "app_update_check"
        private const val KEY_UPDATE_URL = "update_url"

        fun setupPeriodicWork(context: Context, updateUrl: String) {
            if (updateUrl.isBlank()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val data = workDataOf(KEY_UPDATE_URL to updateUrl)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
