package com.waqanah.app.service.lock

import com.waqanah.app.service.hardening.PolicyResult
import com.waqanah.app.service.hardening.PolicyState

object HardeningGate {

    fun isReady(results: List<PolicyResult>): Boolean {
        if (results.isEmpty()) return false
        return results.all { result ->
            result.state == PolicyState.UNSUPPORTED ||
            result.state == PolicyState.APPLIED ||
            result.state == PolicyState.UNCHANGED
        }
    }
}
