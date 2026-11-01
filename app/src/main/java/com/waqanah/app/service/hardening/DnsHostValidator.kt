package com.waqanah.app.service.hardening

object DnsHostValidator {

    /**
     * Validates that host is a bare hostname:
     * - No scheme (http://, https://)
     * - No port (:853)
     * - No spaces or IP literals (1.2.3.4)
     * - Labels 1-63 chars of [a-zA-Z0-9-] not starting or ending with '-'
     * - At least two labels
     * - Total length <= 253
     */
    fun isValid(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || trimmed.length > 253) return false

        if (trimmed.contains("/") || trimmed.contains(":") || trimmed.contains(" ")) return false

        // Reject IP literals
        if (trimmed.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) return false

        val labels = trimmed.split(".")
        if (labels.size < 2) return false

        val labelRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$")
        for (label in labels) {
            if (label.isEmpty() || label.length > 63 || !labelRegex.matches(label)) {
                return false
            }
        }
        return true
    }

    fun getEffectiveHost(provider: DnsProvider, customHost: String): String {
        return when (provider) {
            DnsProvider.FAMILY, DnsProvider.ADULT -> provider.host
            DnsProvider.CUSTOM -> {
                val trimmed = customHost.trim()
                if (isValid(trimmed)) trimmed else DnsProvider.FAMILY.host
            }
        }
    }
}
