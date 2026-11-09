package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.example.alarm.AlarmSoundOption
import com.example.data.repository.AppSettings
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: AppSettings,
    soundOptions: List<AlarmSoundOption>,
    onUpdateSettings: (AppSettings) -> Unit,
    onImportCustomMp3: (Uri, (AlarmSoundOption) -> Unit) -> Unit,
    onPreviewTone: (String?, String?, Float) -> Unit,
    onStopPreview: () -> Unit
) {
    var isPreviewing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onStopPreview()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Alarm & Calendar Preferences",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        // Section 1: Re-Alarm Rules (Fail-safe auto re-ring if no action taken)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Auto Re-Alarm (When No Action Taken)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "If you sleep through an alarm, it auto-silences and rings again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max Re-Alarm Rings
                Text("Max Re-Ring Count:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 5).forEach { r ->
                        FilterChip(
                            selected = settings.maxReAlarmRings == r,
                            onClick = { onUpdateSettings(settings.copy(maxReAlarmRings = r)) },
                            label = { Text("${r} times", fontSize = 12.sp) },
                            modifier = Modifier.testTag("max_rings_$r")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gap between Re-Alarms
                Text("Gap Between Re-Rings:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 5, 10).forEach { g ->
                        FilterChip(
                            selected = settings.reAlarmGapMinutes == g,
                            onClick = { onUpdateSettings(settings.copy(reAlarmGapMinutes = g)) },
                            label = { Text("${g} mins", fontSize = 12.sp) },
                            modifier = Modifier.testTag("re_alarm_gap_$g")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Silence Timeout
                Text("Auto-Silence Timeout:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 120).forEach { s ->
                        FilterChip(
                            selected = settings.autoSilenceSeconds == s,
                            onClick = { onUpdateSettings(settings.copy(autoSilenceSeconds = s)) },
                            label = { Text("${s} sec", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Section 2: Snooze Settings
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Snooze Settings",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Interval when you manually tap the Snooze button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15).forEach { m ->
                        FilterChip(
                            selected = settings.defaultSnoozeMinutes == m,
                            onClick = { onUpdateSettings(settings.copy(defaultSnoozeMinutes = m)) },
                            label = { Text("${m} minutes", fontSize = 12.sp) },
                            modifier = Modifier.testTag("snooze_setting_$m")
                        )
                    }
                }
            }
        }

        // Section 3: Google Calendar Lead-Time Default
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Google Calendar Preferences",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Notification behavior when new meetings are added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Prompt on new meeting", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Switch(
                        checked = settings.promptCalendarMeetings,
                        onCheckedChange = { onUpdateSettings(settings.copy(promptCalendarMeetings = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Default Gap Before Meeting:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10).forEach { lt ->
                        FilterChip(
                            selected = settings.defaultLeadTimeMinutes == lt,
                            onClick = { onUpdateSettings(settings.copy(defaultLeadTimeMinutes = lt)) },
                            label = { Text("${lt} mins before", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Section 4: Sound, Volume & Haptics
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sound & Haptics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Full Ringtone List + Custom MP3 Import
                SoundSelectorList(
                    soundOptions = soundOptions,
                    selectedToneTitle = settings.defaultToneTitle,
                    selectedToneUri = settings.defaultToneUri,
                    onSelectSound = { option ->
                        onUpdateSettings(
                            settings.copy(
                                defaultToneTitle = option.title,
                                defaultToneUri = option.uri
                            )
                        )
                        onPreviewTone(option.title, option.uri, settings.defaultVolume)
                        isPreviewing = true
                    },
                    onAddCustomMp3Uri = { uri ->
                        onImportCustomMp3(uri) { imported ->
                            onUpdateSettings(
                                settings.copy(
                                    defaultToneTitle = imported.title,
                                    defaultToneUri = imported.uri
                                )
                            )
                            onPreviewTone(imported.title, imported.uri, settings.defaultVolume)
                            isPreviewing = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Volume Slider with Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isPreviewing) {
                                onStopPreview()
                                isPreviewing = false
                            } else {
                                onPreviewTone(
                                    settings.defaultToneTitle,
                                    settings.defaultToneUri,
                                    settings.defaultVolume
                                )
                                isPreviewing = true
                            }
                        },
                        modifier = Modifier
                            .background(PrimaryViolet.copy(alpha = 0.2f), CircleShape)
                            .testTag("preview_settings_sound_button")
                    ) {
                        Icon(
                            imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Preview Tone",
                            tint = PrimaryIndigo
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Volume: ${(settings.defaultVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.defaultVolume,
                            onValueChange = {
                                onUpdateSettings(settings.copy(defaultVolume = it))
                                if (isPreviewing) {
                                    onPreviewTone(
                                        settings.defaultToneTitle,
                                        settings.defaultToneUri,
                                        it
                                    )
                                }
                            },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryIndigo,
                                activeTrackColor = PrimaryIndigo
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vibrate Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibration", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Switch(
                        checked = settings.vibrateEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(vibrateEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
