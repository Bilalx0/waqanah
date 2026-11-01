package com.waqanah.app.service.hardening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyCatalogTest {

    @Test
    fun `supported policies on API 26-28`() {
        val supported26 = PolicyCatalog.supported(26)
        assertTrue(supported26.contains(PolicyId.UNINSTALL_BLOCKED))
        assertTrue(supported26.contains(PolicyId.SAFE_BOOT_BLOCKED))
        assertTrue(supported26.contains(PolicyId.VPN_CONFIG_BLOCKED))
        assertTrue(supported26.contains(PolicyId.DATE_TIME_LOCKED))
        assertFalse(supported26.contains(PolicyId.PRIVATE_DNS_LOCKED))
        assertFalse(supported26.contains(PolicyId.FORCE_STOP_BLOCKED))
        assertEquals(4, supported26.size)
    }

    @Test
    fun `supported policies on API 29`() {
        val supported29 = PolicyCatalog.supported(29)
        assertTrue(supported29.contains(PolicyId.PRIVATE_DNS_LOCKED))
        assertFalse(supported29.contains(PolicyId.FORCE_STOP_BLOCKED))
        assertEquals(5, supported29.size)
    }

    @Test
    fun `supported policies on API 30 plus`() {
        val supported30 = PolicyCatalog.supported(30)
        assertTrue(supported30.contains(PolicyId.FORCE_STOP_BLOCKED))
        assertTrue(supported30.contains(PolicyId.PRIVATE_DNS_LOCKED))
        assertEquals(6, supported30.size)
    }
}
