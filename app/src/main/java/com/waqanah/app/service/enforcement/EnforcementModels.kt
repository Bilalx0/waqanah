package com.waqanah.app.service.enforcement

enum class EnforcementReason {
    BLOCKLIST,
    VPN_APP,
    EXTRA_BROWSER,
    CUSTOM
}

data class EnforcementTarget(
    val packageName: String,
    val label: String,
    val reason: EnforcementReason
)

enum class EnforcementMode {
    HIDE_AND_SUSPEND,
    UNINSTALL
}

enum class SkipReason {
    SELF,
    SYSTEM_APP,
    PROTECTED,
    ALLOW_LISTED,
    ALLOWED_BROWSER,
    BROWSER_RULE_INACTIVE
}

data class SkippedApp(
    val packageName: String,
    val label: String,
    val why: SkipReason
)

data class EnforcementPlan(
    val targets: List<EnforcementTarget>,
    val skipped: List<SkippedApp>
)

data class EnforcementReport(
    val applied: List<EnforcementTarget>,
    val unchanged: List<EnforcementTarget> = emptyList(),
    val failed: Map<String, String>,
    val skipped: List<SkippedApp>
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)
