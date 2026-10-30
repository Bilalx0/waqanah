package com.waqanah.app.service.enforcement

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.waqanah.app.service.admin.DeviceOwnerManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppEnforcer(
    private val context: Context,
    private val deviceOwnerManager: DeviceOwnerManager
) {

    private val devicePolicyManager =
        context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val mutex = Mutex()

    suspend fun apply(plan: EnforcementPlan, mode: EnforcementMode): EnforcementReport = mutex.withLock {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot apply enforcement: Device Owner is not active.")
            return EnforcementReport(applied = emptyList(), unchanged = emptyList(), failed = emptyMap(), skipped = plan.skipped)
        }

        val applied = mutableListOf<EnforcementTarget>()
        val unchanged = mutableListOf<EnforcementTarget>()
        val failed = mutableMapOf<String, String>()

        for (target in plan.targets) {
            val pkg = target.packageName
            try {
                when (mode) {
                    EnforcementMode.HIDE_AND_SUSPEND -> {
                        val isAlreadyHidden = devicePolicyManager.isApplicationHidden(
                            deviceOwnerManager.adminComponent,
                            pkg
                        )
                        if (isAlreadyHidden) {
                            unchanged.add(target)
                            continue
                        }

                        // Order: suspend FIRST, then hide
                        val suspendFailed = devicePolicyManager.setPackagesSuspended(
                            deviceOwnerManager.adminComponent,
                            arrayOf(pkg),
                            true
                        )
                        if (suspendFailed.contains(pkg)) {
                            failed[pkg] = "Failed to suspend package"
                            continue
                        }

                        val hideSuccess = devicePolicyManager.setApplicationHidden(
                            deviceOwnerManager.adminComponent,
                            pkg,
                            true
                        )
                        if (hideSuccess) {
                            applied.add(target)
                        } else {
                            failed[pkg] = "Failed to hide application"
                        }
                    }

                    EnforcementMode.UNINSTALL -> {
                        val packageInstaller = context.packageManager.packageInstaller
                        val intent = Intent(context, UninstallResultReceiver::class.java).apply {
                            action = "com.waqanah.app.ACTION_UNINSTALL_COMPLETE"
                            setPackage(context.packageName)
                            putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, pkg)
                        }
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            pkg.hashCode(),
                            intent,
                            flags
                        )
                        packageInstaller.uninstall(pkg, pendingIntent.intentSender)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying enforcement on package $pkg", e)
                failed[pkg] = e.localizedMessage ?: "SecurityException or unknown error"
            }
        }

        return EnforcementReport(
            applied = applied,
            unchanged = unchanged,
            failed = failed,
            skipped = plan.skipped
        )
    }

    suspend fun releaseAll(hiddenPackages: Map<String, String>): EnforcementReport = mutex.withLock {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot release packages: Device Owner is not active.")
            return EnforcementReport(applied = emptyList(), unchanged = emptyList(), failed = emptyMap(), skipped = emptyList())
        }

        val releasedTargets = mutableListOf<EnforcementTarget>()
        val failed = mutableMapOf<String, String>()

        for ((pkg, reasonStr) in hiddenPackages) {
            try {
                // Order: unhide FIRST, then unsuspend
                val unhideSuccess = devicePolicyManager.setApplicationHidden(
                    deviceOwnerManager.adminComponent,
                    pkg,
                    false
                )

                val unsuspendFailed = devicePolicyManager.setPackagesSuspended(
                    deviceOwnerManager.adminComponent,
                    arrayOf(pkg),
                    false
                )

                if (unhideSuccess && !unsuspendFailed.contains(pkg)) {
                    val reason = try {
                        EnforcementReason.valueOf(reasonStr)
                    } catch (_: Exception) {
                        EnforcementReason.BLOCKLIST
                    }
                    releasedTargets.add(EnforcementTarget(pkg, pkg, reason))
                } else {
                    failed[pkg] = "Failed to unhide or unsuspend package"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing package $pkg", e)
                failed[pkg] = e.localizedMessage ?: "Error during release"
            }
        }

        return EnforcementReport(
            applied = releasedTargets,
            unchanged = emptyList(),
            failed = failed,
            skipped = emptyList()
        )
    }

    companion object {
        private const val TAG = "AppEnforcer"
    }
}
