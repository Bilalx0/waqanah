package com.waqanah.app

import android.app.Application
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.enforcement.EnforcementManager
import com.waqanah.app.service.enforcement.ShieldServiceController
import com.waqanah.app.service.worker.WidgetSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WaqanahApplication : Application() {

    lateinit var deviceOwnerManager: DeviceOwnerManager
        private set

    lateinit var enforcementManager: EnforcementManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        deviceOwnerManager = DeviceOwnerManager(this)
        enforcementManager = EnforcementManager(this, deviceOwnerManager)

        applicationScope.launch {
            ShieldServiceController.sync(this@WaqanahApplication)
        }

        // Schedule periodic widget sync
        WidgetSyncScheduler.schedulePeriodicWidgetSync(this)
    }
}
