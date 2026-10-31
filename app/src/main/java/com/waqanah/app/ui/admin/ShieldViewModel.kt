package com.waqanah.app.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waqanah.app.WaqanahApplication
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.admin.DeviceOwnerStatus
import com.waqanah.app.service.enforcement.AppInfo
import com.waqanah.app.service.enforcement.EnforcementMode
import com.waqanah.app.service.enforcement.EnforcementPlan
import com.waqanah.app.service.enforcement.EnforcementReport
import com.waqanah.app.service.enforcement.ShieldServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShieldUiState(
    val deviceOwnerStatus: DeviceOwnerStatus = DeviceOwnerStatus.NotProvisioned,
    val enforcementEnabled: Boolean = false,
    val enforcementMode: EnforcementMode = EnforcementMode.HIDE_AND_SUSPEND,
    val allowedBrowserPackage: String? = null,
    val allowListPackages: Set<String> = emptySet(),
    val customBlocklist: Set<String> = emptySet(),
    val hideSystemBrowsers: Boolean = false,
    val hiddenByWaqanah: Map<String, String> = emptyMap(),
    val uninstalledByWaqanah: Set<String> = emptySet(),
    val lastEnforcementAt: Long = 0L
)

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WaqanahApplication
    val deviceOwnerManager: DeviceOwnerManager = app.deviceOwnerManager
    private val enforcementManager = app.enforcementManager
    private val preferences = WaqanahPreferences(application)

    val uiState: StateFlow<ShieldUiState> = combine(
        deviceOwnerManager.status,
        preferences.enforcementEnabledFlow,
        preferences.enforcementModeFlow,
        preferences.allowedBrowserPackageFlow,
        preferences.allowListPackagesFlow,
        preferences.customBlocklistFlow,
        preferences.hideSystemBrowsersFlow,
        preferences.hiddenByWaqanahFlow,
        preferences.uninstalledByWaqanahFlow,
        preferences.lastEnforcementAtFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        ShieldUiState(
            deviceOwnerStatus = args[0] as DeviceOwnerStatus,
            enforcementEnabled = args[1] as Boolean,
            enforcementMode = args[2] as EnforcementMode,
            allowedBrowserPackage = args[3] as String?,
            allowListPackages = args[4] as Set<String>,
            customBlocklist = args[5] as Set<String>,
            hideSystemBrowsers = args[6] as Boolean,
            hiddenByWaqanah = args[7] as Map<String, String>,
            uninstalledByWaqanah = args[8] as Set<String>,
            lastEnforcementAt = args[9] as Long
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ShieldUiState(deviceOwnerStatus = deviceOwnerManager.status.value)
    )

    private val _detectedBrowsers = MutableStateFlow<List<AppInfo>>(emptyList())
    val detectedBrowsers: StateFlow<List<AppInfo>> = _detectedBrowsers.asStateFlow()

    private val _installedNonSystemApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedNonSystemApps: StateFlow<List<AppInfo>> = _installedNonSystemApps.asStateFlow()

    private val _previewPlan = MutableStateFlow<EnforcementPlan?>(null)
    val previewPlan: StateFlow<EnforcementPlan?> = _previewPlan.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        loadScannedAppLists()
    }

    fun refreshStatus() {
        deviceOwnerManager.refresh()
        loadScannedAppLists()
        viewModelScope.launch(Dispatchers.Default) {
            ShieldServiceController.sync(app)
        }
    }

    private fun loadScannedAppLists() {
        viewModelScope.launch(Dispatchers.Default) {
            _detectedBrowsers.value = enforcementManager.getDetectedBrowsers()
            _installedNonSystemApps.value = enforcementManager.getInstalledNonSystemApps()
        }
    }

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnforcementEnabled(enabled)
            if (enabled) {
                val report = enforcementManager.enforceNow()
                emitApplySummary(report)
            } else {
                val report = enforcementManager.disableAndRelease()
                emitReleaseSummary(report)
            }
            ShieldServiceController.sync(app)
            loadScannedAppLists()
        }
    }

    fun setAllowedBrowserPackage(packageName: String?) {
        viewModelScope.launch {
            preferences.setAllowedBrowserPackage(packageName)
        }
    }

    fun setEnforcementMode(mode: EnforcementMode) {
        viewModelScope.launch {
            preferences.setEnforcementMode(mode)
        }
    }

    fun setHideSystemBrowsers(hide: Boolean) {
        viewModelScope.launch {
            preferences.setHideSystemBrowsers(hide)
        }
    }

    fun updateAllowList(packages: Set<String>) {
        viewModelScope.launch {
            preferences.updateAllowList(packages)
        }
    }

    fun updateCustomBlocklist(packages: Set<String>) {
        viewModelScope.launch {
            preferences.updateCustomBlocklist(packages)
        }
    }

    fun generatePreview() {
        viewModelScope.launch {
            _previewPlan.value = enforcementManager.preview()
        }
    }

    fun clearPreview() {
        _previewPlan.value = null
    }

    fun scanAndApplyNow() {
        viewModelScope.launch {
            val report = enforcementManager.enforceNow()
            emitApplySummary(report)
            ShieldServiceController.sync(app)
            loadScannedAppLists()
        }
    }

    private suspend fun emitApplySummary(report: EnforcementReport) {
        val msg = app.getString(
            com.waqanah.app.R.string.msg_apply_summary,
            report.applied.size,
            report.unchanged.size,
            report.failed.size
        )
        _messageEvent.emit(msg)
    }

    private suspend fun emitReleaseSummary(report: EnforcementReport) {
        val msg = app.getString(
            com.waqanah.app.R.string.msg_release_summary,
            report.applied.size,
            report.failed.size
        )
        _messageEvent.emit(msg)
    }
}
