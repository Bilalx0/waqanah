package com.waqanah.app.ui.quran

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun QuranReflectionScreen(
    modifier: Modifier = Modifier,
    viewModel: QuranViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val audioState by viewModel.audioPlayerState.collectAsState()

    var showTranslationDropdown by remember { mutableStateOf(false) }
    var showReciterDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen Title
        Text(
            text = "Daily Quran Reflection (وقانة)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Translation & Reciter Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                OutlinedButton(onClick = { showTranslationDropdown = true }) {
                    Text(
                        text = uiState.translations.find { it.identifier == uiState.selectedTranslationId }?.englishName
                            ?: "Translation",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Translation")
                }
                DropdownMenu(
                    expanded = showTranslationDropdown,
                    onDismissRequest = { showTranslationDropdown = false }
                ) {
                    uiState.translations.take(20).forEach { edition ->
                        DropdownMenuItem(
                            text = { Text("${edition.name} (${edition.language.uppercase()})") },
                            onClick = {
                                viewModel.selectTranslation(edition.identifier)
                                showTranslationDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                OutlinedButton(onClick = { showReciterDropdown = true }) {
                    Text(
                        text = uiState.reciters.find { it.identifier == uiState.selectedReciterId }?.englishName
                            ?: "Reciter",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Reciter")
                }
                DropdownMenu(
                    expanded = showReciterDropdown,
                    onDismissRequest = { showReciterDropdown = false }
                ) {
                    uiState.reciters.take(20).forEach { reciter ->
                        DropdownMenuItem(
                            text = { Text(reciter.englishName) },
                            onClick = {
                                viewModel.selectReciter(reciter.identifier)
                                showReciterDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage ?: "An error occurred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadNextRandomAyah() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            uiState.ayahDetail?.let { detail ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${detail.surahNameArabic} • ${detail.surahNameEnglish} (${detail.surahNumber}:${detail.numberInSurah})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Arabic Uthmani Text
                        Text(
                            text = detail.arabicText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 26.sp,
                                lineHeight = 42.sp
                            ),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Translation Text
                        Text(
                            text = detail.translationText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Left,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Player Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (audioState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            } else {
                                Button(
                                    onClick = { viewModel.toggleAudioPlayback() },
                                    enabled = detail.audioUrl != null
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = if (audioState.isPlaying) "Pause" else "Play"
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (audioState.isPlaying) "Pause" else "Play Recitation")
                                }
                            }
                        }

                        IconButton(onClick = { viewModel.loadNextRandomAyah() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Next Ayah"
                            )
                        }
                    }
                }
            }
        }
    }
}
