package com.waqanah.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waqanah.app.BuildConfig
import com.waqanah.app.R
import com.waqanah.app.service.admin.DeviceOwnerStatus

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val translations by viewModel.translationsState.collectAsState()
    val reciters by viewModel.recitersState.collectAsState()

    var showTranslationDropdown by remember { mutableStateOf(false) }
    var showReciterDropdown by remember { mutableStateOf(false) }
    var showRemoveOwnerDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOwnerStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preferred Translation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.pref_translation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { showTranslationDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = translations.find { it.identifier == uiState.selectedTranslation }?.englishName
                                ?: uiState.selectedTranslation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_dropdown))
                    }
                    DropdownMenu(
                        expanded = showTranslationDropdown,
                        onDismissRequest = { showTranslationDropdown = false }
                    ) {
                        translations.take(25).forEach { edition ->
                            DropdownMenuItem(
                                text = { Text("${edition.name} (${edition.language.uppercase()})") },
                                onClick = {
                                    viewModel.updateTranslation(edition.identifier)
                                    showTranslationDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preferred Reciter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.pref_reciter_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { showReciterDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reciters.find { it.identifier == uiState.selectedReciter }?.englishName
                                ?: uiState.selectedReciter,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_dropdown))
                    }
                    DropdownMenu(
                        expanded = showReciterDropdown,
                        onDismissRequest = { showReciterDropdown = false }
                    ) {
                        reciters.take(25).forEach { reciter ->
                            DropdownMenuItem(
                                text = { Text(reciter.englishName) },
                                onClick = {
                                    viewModel.updateReciter(reciter.identifier)
                                    showReciterDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security & Device Owner Status
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.security_status_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val isOwner = uiState.deviceOwnerStatus is DeviceOwnerStatus.Active
                val statusText = if (isOwner) stringResource(R.string.status_active) else stringResource(R.string.status_not_provisioned)
                Text(text = stringResource(R.string.device_owner_status_label, statusText))

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.blocked_packages_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                uiState.blockedPackages.forEach { pkg ->
                    Text(
                        text = stringResource(R.string.item_format_bullet_pkg, pkg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Debug-only escape hatch
        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showRemoveOwnerDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_debug_remove_owner))
            }
        }
    }

    if (showRemoveOwnerDialog && BuildConfig.DEBUG) {
        AlertDialog(
            onDismissRequest = { showRemoveOwnerDialog = false },
            title = { Text(stringResource(R.string.dialog_remove_owner_title)) },
            text = { Text(stringResource(R.string.dialog_remove_owner_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeDeviceOwnerDebug()
                        showRemoveOwnerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveOwnerDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
