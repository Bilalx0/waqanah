package com.waqanah.app.service.lock

import android.content.Context
import com.waqanah.app.BuildConfig
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.enforcement.ShieldServiceController
import com.waqanah.app.service.hardening.PolicyResult
import com.waqanah.app.service.hardening.PolicyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LockState(
    val active: Boolean = false,
    val lockedUntil: Long = 0L,
    val startedAt: Long = 0L,
    val hasPin: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0L,
    val blockedOffAttempts: Int = 0
)

enum class StartResult {
    SUCCESS,
    NOT_DEVICE_OWNER,
    PROTECTION_OFF,
    ALREADY_LOCKED,
    HARDENING_NOT_APPLIED,
    INVALID_DURATION,
    INVALID_PIN
}

sealed class ReleaseResult {
    object Released : ReleaseResult()
    data class WrongPin(val failures: Int, val lockoutUntil: Long) : ReleaseResult()
    data class LockedOut(val until: Long) : ReleaseResult()
    object NoPinSet : ReleaseResult()
    object NotLocked : ReleaseResult()
}

class CommitmentManager(
    private val context: Context,
    private val deviceOwnerManager: DeviceOwnerManager,
    private val preferences: WaqanahPreferences
) {

    private val tickerFlow: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(30_000L)
        }
    }

    val state: Flow<LockState> = combine(
        preferences.lockUntilFlow,
        preferences.lockStartedAtFlow,
        preferences.lockPinHashFlow,
        preferences.lockFailedAttemptsFlow,
        preferences.lockLockoutUntilFlow,
        preferences.lockLastSeenWallFlow,
        preferences.lockBlockedOffAttemptsFlow,
        tickerFlow
    ) { args: Array<Any?> ->
        val until = args[0] as Long
        val startedAt = args[1] as Long
        val pinHash = args[2] as String?
        val failed = args[3] as Int
        val lockout = args[4] as Long
        val lastSeen = args[5] as Long
        val blockedOff = args[6] as Int

        val now = LockPolicy.effectiveNow(System.currentTimeMillis(), lastSeen)
        val active = LockPolicy.isActive(until, now)

        LockState(
            active = active,
            lockedUntil = until,
            startedAt = startedAt,
            hasPin = !pinHash.isNullOrBlank(),
            failedAttempts = failed,
            lockoutUntil = lockout,
            blockedOffAttempts = blockedOff
        )
    }

    private val lockMutex = Mutex()

    private fun String?.isNullOrBlank(): Boolean = this == null || this.trim().isEmpty()

    suspend fun isActive(): Boolean {
        val until = preferences.lockUntilFlow.first()
        val lastSeen = preferences.lockLastSeenWallFlow.first()
        val now = System.currentTimeMillis()
        if (now - lastSeen >= 60_000L) {
            preferences.advanceLastSeen(now)
        }
        val effective = LockPolicy.effectiveNow(now, lastSeen)
        return LockPolicy.isActive(until, effective)
    }

    suspend fun start(
        durationMs: Long,
        pin: String?,
        protectionEnabled: Boolean,
        hardening: List<PolicyResult>
    ): StartResult = lockMutex.withLock {
        if (!deviceOwnerManager.isDeviceOwner()) return StartResult.NOT_DEVICE_OWNER
        if (!protectionEnabled) return StartResult.PROTECTION_OFF
        if (isActive()) return StartResult.ALREADY_LOCKED

        if (!HardeningGate.isReady(hardening)) return StartResult.HARDENING_NOT_APPLIED

        if (!LockPolicy.allowedDurationsMs(BuildConfig.DEBUG).contains(durationMs)) {
            return StartResult.INVALID_DURATION
        }

        val hasPin = pin != null
        if (hasPin && !LockPolicy.isValidPin(pin)) {
            return StartResult.INVALID_PIN
        }

        val lastSeen = preferences.lockLastSeenWallFlow.first()
        val now = LockPolicy.effectiveNow(System.currentTimeMillis(), lastSeen)
        val until = LockPolicy.lockedUntilFor(now, durationMs, BuildConfig.DEBUG)

        var pinHashB64: String? = null
        var pinSaltB64: String? = null

        if (hasPin) {
            withContext(Dispatchers.Default) {
                val salt = PinHasher.newSalt()
                val hash = PinHasher.hash(pin, salt)
                pinSaltB64 = PinHasher.toBase64(salt)
                pinHashB64 = PinHasher.toBase64(hash)
            }
        }

        preferences.startLock(until, now, pinHashB64, pinSaltB64)
        ShieldServiceController.sync(context)
        return StartResult.SUCCESS
    }

    suspend fun releaseWithPin(pin: String): ReleaseResult = lockMutex.withLock {
        if (!isActive()) return ReleaseResult.NotLocked

        val pinHashB64 = preferences.lockPinHashFlow.first()
        val pinSaltB64 = preferences.lockPinSaltFlow.first()
        val failedAttempts = preferences.lockFailedAttemptsFlow.first()
        val lockoutUntil = preferences.lockLockoutUntilFlow.first()

        if (pinHashB64 == null || pinSaltB64 == null) return ReleaseResult.NoPinSet

        val now = System.currentTimeMillis()
        val lastSeen = preferences.lockLastSeenWallFlow.first()
        preferences.advanceLastSeen(now)
        val effectiveNow = LockPolicy.effectiveNow(now, lastSeen)

        if (effectiveNow < lockoutUntil) {
            return ReleaseResult.LockedOut(lockoutUntil)
        }

        val verified = withContext(Dispatchers.Default) {
            val salt = PinHasher.fromBase64(pinSaltB64)
            val expectedHash = PinHasher.fromBase64(pinHashB64)
            PinHasher.verify(pin, salt, expectedHash)
        }

        return if (verified) {
            preferences.clearLock()
            ReleaseResult.Released
        } else {
            val newFailures = failedAttempts + 1
            val lockoutMs = LockPolicy.lockoutMsAfterFailures(newFailures)
            val newLockoutUntil = effectiveNow + lockoutMs
            preferences.recordFailedPin(newFailures, newLockoutUntil)
            ReleaseResult.WrongPin(newFailures, newLockoutUntil)
        }
    }

    suspend fun noteBlockedOffAttempt() {
        preferences.bumpBlockedOff()
    }
}
