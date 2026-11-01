package com.waqanah.app.service.hardening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsHostValidatorTest {

    @Test
    fun `accepts valid CleanBrowsing DNS hosts`() {
        assertTrue(DnsHostValidator.isValid("family-filter-dns.cleanbrowsing.org"))
        assertTrue(DnsHostValidator.isValid("adult-filter-dns.cleanbrowsing.org"))
    }

    @Test
    fun `rejects invalid DNS host formats`() {
        assertFalse(DnsHostValidator.isValid("https://x.com"))
        assertFalse(DnsHostValidator.isValid("x.com:853"))
        assertFalse(DnsHostValidator.isValid("1.2.3.4"))
        assertFalse(DnsHostValidator.isValid("a b.com"))
        assertFalse(DnsHostValidator.isValid("-a.com"))
        assertFalse(DnsHostValidator.isValid("singlelabel"))
        assertFalse(DnsHostValidator.isValid(""))

        val longLabel = "a".repeat(254)
        assertFalse(DnsHostValidator.isValid(longLabel))
    }

    @Test
    fun `getEffectiveHost returns correct host based on provider`() {
        assertEquals("family-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.FAMILY, ""))
        assertEquals("adult-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.ADULT, ""))
        assertEquals("my-dns.example.org", DnsHostValidator.getEffectiveHost(DnsProvider.CUSTOM, " my-dns.example.org "))
        assertEquals("family-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.CUSTOM, "invalid_host"))
    }
}
