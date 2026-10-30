package com.waqanah.app

import android.app.Application
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.enforcement.EnforcementManager
import com.waqanah.app.service.worker.WidgetSyncScheduler

class WaqanahApplication : Application() {

    lateinit var deviceOwnerManager: DeviceOwnerManager
        private set

    lateinit var enforcementManager: EnforcementManager
        private set

    override fun onCreate() {
        super.onCreate()
        deviceOwnerManager = DeviceOwnerManager(this)
        enforcementManager = EnforcementManager(this, deviceOwnerManager)

        // Schedule periodic widget sync
        WidgetSyncScheduler.schedulePeriodicWidgetSync(this)
    }
}
