package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmSoundOption
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SoundSelectorList(
    soundOptions: List<AlarmSoundOption>,
    selectedToneTitle: String,
    selectedToneUri: String?,
    onSelectSound: (AlarmSoundOption) -> Unit,
    onAddCustomMp3Uri: (Uri) -> Unit
) {
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddCustomMp3Uri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row with currently selected tone and "+ Add Custom MP3" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Selected: $selectedToneTitle",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AccentEmerald
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap any tone below to preview & select (${soundOptions.size} available)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = {
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PrimaryIndigo.copy(alpha = 0.25f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_custom_mp3_button")
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AccentCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Custom MP3", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable list of all phone default ringtones, custom MP3s, and built-in tones
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 250.dp)
                .testTag("sound_options_list")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(
                    items = soundOptions,
                    key = { "${it.category}_${it.title}_${it.uri}" }
                ) { option ->
                    val isSelected = if (!selectedToneUri.isNullOrBlank() && !selectedToneUri.startsWith("synthesized://")) {
                        option.uri == selectedToneUri || (option.title == selectedToneTitle && option.category == "Custom MP3")
                    } else {
                        option.title == selectedToneTitle
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSound(option) }
                            .background(
                                if (isSelected) PrimaryIndigo.copy(alpha = 0.22f) else Color.Transparent
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) AccentEmerald else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Category badge (Custom MP3 / Phone Alarm / Phone Ringtone / Built-in Tone)
                        val badgeColor = when (option.category) {
                            "Custom MP3" -> AccentCyan
                            "Built-in Tone" -> PrimaryViolet
                            else -> AccentEmerald
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = option.category,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeColor
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        color = BorderSubtle.copy(alpha = 0.35f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}
