package com.waqanah.app.service.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.waqanah.app.data.local.WaqanahPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UninstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown uninstall status"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        Log.i(TAG, "Successfully uninstalled package: $packageName")
                        if (packageName.isNotBlank()) {
                            val preferences = WaqanahPreferences(context.applicationContext)
                            preferences.addUninstalledPackages(setOf(packageName))
                        }
                    }
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        Log.w(TAG, "Uninstall pending user action for $packageName")
                    }
                    else -> {
                        Log.e(TAG, "Uninstall failed for $packageName: status=$status, message=$message")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling uninstall result broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UninstallResultReceiver"
    }
}
