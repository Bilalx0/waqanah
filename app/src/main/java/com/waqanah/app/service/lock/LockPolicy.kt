package com.waqanah.app.service.lock

import kotlin.math.max

object LockPolicy {

    private const val MS_MINUTE = 60_000L
    private const val MS_DAY = 86_400_000L

    fun allowedDurationsMs(debug: Boolean): List<Long> {
        val list = mutableListOf<Long>()
        if (debug) {
            list.add(2 * MS_MINUTE)  // 2 minutes
            list.add(10 * MS_MINUTE) // 10 minutes
        }
        list.add(1 * MS_DAY)  // 1 day
        list.add(3 * MS_DAY)  // 3 days
        list.add(7 * MS_DAY)  // 7 days
        list.add(14 * MS_DAY) // 14 days
        list.add(30 * MS_DAY) // 30 days
        return list
    }

    fun lockedUntilFor(now: Long, durationMs: Long, debug: Boolean): Long {
        if (!allowedDurationsMs(debug).contains(durationMs)) {
            throw IllegalArgumentException("Duration $durationMs ms is not allowed")
        }
        return now + durationMs
    }

    fun effectiveNow(wallNow: Long, lastSeen: Long): Long {
        return max(wallNow, lastSeen)
    }

    fun isActive(lockedUntil: Long, now: Long): Boolean {
        return lockedUntil > 0L && now < lockedUntil
    }

    fun remainingMs(lockedUntil: Long, now: Long): Long {
        return max(0L, lockedUntil - now)
    }

    fun lockoutMsAfterFailures(failures: Int): Long {
        if (failures < 5) return 0L
        val maxLockoutMs = MS_DAY // 24 hours
        val minutes = when (failures) {
            5 -> 15L
            6 -> 30L
            7 -> 60L
            8 -> 120L
            9 -> 240L
            10 -> 480L
            11 -> 960L
            else -> 1440L
        }
        val computedMs = minutes * MS_MINUTE
        return minOf(computedMs, maxLockoutMs)
    }

    fun isValidPin(pin: String): Boolean {
        if (pin.length < 6 || pin.length > 64) return false
        if (pin != pin.trim()) return false
        if (pin.toSet().size < 2) return false
        return true
    }
}
