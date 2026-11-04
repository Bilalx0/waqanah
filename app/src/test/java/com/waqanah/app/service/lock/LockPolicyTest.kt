package com.waqanah.app.service.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    @Test
    fun `effectiveNow never decreases`() {
        assertEquals(200L, LockPolicy.effectiveNow(100L, 200L))
        assertEquals(200L, LockPolicy.effectiveNow(200L, 100L))
        assertEquals(150L, LockPolicy.effectiveNow(150L, 150L))
    }

    @Test
    fun `isActive boundary conditions`() {
        // now == lockedUntil is NOT active
        assertFalse(LockPolicy.isActive(1000L, 1000L))
        // now < lockedUntil is active
        assertTrue(LockPolicy.isActive(1000L, 999L))
        // now > lockedUntil is NOT active
        assertFalse(LockPolicy.isActive(1000L, 1001L))
        // lockedUntil = 0 is NOT active
        assertFalse(LockPolicy.isActive(0L, 100L))
    }

    @Test
    fun `lockedUntilFor accepts allowed and rejects unallowed`() {
        val now = 1000L
        val allowedMs = LockPolicy.allowedDurationsMs(debug = false).first()
        assertEquals(now + allowedMs, LockPolicy.lockedUntilFor(now, allowedMs, debug = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lockedUntilFor rejects invalid duration`() {
        LockPolicy.lockedUntilFor(1000L, 12345L, debug = false)
    }

    @Test
    fun `allowedDurationsMs contains no minute-based option in release mode`() {
        val releaseDurations = LockPolicy.allowedDurationsMs(debug = false)
        assertTrue(releaseDurations.none { it < 86_400_000L })

        val debugDurations = LockPolicy.allowedDurationsMs(debug = true)
        assertTrue(debugDurations.any { it < 86_400_000L })
    }

    @Test
    fun `lockoutMsAfterFailures calculation`() {
        assertEquals(0L, LockPolicy.lockoutMsAfterFailures(4))
        assertEquals(15 * 60_000L, LockPolicy.lockoutMsAfterFailures(5))
        assertEquals(30 * 60_000L, LockPolicy.lockoutMsAfterFailures(6))
        assertEquals(60 * 60_000L, LockPolicy.lockoutMsAfterFailures(7))
        assertEquals(86_400_000L, LockPolicy.lockoutMsAfterFailures(12)) // capped at 24h
        assertEquals(86_400_000L, LockPolicy.lockoutMsAfterFailures(20)) // capped at 24h
    }

    @Test
    fun `isValidPin validation cases`() {
        assertFalse(LockPolicy.isValidPin("12345"))  // too short (5 chars)
        assertFalse(LockPolicy.isValidPin("111111")) // only 1 distinct char
        assertFalse(LockPolicy.isValidPin(" abcdef"))// leading space
        assertFalse(LockPolicy.isValidPin("abcdef "))// trailing space
        assertTrue(LockPolicy.isValidPin("abc123"))  // valid 6 chars, 6 distinct chars
        assertTrue(LockPolicy.isValidPin("121212"))  // valid 6 chars, 2 distinct chars
        assertFalse(LockPolicy.isValidPin("a".repeat(65))) // 65 chars (too long)
    }
}
