package com.waqanah.app.service.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waqanah.app.WaqanahApplication
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.service.enforcement.ShieldServiceController
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class EnforcementWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = context.applicationContext as? WaqanahApplication ?: return Result.success()
        val preferences = WaqanahPreferences(context)

        val isOwner = app.deviceOwnerManager.isDeviceOwner()
        val isEnabled = preferences.enforcementEnabledFlow.first()

        if (isOwner && isEnabled) {
            app.enforcementManager.enforceNow()
            ShieldServiceController.sync(context)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "waqanah_enforcement"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<EnforcementWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
