package com.waqanah.app.service.enforcement

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.waqanah.app.WaqanahApplication
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.service.worker.EnforcementWorker
import kotlinx.coroutines.flow.first

object ShieldServiceController {

    private const val TAG = "ShieldServiceController"

    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val preferences = WaqanahPreferences(appContext)
        val app = appContext as? WaqanahApplication ?: return

        val isOwner = app.deviceOwnerManager.isDeviceOwner()
        val isEnabled = preferences.enforcementEnabledFlow.first()

        if (isOwner && isEnabled) {
            try {
                if (!ShieldService.running.value) {
                    val serviceIntent = Intent(appContext, ShieldService::class.java)
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                }
                EnforcementWorker.schedule(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ShieldService (${e.javaClass.simpleName}): ${e.message}. Falling back to WorkManager safety net.")
                EnforcementWorker.schedule(appContext)
            }
        } else {
            try {
                val serviceIntent = Intent(appContext, ShieldService::class.java)
                appContext.stopService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ShieldService", e)
            }
            EnforcementWorker.cancel(appContext)
        }
    }
}
