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

        val supported = PolicyCatalog.supported(Build.VERSION.SDK_INT)
        val admin = deviceOwnerManager.adminComponent

        return PolicyId.entries.map { id ->
            if (!supported.contains(id)) {
                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
            } else {
                tryPolicy(id) {
                    when (id) {
                        PolicyId.UNINSTALL_BLOCKED -> {
                            if (devicePolicyManager.isUninstallBlocked(admin, ownPackage)) {
                                PolicyResult(id, PolicyState.UNCHANGED)
                            } else {
                                devicePolicyManager.setUninstallBlocked(admin, ownPackage, true)
                                PolicyResult(id, PolicyState.APPLIED)
                            }
                        }

                        PolicyId.FORCE_STOP_BLOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val disabledPackages = devicePolicyManager.getUserControlDisabledPackages(admin)
                                if (disabledPackages.contains(ownPackage)) {
                                    PolicyResult(id, PolicyState.UNCHANGED)
                                } else {
                                    devicePolicyManager.setUserControlDisabledPackages(admin, listOf(ownPackage))
                                    PolicyResult(id, PolicyState.APPLIED)
                                }
                            } else {
                                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
                            }
                        }

                        PolicyId.SAFE_BOOT_BLOCKED -> {
                            val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
                            if (userRestrictions.getBoolean(UserManager.DISALLOW_SAFE_BOOT)) {
                                PolicyResult(id, PolicyState.UNCHANGED)
                            } else {
                                devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                                PolicyResult(id, PolicyState.APPLIED)
                            }
                        }

                        PolicyId.VPN_CONFIG_BLOCKED -> {
                            val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
                            if (userRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_VPN)) {
                                PolicyResult(id, PolicyState.UNCHANGED)
                            } else {
                                devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
                                PolicyResult(id, PolicyState.APPLIED)
                            }
                        }

                        PolicyId.DATE_TIME_LOCKED -> {
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
                                PolicyResult(id, PolicyState.UNCHANGED)
                            } else {
                                PolicyResult(id, PolicyState.APPLIED)
                            }
                        }

                        PolicyId.PRIVATE_DNS_LOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val currentMode = devicePolicyManager.getGlobalPrivateDnsMode(admin)
                                val currentHost = devicePolicyManager.getGlobalPrivateDnsHost(admin)

                                if (currentMode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME && currentHost == dnsHost) {
                                    val userRestrictions = devicePolicyManager.getUserRestrictions(admin)
                                    if (!userRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)) {
                                        devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                                    }
                                    PolicyResult(id, PolicyState.UNCHANGED)
                                } else {
                                    val dnsResult = devicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost(admin, dnsHost)
                                    if (dnsResult == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                                        devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                                        PolicyResult(id, PolicyState.APPLIED)
                                    } else {
                                        val errMsg = when (dnsResult) {
                                            DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> "Private DNS host not serving"
                                            DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING -> "Failed setting Private DNS (check network)"
                                            else -> "Private DNS error code $dnsResult"
                                        }
                                        PolicyResult(id, PolicyState.FAILED, errMsg)
                                    }
                                }
                            } else {
                                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun releaseAll(): List<PolicyResult> {
        if (!deviceOwnerManager.isDeviceOwner()) {
            Log.w(TAG, "Cannot release hardening: Device Owner is not active.")
            return PolicyId.entries.map { id ->
                PolicyResult(id, PolicyState.FAILED, "Device Owner is not active")
            }
        }

        val supported = PolicyCatalog.supported(Build.VERSION.SDK_INT)
        val admin = deviceOwnerManager.adminComponent

        return PolicyId.entries.map { id ->
            if (!supported.contains(id)) {
                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
            } else {
                tryPolicy(id) {
                    when (id) {
                        PolicyId.UNINSTALL_BLOCKED -> {
                            devicePolicyManager.setUninstallBlocked(admin, ownPackage, false)
                            PolicyResult(id, PolicyState.RELEASED)
                        }

                        PolicyId.FORCE_STOP_BLOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                devicePolicyManager.setUserControlDisabledPackages(admin, emptyList())
                                PolicyResult(id, PolicyState.RELEASED)
                            } else {
                                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
                            }
                        }

                        PolicyId.SAFE_BOOT_BLOCKED -> {
                            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                            PolicyResult(id, PolicyState.RELEASED)
                        }

                        PolicyId.VPN_CONFIG_BLOCKED -> {
                            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
                            PolicyResult(id, PolicyState.RELEASED)
                        }

                        PolicyId.DATE_TIME_LOCKED -> {
                            devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
                            PolicyResult(id, PolicyState.RELEASED)
                        }

                        PolicyId.PRIVATE_DNS_LOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                devicePolicyManager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                                devicePolicyManager.setGlobalPrivateDnsModeOpportunistic(admin)
                                PolicyResult(id, PolicyState.RELEASED)
                            } else {
                                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun inspect(dnsHost: String): List<PolicyResult> {
        if (!deviceOwnerManager.isDeviceOwner()) {
            return PolicyId.entries.map { id ->
                PolicyResult(id, PolicyState.NOT_APPLIED)
            }
        }

        val supported = PolicyCatalog.supported(Build.VERSION.SDK_INT)
        val admin = deviceOwnerManager.adminComponent

        return PolicyId.entries.map { id ->
            if (!supported.contains(id)) {
                PolicyResult(id, PolicyState.UNSUPPORTED, getUnsupportedMessage(id))
            } else {
                try {
                    val isInForce = when (id) {
                        PolicyId.UNINSTALL_BLOCKED -> {
                            devicePolicyManager.isUninstallBlocked(admin, ownPackage)
                        }

                        PolicyId.FORCE_STOP_BLOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                devicePolicyManager.getUserControlDisabledPackages(admin).contains(ownPackage)
                            } else false
                        }

                        PolicyId.SAFE_BOOT_BLOCKED -> {
                            devicePolicyManager.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_SAFE_BOOT)
                        }

                        PolicyId.VPN_CONFIG_BLOCKED -> {
                            devicePolicyManager.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_CONFIG_VPN)
                        }

                        PolicyId.DATE_TIME_LOCKED -> {
                            devicePolicyManager.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_CONFIG_DATE_TIME)
                        }

                        PolicyId.PRIVATE_DNS_LOCKED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val currentMode = devicePolicyManager.getGlobalPrivateDnsMode(admin)
                                val currentHost = devicePolicyManager.getGlobalPrivateDnsHost(admin)
                                currentMode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME && currentHost == dnsHost
                            } else false
                        }
                    }

                    if (isInForce) {
                        PolicyResult(id, PolicyState.APPLIED)
                    } else {
                        PolicyResult(id, PolicyState.NOT_APPLIED)
                    }
                } catch (e: Exception) {
                    PolicyResult(id, PolicyState.FAILED, e.localizedMessage ?: "Inspection failed")
                }
            }
        }
    }

    private fun getUnsupportedMessage(id: PolicyId): String {
        return when (id) {
            PolicyId.FORCE_STOP_BLOCKED -> "Requires Android 11+ (API 30)"
            PolicyId.PRIVATE_DNS_LOCKED -> "Requires Android 10+ (API 29)"
            else -> "Not supported on this Android version"
        }
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
