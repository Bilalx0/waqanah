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
    fun `rejects invalid DNS host formats and respects max total length of 253`() {
        assertFalse(DnsHostValidator.isValid("https://x.com"))
        assertFalse(DnsHostValidator.isValid("x.com:853"))
        assertFalse(DnsHostValidator.isValid("1.2.3.4"))
        assertFalse(DnsHostValidator.isValid("a b.com"))
        assertFalse(DnsHostValidator.isValid("-a.com"))
        assertFalse(DnsHostValidator.isValid("singlelabel"))
        assertFalse(DnsHostValidator.isValid(""))

        // Labels <= 63 chars; total length 253 is accepted, total length 254 is rejected
        val host253 = "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(61)
        assertEquals(253, host253.length)
        assertTrue(DnsHostValidator.isValid(host253))

        val host254 = "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(63) + "." + "a".repeat(62)
        assertEquals(254, host254.length)
        assertFalse(DnsHostValidator.isValid(host254))
    }

    @Test
    fun `normalizes uppercase hostnames to lowercase`() {
        assertTrue(DnsHostValidator.isValid("Example.COM"))
        assertEquals("example.com", DnsHostValidator.getEffectiveHost(DnsProvider.CUSTOM, "Example.COM"))
    }

    @Test
    fun `getEffectiveHost returns correct host based on provider`() {
        assertEquals("family-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.FAMILY, ""))
        assertEquals("adult-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.ADULT, ""))
        assertEquals("my-dns.example.org", DnsHostValidator.getEffectiveHost(DnsProvider.CUSTOM, " my-dns.example.org "))
        assertEquals("family-filter-dns.cleanbrowsing.org", DnsHostValidator.getEffectiveHost(DnsProvider.CUSTOM, "invalid_host"))
    }
}
