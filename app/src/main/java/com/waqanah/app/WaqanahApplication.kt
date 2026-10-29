package com.waqanah.app

import android.app.Application
import com.waqanah.app.service.worker.WidgetSyncScheduler

class WaqanahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule 6-hour periodic widget sync
        WidgetSyncScheduler.schedulePeriodicWidgetSync(this)
    }
}
