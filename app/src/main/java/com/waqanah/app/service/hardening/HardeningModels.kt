package com.waqanah.app.service.hardening

enum class PolicyId {
    UNINSTALL_BLOCKED,
    FORCE_STOP_BLOCKED,
    SAFE_BOOT_BLOCKED,
    VPN_CONFIG_BLOCKED,
    DATE_TIME_LOCKED,
    PRIVATE_DNS_LOCKED
}

enum class PolicyState {
    APPLIED,
    UNCHANGED,
    FAILED,
    UNSUPPORTED,
    RELEASED
}

data class PolicyResult(
    val id: PolicyId,
    val state: PolicyState,
    val message: String? = null
)

object PolicyCatalog {
    fun supported(sdkInt: Int): Set<PolicyId> {
        val set = mutableSetOf(
            PolicyId.UNINSTALL_BLOCKED,
            PolicyId.SAFE_BOOT_BLOCKED,
            PolicyId.VPN_CONFIG_BLOCKED,
            PolicyId.DATE_TIME_LOCKED
        )
        if (sdkInt >= 29) {
            set.add(PolicyId.PRIVATE_DNS_LOCKED)
        }
        if (sdkInt >= 30) {
            set.add(PolicyId.FORCE_STOP_BLOCKED)
        }
        return set
    }
}
