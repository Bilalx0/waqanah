package com.waqanah.app.service.hardening

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.waqanah.app.service.admin.DeviceOwnerManager

class HardeningManager(
    private val context: Context,
    private val deviceOwnerManager: DeviceOwnerManager
) {

    private val devicePolicyManager =
        context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val ownPackage: String = context.packageName

    suspend fun applyAll(dnsHost: String): List<PolicyResult> {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot apply hardening: Device Owner is not active.")
            return PolicyId.entries.map { id ->
                PolicyResult(id, PolicyState.FAILED, "Device Owner is not active")
            }
        }

        val results = mutableListOf<PolicyResult>()
        val admin = deviceOwnerManager.adminComponent

        // 1. UNINSTALL_BLOCKED
        results.add(tryPolicy(PolicyId.UNINSTALL_BLOCKED) {
            if (devicePolicyManager.isUninstallBlocked(admin, ownPackage)) {
                PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.UNCHANGED)
            } else {
                devicePolicyManager.setUninstallBlocked(admin, ownPackage, true)
                PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.APPLIED)
            }
        })

        // 2. FORCE_STOP_BLOCKED
        results.add(tryPolicy(PolicyId.FORCE_STOP_BLOCKED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val disabledPackages = devicePolicyManager.getUserControlDisabledPackages(admin)
                if (disabledPackages.contains(ownPackage)) {
                    PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.UNCHANGED)
                } else {
                    devicePolicyManager.setUserControlDisabledPackages(admin, listOf(ownPackage))
                    PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.APPLIED)
                }
            } else {
                PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.UNSUPPORTED, "Requires Android 11+ (API 30)")
            }
        })

        // 3. SAFE_BOOT_BLOCKED
        results.add(tryPolicy(PolicyId.SAFE_BOOT_BLOCKED) {
            val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
            if (userRestrictions.getBoolean(UserManager.DISALLOW_SAFE_BOOT)) {
                PolicyResult(PolicyId.SAFE_BOOT_BLOCKED, PolicyState.UNCHANGED)
            } else {
                devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                PolicyResult(PolicyId.SAFE_BOOT_BLOCKED, PolicyState.APPLIED)
            }
        })

        // 4. VPN_CONFIG_BLOCKED
        results.add(tryPolicy(PolicyId.VPN_CONFIG_BLOCKED) {
            val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
            if (userRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_VPN)) {
                PolicyResult(PolicyId.VPN_CONFIG_BLOCKED, PolicyState.UNCHANGED)
            } else {
                devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
                PolicyResult(PolicyId.VPN_CONFIG_BLOCKED, PolicyState.APPLIED)
            }
        })

        // 5. DATE_TIME_LOCKED
        results.add(tryPolicy(PolicyId.DATE_TIME_LOCKED) {
            val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
            val isRestricted = userRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_DATE_TIME)

            if (!isRestricted) {
                devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                devicePolicyManager.setAutoTimeEnabled(admin, true)
                devicePolicyManager.setAutoTimeZoneEnabled(admin, true)
            } else {
                @Suppress("DEPRECATION")
                devicePolicyManager.setAutoTimeRequired(admin, true)
            }

            if (isRestricted) {
                PolicyResult(PolicyId.DATE_TIME_LOCKED, PolicyState.UNCHANGED)
            } else {
                PolicyResult(PolicyId.DATE_TIME_LOCKED, PolicyState.APPLIED)
            }
        })

        // 6. PRIVATE_DNS_LOCKED
        results.add(tryPolicy(PolicyId.PRIVATE_DNS_LOCKED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentMode = devicePolicyManager.getGlobalPrivateDnsMode(admin)
                val currentHost = devicePolicyManager.getGlobalPrivateDnsHost(admin)

                if (currentMode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME && currentHost == dnsHost) {
                    val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
                    if (!userRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)) {
                        devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                    }
                    PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.UNCHANGED)
                } else {
                    val dnsResult = devicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost(admin, dnsHost)
                    if (dnsResult == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                        devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                        PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.APPLIED)
                    } else {
                        val errMsg = when (dnsResult) {
                            DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> "Private DNS host not serving"
                            DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING -> "Failed setting Private DNS (check network)"
                            else -> "Private DNS error code $dnsResult"
                        }
                        PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.FAILED, errMsg)
                    }
                }
            } else {
                PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.UNSUPPORTED, "Requires Android 10+ (API 29)")
            }
        })

        return results
    }

    suspend fun releaseAll(): List<PolicyResult> {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot release hardening: Device Owner is not active.")
            return PolicyId.entries.map { id ->
                PolicyResult(id, PolicyState.FAILED, "Device Owner is not active")
            }
        }

        val results = mutableListOf<PolicyResult>()
        val admin = deviceOwnerManager.adminComponent

        // 1. UNINSTALL_BLOCKED
        results.add(tryPolicy(PolicyId.UNINSTALL_BLOCKED) {
            devicePolicyManager.setUninstallBlocked(admin, ownPackage, false)
            PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.RELEASED)
        })

        // 2. FORCE_STOP_BLOCKED
        results.add(tryPolicy(PolicyId.FORCE_STOP_BLOCKED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                devicePolicyManager.setUserControlDisabledPackages(admin, emptyList())
                PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.RELEASED)
            } else {
                PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.UNSUPPORTED)
            }
        })

        // 3. SAFE_BOOT_BLOCKED
        results.add(tryPolicy(PolicyId.SAFE_BOOT_BLOCKED) {
            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            PolicyResult(PolicyId.SAFE_BOOT_BLOCKED, PolicyState.RELEASED)
        })

        // 4. VPN_CONFIG_BLOCKED
        results.add(tryPolicy(PolicyId.VPN_CONFIG_BLOCKED) {
            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
            PolicyResult(PolicyId.VPN_CONFIG_BLOCKED, PolicyState.RELEASED)
        })

        // 5. DATE_TIME_LOCKED
        results.add(tryPolicy(PolicyId.DATE_TIME_LOCKED) {
            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
            PolicyResult(PolicyId.DATE_TIME_LOCKED, PolicyState.RELEASED)
        })

        // 6. PRIVATE_DNS_LOCKED
        results.add(tryPolicy(PolicyId.PRIVATE_DNS_LOCKED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                devicePolicyManager.setGlobalPrivateDnsModeOpportunistic(admin)
                PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.RELEASED)
            } else {
                PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.UNSUPPORTED)
            }
        })

        return results
    }

    private inline fun tryPolicy(id: PolicyId, block: () -> PolicyResult): PolicyResult {
        return try {
            block()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid argument for policy $id", e)
            PolicyResult(id, PolicyState.FAILED, "Invalid argument: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating policy $id", e)
            PolicyResult(id, PolicyState.FAILED, e.localizedMessage ?: "Unknown policy error")
        }
    }

    companion object {
        private const val TAG = "HardeningManager"
    }
}
