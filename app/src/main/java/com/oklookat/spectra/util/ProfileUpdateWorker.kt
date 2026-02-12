package com.oklookat.spectra.util

import android.content.Context
import androidx.work.*
import com.oklookat.spectra.XrayVpnService
import java.util.concurrent.TimeUnit

class ProfileUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val profileManager = ProfileManager(applicationContext)
        val currentTime = System.currentTimeMillis()
        
        // 1. Update remote groups
        val groups = profileManager.getGroups().filter { 
            it.isRemote && it.autoUpdateEnabled && 
            (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
        }
        
        for (group in groups) {
            try {
                profileManager.updateGroupProfiles(group)
                profileManager.saveGroups(profileManager.getGroups().map { 
                    if (it.id == group.id) it.copy(lastUpdated = System.currentTimeMillis()) else it 
                })
            } catch (_: Exception) { }
        }

        // 2. Update individual remote profiles
        val profiles = profileManager.getProfiles().filter { 
            it.isRemote && !it.isImported && it.autoUpdateEnabled && 
            (currentTime - it.lastUpdated >= it.autoUpdateIntervalMinutes * 60 * 1000L)
        }
        
        var hasError = false
        for (profile in profiles) {
            val url = profile.url ?: continue
            val fileName = profile.fileName ?: continue
            
            try {
                val contentChanged = profileManager.downloadProfile(url, fileName)
                val updatedProfile = profile.copy(lastUpdated = System.currentTimeMillis())
                profileManager.updateProfile(updatedProfile)
                
                if (contentChanged && XrayVpnService.isServiceRunning && XrayVpnService.runningProfileId == profile.id) {
                    val content = profileManager.getProfileContent(updatedProfile)
                    if (!content.isNullOrEmpty()) {
                        restartVpn(applicationContext, profile.id, content)
                    }
                }
            } catch (_: Exception) {
                hasError = true
            }
        }
        
        return if (hasError) Result.retry() else Result.success()
    }

    private fun restartVpn(context: Context, profileId: String, configJson: String) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val enableIpv6 = prefs.getBoolean("enable_ipv6", false)
        val vpnAddress = prefs.getString("vpn_address", "10.0.0.1") ?: "10.0.0.1"
        val vpnDns = prefs.getString("vpn_dns", "10.0.0.2") ?: "10.0.0.2"
        val vpnAddressIpv6 = prefs.getString("vpn_address_ipv6", "fd00::1") ?: "fd00::1"
        val vpnDnsIpv6 = prefs.getString("vpn_dns_ipv6", "fd00::2") ?: "fd00::2"
        val vpnMtu = prefs.getInt("vpn_mtu", 9000)

        XrayVpnService.startOrRestart(
            context = context,
            configJson = configJson,
            profileId = profileId,
            enableIpv6 = enableIpv6,
            vpnAddress = vpnAddress,
            vpnDns = vpnDns,
            vpnAddressIpv6 = vpnAddressIpv6,
            vpnDnsIpv6 = vpnDnsIpv6,
            vpnMtu = vpnMtu
        )
    }

    companion object {
        private const val WORK_NAME = "profile_update_work"

        fun setupPeriodicWork(context: Context) {
            val profileManager = ProfileManager(context)
            val autoUpdateProfiles = profileManager.getProfiles().filter { it.isRemote && !it.isImported && it.autoUpdateEnabled }
            val autoUpdateGroups = profileManager.getGroups().filter { it.isRemote && it.autoUpdateEnabled }

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
