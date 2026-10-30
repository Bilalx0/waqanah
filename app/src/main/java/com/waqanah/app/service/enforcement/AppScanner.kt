package com.waqanah.app.service.enforcement

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class AppScanner(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun installedApps(): List<AppInfo> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
        } else {
            @Suppress("DEPRECATION")
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(flags as PackageManager.ApplicationInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(flags as Int)
        }

        return apps.map { appInfo ->
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(
                packageName = appInfo.packageName,
                label = label,
                isSystem = isSystem
            )
        }
    }

    fun vpnPackages(): Set<String> {
        val intent = Intent("android.net.VpnService")
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        return services.map { it.serviceInfo.packageName }.toSet()
    }

    /**
     * Queries web browser apps on the system.
     * Note: A host-less http URI ("http://") matches only real browsers,
     * not apps that handle specific domains (such as YouTube, Twitter, etc.).
     */
    fun browserPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }

        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, flags)
        }

        return activities.map { it.activityInfo.packageName }.toSet()
    }
}
