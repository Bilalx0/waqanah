package com.waqanah.app.service.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPolicyEngineTest {

    private val ownPackage = "com.waqanah.app"
    private val protectedPackages = setOf("android", "com.android.settings", "com.android.systemui")

    @Test
    fun `own package is never targeted`() {
        val installed = listOf(AppInfo(ownPackage, "Waqanah", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = setOf(ownPackage),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertEquals(1, plan.skipped.size)
        assertEquals(SkipReason.SELF, plan.skipped.first().why)
    }

    @Test
    fun `protected package is never targeted`() {
        val installed = listOf(
            AppInfo("com.android.settings", "Settings", true),
            AppInfo("com.android.providers.media", "Media Provider", true)
        )
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = setOf("com.android.settings"),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = setOf("com.android.settings"),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertEquals(2, plan.skipped.size)
        assertTrue(plan.skipped.all { it.why == SkipReason.PROTECTED })
    }

    @Test
    fun `allow list beats VPN rule`() {
        val vpnPkg = "com.expressvpn.vpn"
        val installed = listOf(AppInfo(vpnPkg, "ExpressVPN", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = setOf(vpnPkg),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = setOf(vpnPkg),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertEquals(1, plan.skipped.size)
        assertEquals(SkipReason.ALLOW_LISTED, plan.skipped.first().why)
    }

    @Test
    fun `system apps skipped by default`() {
        val systemApp = "com.system.app"
        val installed = listOf(AppInfo(systemApp, "System App", true))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = setOf(systemApp),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertEquals(1, plan.skipped.size)
        assertEquals(SkipReason.SYSTEM_APP, plan.skipped.first().why)
    }

    @Test
    fun `allowed browser is kept when allowedBrowser is selected`() {
        val chrome = "com.android.chrome"
        val firefox = "org.mozilla.firefox"
        val installed = listOf(
            AppInfo(chrome, "Chrome", false),
            AppInfo(firefox, "Firefox", false)
        )

        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = setOf(chrome, firefox),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = chrome,
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertEquals(1, plan.targets.size)
        assertEquals(firefox, plan.targets.first().packageName)
        assertEquals(EnforcementReason.EXTRA_BROWSER, plan.targets.first().reason)

        val chromeSkipped = plan.skipped.find { it.packageName == chrome }
        assertEquals(SkipReason.ALLOWED_BROWSER, chromeSkipped?.why)
    }

    @Test
    fun `no allowed browser selected skips browser rule entirely`() {
        val chrome = "com.android.chrome"
        val firefox = "org.mozilla.firefox"
        val installed = listOf(
            AppInfo(chrome, "Chrome", false),
            AppInfo(firefox, "Firefox", false)
        )

        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = setOf(chrome, firefox),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertEquals(2, plan.skipped.size)
        assertTrue(plan.skipped.all { it.why == SkipReason.BROWSER_RULE_INACTIVE })
    }

    @Test
    fun `hideSystemBrowsers targets system browser when enabled`() {
        val secBrowser = "com.sec.android.app.sbrowser"
        val installed = listOf(AppInfo(secBrowser, "Samsung Internet", true))

        // When hideSystemBrowsers = false
        val planOff = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = setOf(secBrowser),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = "com.android.chrome",
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )
        assertTrue(planOff.targets.isEmpty())

        // When hideSystemBrowsers = true
        val planOn = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = setOf(secBrowser),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = "com.android.chrome",
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = true
        )
        assertEquals(1, planOn.targets.size)
        assertEquals(secBrowser, planOn.targets.first().packageName)
    }

    @Test
    fun `blocklist package targets BLOCKLIST`() {
        val blockPkg = "com.UCMobile.intl"
        val installed = listOf(AppInfo(blockPkg, "UC Browser", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertEquals(1, plan.targets.size)
        assertEquals(EnforcementReason.BLOCKLIST, plan.targets.first().reason)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `custom blocklist package targets CUSTOM`() {
        val customPkg = "com.example.distractor"
        val installed = listOf(AppInfo(customPkg, "Distractor", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = setOf(customPkg),
            hideSystemBrowsers = false
        )

        assertEquals(1, plan.targets.size)
        assertEquals(EnforcementReason.CUSTOM, plan.targets.first().reason)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `VPN package targets VPN_APP`() {
        val vpnPkg = "com.custom.vpn"
        val installed = listOf(AppInfo(vpnPkg, "Custom VPN", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = setOf(vpnPkg),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertEquals(1, plan.targets.size)
        assertEquals(EnforcementReason.VPN_APP, plan.targets.first().reason)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun `unmatched app appears nowhere in targets or skipped`() {
        val benignApp = "com.example.calculator"
        val installed = listOf(AppInfo(benignApp, "Calculator", false))
        val plan = AppPolicyEngine.plan(
            installedApps = installed,
            vpnPackages = emptySet(),
            browserPackages = emptySet(),
            protectedPackages = protectedPackages,
            ownPackage = ownPackage,
            allowedBrowser = null,
            allowList = emptySet(),
            customBlocklist = emptySet(),
            hideSystemBrowsers = false
        )

        assertTrue(plan.targets.isEmpty())
        assertTrue(plan.skipped.isEmpty())
    }
}
