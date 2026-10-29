package com.waqanah.app.service.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DeviceOwnerStatus {
    object NotProvisioned : DeviceOwnerStatus()
    object Active : DeviceOwnerStatus()
}

class DeviceOwnerManager(private val context: Context) {

    private val devicePolicyManager =
        context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adminComponent: ComponentName = ComponentName(
        context.applicationContext,
        WaqanahDeviceAdminReceiver::class.java
    )

    private val _status = MutableStateFlow<DeviceOwnerStatus>(getOwnerStatus())
    val status: StateFlow<DeviceOwnerStatus> = _status.asStateFlow()

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun refresh() {
        _status.value = getOwnerStatus()
    }

    @Suppress("DEPRECATION")
    fun clearDeviceOwnerApp() {
        if (isDeviceOwner()) {
            devicePolicyManager.clearDeviceOwnerApp(context.packageName)
            refresh()
        }
    }

    private fun getOwnerStatus(): DeviceOwnerStatus {
        return if (isDeviceOwner()) {
            DeviceOwnerStatus.Active
        } else {
            DeviceOwnerStatus.NotProvisioned
        }
    }
}
