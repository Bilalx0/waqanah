package com.waqanah.app.service.lock

import com.waqanah.app.service.hardening.PolicyId
import com.waqanah.app.service.hardening.PolicyResult
import com.waqanah.app.service.hardening.PolicyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningGateTest {

    @Test
    fun `empty list is rejected`() {
        assertFalse(HardeningGate.isReady(emptyList()))
    }

    @Test
    fun `mix of APPLIED UNCHANGED and UNSUPPORTED is ready`() {
        val list = listOf(
            PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.APPLIED),
            PolicyResult(PolicyId.FORCE_STOP_BLOCKED, PolicyState.UNCHANGED),
            PolicyResult(PolicyId.PRIVATE_DNS_LOCKED, PolicyState.UNSUPPORTED)
        )
        assertTrue(HardeningGate.isReady(list))
    }

    @Test
    fun `single FAILED is not ready`() {
        val list = listOf(
            PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.APPLIED),
            PolicyResult(PolicyId.SAFE_BOOT_BLOCKED, PolicyState.FAILED, "error")
        )
        assertFalse(HardeningGate.isReady(list))
    }

    @Test
    fun `single NOT_APPLIED is not ready`() {
        val list = listOf(
            PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.NOT_APPLIED)
        )
        assertFalse(HardeningGate.isReady(list))
    }

    @Test
    fun `single RELEASED is not ready`() {
        val list = listOf(
            PolicyResult(PolicyId.UNINSTALL_BLOCKED, PolicyState.RELEASED)
        )
        assertFalse(HardeningGate.isReady(list))
    }
}
