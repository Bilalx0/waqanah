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
import com.waqanah.app.service.hardening.DnsProvider
import com.waqanah.app.service.hardening.PolicyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val lastEnforcementAt: Long = 0L,
    val dnsProvider: DnsProvider = DnsProvider.FAMILY,
    val customDnsHost: String = ""
)

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WaqanahApplication
    val deviceOwnerManager: DeviceOwnerManager = app.deviceOwnerManager
    private val enforcementManager = app.enforcementManager
    private val preferences = WaqanahPreferences(application)

    val hardeningResults: StateFlow<List<PolicyResult>> = enforcementManager.hardeningResults

    val lockState: StateFlow<com.waqanah.app.service.lock.LockState> = app.commitmentManager.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = com.waqanah.app.service.lock.LockState()
    )

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
        preferences.lastEnforcementAtFlow,
        preferences.dnsProviderFlow,
        preferences.customDnsHostFlow
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
            lastEnforcementAt = args[9] as Long,
            dnsProvider = args[10] as DnsProvider,
            customDnsHost = args[11] as String
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
        refreshHardeningStatus()
    }

    fun refreshStatus() {
        deviceOwnerManager.refresh()
        loadScannedAppLists()
        refreshHardeningStatus()
        viewModelScope.launch(Dispatchers.Default) {
            ShieldServiceController.sync(app)
        }
    }

    fun refreshHardeningStatus() {
        viewModelScope.launch {
            enforcementManager.refreshHardeningStatus()
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
            if (!enabled && app.commitmentManager.isActive()) {
                app.commitmentManager.noteBlockedOffAttempt()
                val untilFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lockState.value.lockedUntil))
                _messageEvent.emit(app.getString(com.waqanah.app.R.string.msg_lock_prevents_off, untilFormatted))
                return@launch
            }
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

    private suspend fun checkLockAndRefuse(): Boolean {
        if (app.commitmentManager.isActive()) {
            _messageEvent.emit(app.getString(com.waqanah.app.R.string.msg_config_frozen_by_lock))
            return true
        }
        return false
    }

    fun setAllowedBrowserPackage(packageName: String?) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.setAllowedBrowserPackage(packageName)
        }
    }

    fun setEnforcementMode(mode: EnforcementMode) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.setEnforcementMode(mode)
        }
    }

    fun setHideSystemBrowsers(hide: Boolean) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.setHideSystemBrowsers(hide)
        }
    }

    fun setDnsProvider(provider: DnsProvider) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.setDnsProvider(provider)
            if (uiState.value.enforcementEnabled) {
                val report = enforcementManager.enforceNow()
                emitApplySummary(report)
            }
        }
    }

    fun setCustomDnsHost(host: String) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.setCustomDnsHost(host)
            if (uiState.value.enforcementEnabled) {
                val report = enforcementManager.enforceNow()
                emitApplySummary(report)
            }
        }
    }

    fun updateAllowList(packages: Set<String>) {
        viewModelScope.launch {
            if (checkLockAndRefuse()) return@launch
            preferences.updateAllowList(packages)
        }
    }

    fun updateCustomBlocklist(packages: Set<String>) {
        viewModelScope.launch {
            if (app.commitmentManager.isActive()) {
                val current = preferences.customBlocklistFlow.first()
                if (!packages.containsAll(current)) {
                    _messageEvent.emit(app.getString(com.waqanah.app.R.string.msg_cannot_remove_blocklist_during_lock))
                    return@launch
                }
            }
            preferences.updateCustomBlocklist(packages)
        }
    }

    fun startCommitmentLock(durationMs: Long, pin: String?) {
        viewModelScope.launch {
            val enabled = preferences.enforcementEnabledFlow.first()
            val hardening = enforcementManager.refreshHardeningStatus()
            val res = app.commitmentManager.start(durationMs, pin, enabled, hardening)
            val msg = when (res) {
                com.waqanah.app.service.lock.StartResult.SUCCESS -> app.getString(com.waqanah.app.R.string.msg_lock_started)
                com.waqanah.app.service.lock.StartResult.NOT_DEVICE_OWNER -> app.getString(com.waqanah.app.R.string.msg_lock_err_owner)
                com.waqanah.app.service.lock.StartResult.PROTECTION_OFF -> app.getString(com.waqanah.app.R.string.msg_lock_err_protection)
                com.waqanah.app.service.lock.StartResult.ALREADY_LOCKED -> app.getString(com.waqanah.app.R.string.msg_lock_err_already)
                com.waqanah.app.service.lock.StartResult.HARDENING_NOT_APPLIED -> app.getString(com.waqanah.app.R.string.msg_lock_err_hardening)
                com.waqanah.app.service.lock.StartResult.INVALID_DURATION -> app.getString(com.waqanah.app.R.string.msg_lock_err_duration)
                com.waqanah.app.service.lock.StartResult.INVALID_PIN -> app.getString(com.waqanah.app.R.string.msg_lock_err_pin)
            }
            _messageEvent.emit(msg)
        }
    }

    fun releaseCommitmentLock(pin: String) {
        viewModelScope.launch {
            val res = app.commitmentManager.releaseWithPin(pin)
            val msg = when (res) {
                is com.waqanah.app.service.lock.ReleaseResult.Released -> app.getString(com.waqanah.app.R.string.msg_lock_released)
                is com.waqanah.app.service.lock.ReleaseResult.WrongPin -> app.getString(com.waqanah.app.R.string.msg_lock_wrong_pin, res.failures)
                is com.waqanah.app.service.lock.ReleaseResult.LockedOut -> {
                    val untilStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(res.until))
                    app.getString(com.waqanah.app.R.string.msg_lock_locked_out, untilStr)
                }
                com.waqanah.app.service.lock.ReleaseResult.NoPinSet -> app.getString(com.waqanah.app.R.string.msg_lock_no_pin)
                com.waqanah.app.service.lock.ReleaseResult.NotLocked -> app.getString(com.waqanah.app.R.string.msg_lock_not_locked)
            }
            _messageEvent.emit(msg)
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
