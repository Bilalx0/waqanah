package com.waqanah.app.service.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.waqanah.app.MainActivity
import com.waqanah.app.R
import com.waqanah.app.WaqanahApplication
import com.waqanah.app.data.local.WaqanahPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground Service that provides real-time app installation detection and enforcement.
 *
 * KNOWN LIMITATIONS:
 * 1. Force Stopping Waqanah via System Settings kills this service until the next device reboot
 *    or until the app is launched again by the user (Step 4 will add un-killable protections).
 * 2. There is a 1-3 second detection window after a package is installed before it is hidden/suspended.
 */
class ShieldService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val packageEventsFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var packageReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    private var isInitialized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @OptIn(FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Synchronous startForeground FIRST (No coroutine before it)
        startForegroundNotification()
        _running.value = true

        val app = application as WaqanahApplication
        val preferences = WaqanahPreferences(this)

        // 2. Validate state in coroutine
        serviceScope.launch {
            val isOwner = app.deviceOwnerManager.isDeviceOwner()
            val isEnabled = preferences.enforcementEnabledFlow.first()

            if (!isOwner || !isEnabled) {
                Log.w(TAG, "ShieldService started but Device Owner or Protection is inactive. Stopping.")
                stopSelf()
                return@launch
            }

            if (isInitialized) {
                // If onStartCommand runs again while already initialized, only refresh notification
                val hiddenMap = preferences.hiddenByWaqanahFlow.first()
                val lastScanTime = preferences.lastEnforcementAtFlow.first()
                updateNotification(hiddenMap.size, lastScanTime)
                return@launch
            }

            isInitialized = true

            // Dynamically register package installation receiver
            registerPackageReceiverIfNeeded()

            // Notification collector using combine(hiddenByWaqanahFlow, lastEnforcementAtFlow)
            launch {
                combine(
                    preferences.hiddenByWaqanahFlow,
                    preferences.lastEnforcementAtFlow
                ) { hiddenMap, lastScanTime ->
                    hiddenMap.size to lastScanTime
                }.collect { (hiddenCount, lastScanTime) ->
                    updateNotification(hiddenCount, lastScanTime)
                }
            }

            // Debounced event collector for package events
            launch {
                packageEventsFlow.debounce(750L).collect {
                    app.enforcementManager.enforceNow()
                }
            }

            // Catch-up scan for apps installed while service was stopped
            app.enforcementManager.enforceNow()
        }

        return START_STICKY
    }

    private fun registerPackageReceiverIfNeeded() {
        if (isReceiverRegistered) return
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkgName = intent.data?.schemeSpecificPart
                if (pkgName == context.packageName) return // Ignore own package events

                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                        Log.d(TAG, "Package event detected ($pkgName). Triggering debounced scan.")
                        packageEventsFlow.tryEmit(Unit)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        try {
            ContextCompat.registerReceiver(
                this,
                packageReceiver!!,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
            Log.d(TAG, "Dynamic package event receiver registered with RECEIVER_NOT_EXPORTED.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register package event receiver", e)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification(0, 0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(hiddenCount: Int, lastScanTime: Long) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(hiddenCount, lastScanTime))
    }

    private fun buildNotification(hiddenCount: Int, lastScanTime: Long): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val timeFormatted = if (lastScanTime > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastScanTime))
        } else {
            getString(R.string.time_never)
        }

        val titleText = getString(R.string.notification_shield_title)
        val contentText = getString(R.string.notification_shield_text, hiddenCount, timeFormatted)

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered && packageReceiver != null) {
            try {
                unregisterReceiver(packageReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering package receiver", e)
            }
            isReceiverRegistered = false
        }
        isInitialized = false
        serviceScope.cancel()
        _running.value = false
        Log.d(TAG, "ShieldService destroyed.")
    }

    companion object {
        private const val TAG = "ShieldService"
        private const val CHANNEL_ID = "shield_status"
        private const val NOTIFICATION_ID = 1001

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
