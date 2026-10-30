package com.waqanah.app.service.enforcement

object AppPolicyEngine {

    /**
     * Pure planning function. No Android runtime calls inside so it can be unit-tested directly.
     * Evaluates installed apps against policy rules in strict priority order.
     */
    fun plan(
        installedApps: List<AppInfo>,
        vpnPackages: Set<String>,
        browserPackages: Set<String>,
        protectedPackages: Set<String>,
        ownPackage: String,
        allowedBrowser: String?,
        allowList: Set<String>,
        customBlocklist: Set<String>,
        hideSystemBrowsers: Boolean
    ): EnforcementPlan {
        val targets = mutableListOf<EnforcementTarget>()
        val skipped = mutableListOf<SkippedApp>()

        for (app in installedApps) {
            val pkg = app.packageName

            // Rule 1: Never target own package, protected packages, or allow-listed packages
            if (pkg == ownPackage) {
                skipped.add(SkippedApp(pkg, app.label, SkipReason.SELF))
                continue
            }

            if (protectedPackages.contains(pkg) || isPackageProtectedByPrefix(pkg)) {
                skipped.add(SkippedApp(pkg, app.label, SkipReason.PROTECTED))
                continue
            }

            if (allowList.contains(pkg)) {
                skipped.add(SkippedApp(pkg, app.label, SkipReason.ALLOW_LISTED))
                continue
            }

            // Rule 2: System apps are skipped, EXCEPT if hideSystemBrowsers is true and pkg is in BuiltInLists.systemBrowsers
            if (app.isSystem) {
                val isTargetedSystemBrowser = hideSystemBrowsers && BuiltInLists.systemBrowsers.contains(pkg)
                if (!isTargetedSystemBrowser) {
                    skipped.add(SkippedApp(pkg, app.label, SkipReason.SYSTEM_APP))
                    continue
                }
            }

            // Rule 3: Built-in Blocklist
            if (BuiltInLists.blocklist.contains(pkg)) {
                targets.add(EnforcementTarget(pkg, app.label, EnforcementReason.BLOCKLIST))
                continue
            }

            // Rule 4: Custom Blocklist
            if (customBlocklist.contains(pkg)) {
                targets.add(EnforcementTarget(pkg, app.label, EnforcementReason.CUSTOM))
                continue
            }

            // Rule 5: VPN App
            if (vpnPackages.contains(pkg)) {
                targets.add(EnforcementTarget(pkg, app.label, EnforcementReason.VPN_APP))
                continue
            }

            // Rule 6: Browser Packages
            if (browserPackages.contains(pkg) || BuiltInLists.systemBrowsers.contains(pkg)) {
                if (allowedBrowser != null) {
                    if (pkg == allowedBrowser) {
                        skipped.add(SkippedApp(pkg, app.label, SkipReason.ALLOWED_BROWSER))
                    } else {
                        targets.add(EnforcementTarget(pkg, app.label, EnforcementReason.EXTRA_BROWSER))
                    }
                } else {
                    // No allowed browser chosen -> skip with BROWSER_RULE_INACTIVE
                    skipped.add(SkippedApp(pkg, app.label, SkipReason.BROWSER_RULE_INACTIVE))
                }
                continue
            }

            // Unmatched app: Matches no rule -> NOT added to targets, NOT added to skipped.
        }

        return EnforcementPlan(targets = targets, skipped = skipped)
    }

    private fun isPackageProtectedByPrefix(packageName: String): Boolean {
        return packageName.startsWith("com.android.providers.")
    }
}
