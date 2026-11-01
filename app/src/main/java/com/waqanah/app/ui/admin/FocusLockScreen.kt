package com.waqanah.app.ui.admin

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waqanah.app.R
import com.waqanah.app.service.admin.DeviceOwnerStatus
import com.waqanah.app.service.enforcement.AppInfo
import com.waqanah.app.service.enforcement.EnforcementMode
import com.waqanah.app.service.enforcement.ShieldService
import com.waqanah.app.service.hardening.DnsHostValidator
import com.waqanah.app.service.hardening.DnsProvider
import com.waqanah.app.service.hardening.PolicyId
import com.waqanah.app.service.hardening.PolicyResult
import com.waqanah.app.service.hardening.PolicyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FocusLockScreen(
    modifier: Modifier = Modifier,
    viewModel: ShieldViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewPlan by viewModel.previewPlan.collectAsState()
    val detectedBrowsers by viewModel.detectedBrowsers.collectAsState()
    val installedNonSystemApps by viewModel.installedNonSystemApps.collectAsState()
    val hardeningResults by viewModel.hardeningResults.collectAsState()
    val isServiceRunning by ShieldService.running.collectAsState()
    val context = LocalContext.current

    var notificationDeniedHint by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            notificationDeniedHint = true
        }
        viewModel.setProtectionEnabled(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        when (uiState.deviceOwnerStatus) {
            is DeviceOwnerStatus.NotProvisioned -> {
                NotProvisionedCard(onCheckAgain = { viewModel.refreshStatus() })
            }

            is DeviceOwnerStatus.Active -> {
                ActiveCard()

                Spacer(modifier = Modifier.height(20.dp))

                ActiveEnforcementPanel(
                    uiState = uiState,
                    isServiceRunning = isServiceRunning,
                    notificationDeniedHint = notificationDeniedHint,
                    detectedBrowsers = detectedBrowsers,
                    installedNonSystemApps = installedNonSystemApps,
                    hardeningResults = hardeningResults,
                    onProtectionToggle = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setProtectionEnabled(enabled)
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    // Preview Dialog
    previewPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPreview() },
            title = { Text(stringResource(R.string.title_preview_dialog)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.label_target_count, plan.targets.size),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.label_skipped_count, plan.skipped.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    plan.targets.forEach { target ->
                        Text(
                            text = stringResource(R.string.item_format_preview_target, target.label, target.packageName, target.reason.name),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPreview() }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        )
    }
}

@Composable
private fun ActiveEnforcementPanel(
    uiState: ShieldUiState,
    isServiceRunning: Boolean,
    notificationDeniedHint: Boolean,
    detectedBrowsers: List<AppInfo>,
    installedNonSystemApps: List<AppInfo>,
    hardeningResults: List<PolicyResult>,
    onProtectionToggle: (Boolean) -> Unit,
    viewModel: ShieldViewModel
) {
    val context = LocalContext.current
    var showBrowserDropdown by remember { mutableStateOf(false) }
    var showUninstallWarningDialog by remember { mutableStateOf(false) }
    var showAllowListPicker by remember { mutableStateOf(false) }
    var showCustomBlocklistPicker by remember { mutableStateOf(false) }

    // Protection Enabled Switch Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_protection_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = uiState.enforcementEnabled,
                    onCheckedChange = { onProtectionToggle(it) }
                )
            }

            if (notificationDeniedHint) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.hint_notification_permission_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Allowed Browser Dropdown
            Text(
                text = stringResource(R.string.label_allowed_browser),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedButton(
                    onClick = { showBrowserDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = detectedBrowsers.find { it.packageName == uiState.allowedBrowserPackage }?.label
                        ?: uiState.allowedBrowserPackage
                        ?: stringResource(R.string.warning_no_allowed_browser)
                    Text(text = label, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_dropdown))
                }
                DropdownMenu(
                    expanded = showBrowserDropdown,
                    onDismissRequest = { showBrowserDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none_browser_inactive)) },
                        onClick = {
                            viewModel.setAllowedBrowserPackage(null)
                            showBrowserDropdown = false
                        }
                    )
                    detectedBrowsers.forEach { browser ->
                        DropdownMenuItem(
                            text = { Text("${browser.label} (${browser.packageName})") },
                            onClick = {
                                viewModel.setAllowedBrowserPackage(browser.packageName)
                                showBrowserDropdown = false
                            }
                        )
                    }
                }
            }

            if (uiState.allowedBrowserPackage == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.warning_no_allowed_browser),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enforcement Mode Radio Selection
            Text(
                text = stringResource(R.string.label_enforcement_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.enforcementMode == EnforcementMode.HIDE_AND_SUSPEND,
                    onClick = { viewModel.setEnforcementMode(EnforcementMode.HIDE_AND_SUSPEND) }
                )
                Text(text = stringResource(R.string.mode_hide_and_suspend), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.enforcementMode == EnforcementMode.UNINSTALL,
                    onClick = { showUninstallWarningDialog = true }
                )
                Text(text = stringResource(R.string.mode_uninstall), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hide System Browsers Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_hide_system_browsers),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.hideSystemBrowsers,
                    onCheckedChange = { viewModel.setHideSystemBrowsers(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Preview & Scan and Apply Now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.generatePreview() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_preview))
                }
                Button(
                    onClick = { viewModel.scanAndApplyNow() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_scan_and_apply))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Allow List & Custom Blocklist Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showAllowListPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_never_hide_allowlist))
                }
                OutlinedButton(
                    onClick = { showCustomBlocklistPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_custom_blocklist))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // System Hardening Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.card_hardening_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Hardening policy status rows
            val policyLabels = mapOf(
                PolicyId.UNINSTALL_BLOCKED to stringResource(R.string.policy_uninstall_blocked),
                PolicyId.FORCE_STOP_BLOCKED to stringResource(R.string.policy_force_stop_blocked),
                PolicyId.SAFE_BOOT_BLOCKED to stringResource(R.string.policy_safe_boot_blocked),
                PolicyId.VPN_CONFIG_BLOCKED to stringResource(R.string.policy_vpn_config_blocked),
                PolicyId.DATE_TIME_LOCKED to stringResource(R.string.policy_date_time_locked),
                PolicyId.PRIVATE_DNS_LOCKED to stringResource(R.string.policy_private_dns_locked)
            )

            policyLabels.forEach { (id, label) ->
                val result = hardeningResults.find { it.id == id }
                val stateText = when (result?.state) {
                    PolicyState.APPLIED -> stringResource(R.string.state_applied)
                    PolicyState.UNCHANGED -> stringResource(R.string.state_unchanged)
                    PolicyState.RELEASED -> stringResource(R.string.state_released)
                    PolicyState.UNSUPPORTED -> stringResource(R.string.state_unsupported)
                    PolicyState.FAILED -> stringResource(R.string.state_failed, result.message ?: "")
                    null -> stringResource(R.string.state_released)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(text = stateText, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Private DNS Provider Radio Buttons
            Text(
                text = stringResource(R.string.label_dns_provider),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.dnsProvider == DnsProvider.FAMILY,
                    onClick = { viewModel.setDnsProvider(DnsProvider.FAMILY) }
                )
                Text(text = stringResource(R.string.dns_family), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.dnsProvider == DnsProvider.ADULT,
                    onClick = { viewModel.setDnsProvider(DnsProvider.ADULT) }
                )
                Text(text = stringResource(R.string.dns_adult), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = uiState.dnsProvider == DnsProvider.CUSTOM,
                    onClick = { viewModel.setDnsProvider(DnsProvider.CUSTOM) }
                )
                Text(text = stringResource(R.string.dns_custom), style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.dnsProvider == DnsProvider.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.customDnsHost,
                    onValueChange = { viewModel.setCustomDnsHost(it) },
                    label = { Text(stringResource(R.string.hint_custom_dns)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.customDnsHost.isNotBlank() && !DnsHostValidator.isValid(uiState.customDnsHost)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.error_invalid_dns),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.scanAndApplyNow() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_reapply_now))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.hint_dns_bypass),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hint_dns_network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Background Protection Status Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.card_bg_protection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            val serviceStatusText = if (isServiceRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped)
            Text(
                text = stringResource(R.string.label_service_status, serviceStatusText),
                style = MaterialTheme.typography.bodyMedium
            )

            val scanFormatted = if (uiState.lastEnforcementAt > 0L) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(uiState.lastEnforcementAt))
            } else {
                stringResource(R.string.time_never)
            }
            Text(
                text = stringResource(R.string.label_last_scan, scanFormatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_allow_bg_activity))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.help_samsung_bg_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // List "Hidden by Waqanah"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.title_hidden_by_waqanah),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.hiddenByWaqanah.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_hidden_apps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.hiddenByWaqanah.forEach { (pkg, reason) ->
                    Text(
                        text = stringResource(R.string.item_format_hidden_pkg, pkg, reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // List "Uninstalled by Waqanah (cannot be restored)"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.title_uninstalled_by_waqanah),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.uninstalledByWaqanah.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_uninstalled_apps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.uninstalledByWaqanah.forEach { pkg ->
                    Text(
                        text = stringResource(R.string.item_format_bullet_pkg, pkg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Uninstall Warning Dialog
    if (showUninstallWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallWarningDialog = false },
            title = { Text(stringResource(R.string.dialog_uninstall_warning_title)) },
            text = { Text(stringResource(R.string.dialog_uninstall_warning_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setEnforcementMode(EnforcementMode.UNINSTALL)
                        showUninstallWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallWarningDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Allow-list Picker Dialog
    if (showAllowListPicker) {
        AppPickerDialog(
            title = stringResource(R.string.btn_never_hide_allowlist),
            installedApps = installedNonSystemApps,
            initialSelectedPackages = uiState.allowListPackages,
            onDismiss = { showAllowListPicker = false },
            onConfirm = { selected ->
                viewModel.updateAllowList(selected)
                showAllowListPicker = false
            }
        )
    }

    // Custom Blocklist Picker Dialog
    if (showCustomBlocklistPicker) {
        AppPickerDialog(
            title = stringResource(R.string.btn_custom_blocklist),
            installedApps = installedNonSystemApps,
            initialSelectedPackages = uiState.customBlocklist,
            onDismiss = { showCustomBlocklistPicker = false },
            onConfirm = { selected ->
                viewModel.updateCustomBlocklist(selected)
                showCustomBlocklistPicker = false
            }
        )
    }
}

@Composable
private fun NotProvisionedCard(onCheckAgain: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var isTroubleshootingExpanded by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val adbCommand = stringResource(R.string.adb_command)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.enable_shield_mode_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = stringResource(R.string.checklist_step_1), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = stringResource(R.string.checklist_step_2), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = stringResource(R.string.checklist_step_3), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = stringResource(R.string.checklist_step_4), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = adbCommand,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(adbCommand))
                            isCopied = true
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (isCopied) stringResource(R.string.btn_copied)
                            else stringResource(R.string.btn_copy_command)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = stringResource(R.string.checklist_step_5), style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCheckAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_check_again))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.troubleshooting_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { isTroubleshootingExpanded = !isTroubleshootingExpanded }) {
                    Icon(
                        imageVector = if (isTroubleshootingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_toggle_troubleshooting)
                    )
                }
            }

            AnimatedVisibility(visible = isTroubleshootingExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = stringResource(R.string.troubleshooting_accounts_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.troubleshooting_users_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.shield_active_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.shield_active_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
