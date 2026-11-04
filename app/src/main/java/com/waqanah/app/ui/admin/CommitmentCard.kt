package com.waqanah.app.ui.admin

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.waqanah.app.BuildConfig
import com.waqanah.app.R
import com.waqanah.app.service.admin.DeviceOwnerStatus
import com.waqanah.app.service.hardening.PolicyResult
import com.waqanah.app.service.hardening.PolicyState
import com.waqanah.app.service.lock.LockPolicy
import com.waqanah.app.service.lock.LockState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentCard(
    lockState: LockState,
    uiState: ShieldUiState,
    hardeningResults: List<PolicyResult>,
    onStartLock: (durationMs: Long, pin: String?) -> Unit,
    onReleaseWithPin: (pin: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isOwner = uiState.deviceOwnerStatus is DeviceOwnerStatus.Active
    val isProtectionOn = uiState.enforcementEnabled
    val isHardeningApplied = hardeningResults.isNotEmpty() && hardeningResults.none {
        it.state != PolicyState.UNSUPPORTED && it.state != PolicyState.APPLIED && it.state != PolicyState.UNCHANGED
    }
    val isReadyToLock = isOwner && isProtectionOn && isHardeningApplied

    var selectedDurationMs by remember { mutableLongStateOf(LockPolicy.allowedDurationsMs(BuildConfig.DEBUG).first()) }
    var useTrustedPin by remember { mutableStateOf(false) }
    var trustedPinState by remember { mutableStateOf<String?>(null) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showReleaseDialog by remember { mutableStateOf(false) }

    // FLAG_SECURE guard during PIN dialogs
    val isPinDialogOpen = showPinDialog || showReleaseDialog
    DisposableEffect(isPinDialogOpen) {
        if (isPinDialogOpen && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (activity != null) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.card_lock_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!lockState.active) {
                // Readiness Checklist
                Text(text = stringResource(R.string.lock_ready_check_owner), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (isProtectionOn) stringResource(R.string.lock_ready_check_protection) else "• Protection: Off ❌",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (isHardeningApplied) stringResource(R.string.lock_ready_check_hardening) else "• System Hardening: Incomplete ❌",
                    style = MaterialTheme.typography.bodySmall
                )

                if (!isReadyToLock) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.lock_not_ready_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Duration selection
                Text(text = stringResource(R.string.label_select_duration), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LockPolicy.allowedDurationsMs(BuildConfig.DEBUG).forEach { durMs ->
                        val label = when (durMs) {
                            120_000L -> stringResource(R.string.dur_2_min)
                            600_000L -> stringResource(R.string.dur_10_min)
                            86_400_000L -> stringResource(R.string.dur_1_day)
                            3 * 86_400_000L -> stringResource(R.string.dur_3_days)
                            7 * 86_400_000L -> stringResource(R.string.dur_7_days)
                            14 * 86_400_000L -> stringResource(R.string.dur_14_days)
                            30 * 86_400_000L -> stringResource(R.string.dur_30_days)
                            else -> "${durMs / 86_400_000L} Days"
                        }
                        FilterChip(
                            selected = selectedDurationMs == durMs,
                            onClick = { selectedDurationMs = durMs },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trusted PIN Switch & Set PIN Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_trusted_pin_switch),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useTrustedPin,
                        onCheckedChange = {
                            useTrustedPin = it
                            if (!it) trustedPinState = null
                        }
                    )
                }

                if (useTrustedPin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (trustedPinState != null) stringResource(R.string.btn_pin_set) else stringResource(R.string.btn_set_pin))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val startEnabled = isReadyToLock && (!useTrustedPin || trustedPinState != null)
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = startEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_start_lock))
                }
            } else {
                // Active Lock UI
                val untilDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lockState.lockedUntil))
                Text(
                    text = stringResource(R.string.label_locked_locked_until, untilDate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                var remainingText by remember { mutableStateOf("") }
                LaunchedEffect(lockState.lockedUntil) {
                    while (true) {
                        val remMs = LockPolicy.remainingMs(lockState.lockedUntil, System.currentTimeMillis())
                        val hours = remMs / (1000 * 60 * 60)
                        val mins = (remMs / (1000 * 60)) % 60
                        val secs = (remMs / 1000) % 60
                        remainingText = String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, mins, secs)
                        delay(30_000L)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.label_remaining_time, remainingText),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_blocked_off_attempts, lockState.blockedOffAttempts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lockState.hasPin) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showReleaseDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_release_with_pin))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.frozen_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Set PIN Dialog
    if (showPinDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var pinErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                p1 = ""; p2 = ""
            },
            title = { Text(stringResource(R.string.dialog_pin_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_pin_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = p1,
                        onValueChange = { p1 = it; pinErr = null },
                        label = { Text(stringResource(R.string.hint_enter_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = p2,
                        onValueChange = { p2 = it; pinErr = null },
                        label = { Text(stringResource(R.string.hint_confirm_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinErr != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(pinErr!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (p1 != p2) {
                            pinErr = context.getString(R.string.error_pin_mismatch)
                        } else if (!LockPolicy.isValidPin(p1)) {
                            pinErr = context.getString(R.string.error_invalid_pin)
                        } else {
                            trustedPinState = p1
                            showPinDialog = false
                            p1 = ""; p2 = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    p1 = ""; p2 = ""
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Confirm Start Lock Dialog
    if (showConfirmDialog) {
        var lockInput by remember { mutableStateOf("") }
        val now = System.currentTimeMillis()
        val endDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now + selectedDurationMs))

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false; lockInput = "" },
            title = { Text(stringResource(R.string.dialog_confirm_lock_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_confirm_lock_desc, endDateStr), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = lockInput,
                        onValueChange = { lockInput = it },
                        label = { Text(stringResource(R.string.hint_type_lock)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (lockInput.trim().equals("LOCK", ignoreCase = true)) {
                            onStartLock(selectedDurationMs, trustedPinState)
                            showConfirmDialog = false
                            lockInput = ""
                        }
                    },
                    enabled = lockInput.trim().equals("LOCK", ignoreCase = true)
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false; lockInput = "" }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Release Lock Dialog
    if (showReleaseDialog) {
        var relPin by remember { mutableStateOf("") }
        val isLockedOut = System.currentTimeMillis() < lockState.lockoutUntil

        AlertDialog(
            onDismissRequest = { showReleaseDialog = false; relPin = "" },
            title = { Text(stringResource(R.string.dialog_release_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_release_desc), style = MaterialTheme.typography.bodySmall)
                    if (lockState.failedAttempts > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_failed_attempts, lockState.failedAttempts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (isLockedOut) {
                        val lockoutStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lockState.lockoutUntil))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_locked_out_until, lockoutStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = relPin,
                        onValueChange = { relPin = it },
                        label = { Text(stringResource(R.string.hint_enter_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !isLockedOut,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (relPin.isNotBlank() && !isLockedOut) {
                            onReleaseWithPin(relPin)
                            showReleaseDialog = false
                            relPin = ""
                        }
                    },
                    enabled = relPin.isNotBlank() && !isLockedOut
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseDialog = false; relPin = "" }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
