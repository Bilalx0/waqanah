package com.waqanah.app.service.enforcement

import android.content.Context
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.hardening.DnsHostValidator
import com.waqanah.app.service.hardening.HardeningManager
import com.waqanah.app.service.hardening.PolicyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EnforcementManager(
    private val context: Context,
    private val deviceOwnerManager: DeviceOwnerManager
) {

    private val preferences = WaqanahPreferences(context)
    private val scanner = AppScanner(context)
    private val enforcer = AppEnforcer(context, deviceOwnerManager)
    private val hardening = HardeningManager(context, deviceOwnerManager)

    private val enforcementLock = Mutex()

    private val _hardeningResults = MutableStateFlow<List<PolicyResult>>(emptyList())
    val hardeningResults: StateFlow<List<PolicyResult>> = _hardeningResults.asStateFlow()

    suspend fun preview(): EnforcementPlan = withContext(Dispatchers.Default) {
        val installedApps = scanner.installedApps()
        val vpnPackages = scanner.vpnPackages()
        val browserPackages = scanner.browserPackages()
        val protectedPackages = BuiltInLists.getRuntimeProtectedPackages(context)

        val allowedBrowser = preferences.allowedBrowserPackageFlow.first()
        val allowList = preferences.allowListPackagesFlow.first()
        val customBlocklist = preferences.customBlocklistFlow.first()
        val hideSystemBrowsers = preferences.hideSystemBrowsersFlow.first()

        AppPolicyEngine.plan(
            installedApps = installedApps,
            vpnPackages = vpnPackages,
            browserPackages = browserPackages,
            protectedPackages = protectedPackages,
            ownPackage = context.packageName,
            allowedBrowser = allowedBrowser,
            allowList = allowList,
            customBlocklist = customBlocklist,
            hideSystemBrowsers = hideSystemBrowsers
        )
    }

    suspend fun enforceNow(): EnforcementReport = withContext(Dispatchers.Default) {
        enforcementLock.withLock {
            if (!deviceOwnerManager.isDeviceOwner()) {
                return@withContext EnforcementReport(
                    applied = emptyList(),
                    unchanged = emptyList(),
                    failed = emptyMap(),
                    skipped = emptyList()
                )
            }

            val enabled = preferences.enforcementEnabledFlow.first()
            if (!enabled) {
                return@withContext EnforcementReport(
                    applied = emptyList(),
                    unchanged = emptyList(),
                    failed = emptyMap(),
                    skipped = emptyList()
                )
            }

            val mode = preferences.enforcementModeFlow.first()
            val plan = preview()
            val report = enforcer.apply(plan, mode)

            if (report.applied.isNotEmpty()) {
                val newlyHidden = report.applied.associate { it.packageName to it.reason.name }
                preferences.addHiddenPackages(newlyHidden)
            }

            val provider = preferences.dnsProviderFlow.first()
            val customHost = preferences.customDnsHostFlow.first()
            val effectiveHost = DnsHostValidator.getEffectiveHost(provider, customHost)

            val hardeningRes = hardening.applyAll(effectiveHost)
            _hardeningResults.value = hardeningRes

            preferences.setLastEnforcementAt(System.currentTimeMillis())
            report
        }
    }

    suspend fun disableAndRelease(): EnforcementReport = withContext(Dispatchers.Default) {
        enforcementLock.withLock {
            if (!deviceOwnerManager.isDeviceOwner()) {
                return@withContext EnforcementReport(
                    applied = emptyList(),
                    unchanged = emptyList(),
                    failed = emptyMap(),
                    skipped = emptyList()
                )
            }

            preferences.setEnforcementEnabled(false)

            val currentHidden = preferences.hiddenByWaqanahFlow.first()
            val report = enforcer.releaseAll(currentHidden)

            if (report.applied.isNotEmpty()) {
                val releasedPackages = report.applied.map { it.packageName }.toSet()
                preferences.removeHiddenPackages(releasedPackages)
            }

            val hardeningRes = hardening.releaseAll()
            _hardeningResults.value = hardeningRes

            report
        }
    }

    suspend fun getDetectedBrowsers(): List<AppInfo> = withContext(Dispatchers.Default) {
        val browserPkgs = scanner.browserPackages() + BuiltInLists.systemBrowsers
        scanner.installedApps().filter { browserPkgs.contains(it.packageName) }
    }

    suspend fun getInstalledNonSystemApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val ownPkg = context.packageName
        scanner.installedApps().filter { !it.isSystem && it.packageName != ownPkg }
    }
}
