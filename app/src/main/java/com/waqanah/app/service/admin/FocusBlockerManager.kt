package com.waqanah.app.service.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

class FocusBlockerManager(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val deviceOwnerManager = DeviceOwnerManager(context)

    val adminComponent: ComponentName = deviceOwnerManager.adminComponent

    val defaultTargetPackages = listOf(
        "com.UCMobile.intl",          // UC Browser
        "com.nemo.vidmate",            // VidMate
        "com.expressvpn.vpn",          // ExpressVPN
        "com.nordvpn.android",         // NordVPN
        "com.surfshark.vpn",           // Surfshark
        "org.torproject.torbrowser"    // Tor Browser
    )

    fun isDeviceOwner(): Boolean {
        return deviceOwnerManager.isDeviceOwner()
    }

    fun suspendPackages(
        packagesToSuspend: List<String> = defaultTargetPackages,
        suspended: Boolean = true
    ): Boolean {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot suspend packages: Device Owner is not active.")
            return false
        }

        return try {
            val packagesArray = packagesToSuspend.toTypedArray()
            val failedPackages = devicePolicyManager.setPackagesSuspended(
                adminComponent,
                packagesArray,
                suspended
            )

            if (failedPackages.isNotEmpty()) {
                Log.w(TAG, "Failed to change suspension state for: ${failedPackages.joinToString()}")
            }

            if (suspended) {
                devicePolicyManager.addUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_CONFIG_VPN
                )
            } else {
                devicePolicyManager.clearUserRestriction(
                    adminComponent,
                    UserManager.DISALLOW_CONFIG_VPN
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling package suspension", e)
            false
        }
    }

    companion object {
        private const val TAG = "FocusBlockerManager"
    }
}
