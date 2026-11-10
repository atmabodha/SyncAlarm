package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import com.example.alarm.AlarmSoundOption
import com.example.data.model.AlarmEntity
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AddEditAlarmDialog(
    initialAlarm: AlarmEntity? = null,
    soundOptions: List<AlarmSoundOption>,
    onDismissRequest: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: ((AlarmEntity) -> Unit)? = null,
    onImportCustomMp3: (Uri, (AlarmSoundOption) -> Unit) -> Unit,
    onPreviewTone: (String?, String?, Float) -> Unit,
    onStopPreview: () -> Unit
) {
    val context = LocalContext.current

    var hour by remember { mutableIntStateOf(initialAlarm?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initialAlarm?.minute ?: 0) }
    var title by remember { mutableStateOf(initialAlarm?.title ?: "Wake Up") }
    var scheduleType by remember {
        mutableStateOf(initialAlarm?.scheduleType ?: AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS)
    }
    var repeatDays by remember {
        mutableIntStateOf(initialAlarm?.repeatDays ?: 0)
    }
    var specificDateMillis by remember {
        mutableStateOf<Long?>(
            initialAlarm?.specificDateMillis
                ?: initialAlarm?.triggerTimeMillis?.takeIf { it > 0L }
                ?: initialAlarm?.meetingStartTime?.takeIf { it > 0L }
                ?: (System.currentTimeMillis() + 86400000L)
        )
    }
    var volume by remember { mutableFloatStateOf(initialAlarm?.volume ?: 0.85f) }
    var vibrate by remember { mutableStateOf(initialAlarm?.vibrate ?: true) }
    var toneTitle by remember { mutableStateOf(initialAlarm?.toneTitle ?: "Gentle Digital Chime") }
    var toneUri by remember { mutableStateOf<String?>(initialAlarm?.toneUri) }
    var snoozeMinutes by remember { mutableIntStateOf(initialAlarm?.snoozeDurationMinutes ?: 10) }
    var maxReAlarmRings by remember { mutableIntStateOf(initialAlarm?.maxReAlarmRings ?: 3) }
    var reAlarmGapMinutes by remember { mutableIntStateOf(initialAlarm?.reAlarmGapMinutes ?: 5) }
    var isPreviewing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onStopPreview()
        }
    }

    Dialog(
        onDismissRequest = {
            onStopPreview()
            onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .systemBarsPadding()
                .padding(vertical = 28.dp),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceCard,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialAlarm == null) "Set New Alarm" else "Edit Alarm",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    IconButton(
                        onClick = {
                            onStopPreview()
                            onDismissRequest()
                        },
                        modifier = Modifier.testTag("close_alarm_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Display & Picker Trigger
                val formattedTime = remember(hour, minute) {
                    val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    val amPm = if (hour < 12) "AM" else "PM"
                    String.format("%02d:%02d %s", h12, minute, amPm)
                }

                Card(
                    onClick = {
                        val tpd = TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            false
                        )
                        tpd.show()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_time_picker")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo,
                                fontSize = 48.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to change alarm time",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alarm Label
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Alarm Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Schedule Type Tab (Recurring Days vs Specific Date vs One-Time)
                Text(
                    text = "Schedule Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val tabs = listOf("Repeat Days", "Specific Date", "Once")
                val selectedTabIndex = when (scheduleType) {
                    AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS -> 0
                    AlarmEntity.SCHEDULE_TYPE_SPECIFIC_DATE,
                    AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT -> 1
                    else -> 2
                }

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = SurfaceElevated,
                    contentColor = PrimaryIndigo,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = PrimaryIndigo
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                scheduleType = when (index) {
                                    0 -> AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS
                                    1 -> AlarmEntity.SCHEDULE_TYPE_SPECIFIC_DATE
                                    else -> AlarmEntity.SCHEDULE_TYPE_ONE_TIME
                                }
                            },
                            text = { Text(tabTitle) },
                            modifier = Modifier.testTag("tab_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content: Recurring Days Selector
                if (scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) {
                    Column {
                        Text(
                            text = "Repeat on Days",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Day of week chips
                        val dayMap = listOf(
                            "M" to AlarmEntity.DAY_MON,
                            "T" to AlarmEntity.DAY_TUE,
                            "W" to AlarmEntity.DAY_WED,
                            "T" to AlarmEntity.DAY_THU,
                            "F" to AlarmEntity.DAY_FRI,
                            "S" to AlarmEntity.DAY_SAT,
                            "S" to AlarmEntity.DAY_SUN
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            dayMap.forEachIndexed { idx, (label, bit) ->
                                val isSelected = (repeatDays and bit) != 0
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            color = if (isSelected) PrimaryIndigo else SurfaceElevated,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            repeatDays = if (isSelected) {
                                                repeatDays and bit.inv()
                                            } else {
                                                repeatDays or bit
                                            }
                                        }
                                        .testTag("day_chip_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TextSecondary
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset chips: Mon & Thu, Weekdays, Weekends, Everyday
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = repeatDays == (AlarmEntity.DAY_MON or AlarmEntity.DAY_THU),
                                onClick = { repeatDays = AlarmEntity.DAY_MON or AlarmEntity.DAY_THU },
                                label = { Text("Mon & Thu", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = repeatDays == AlarmEntity.DAYS_WEEKDAYS,
                                onClick = { repeatDays = AlarmEntity.DAYS_WEEKDAYS },
                                label = { Text("Weekdays", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = repeatDays == AlarmEntity.DAYS_EVERYDAY,
                                onClick = { repeatDays = AlarmEntity.DAYS_EVERYDAY },
                                label = { Text("Everyday", fontSize = 12.sp) }
                            )
                        }
                    }
                } else if (scheduleType == AlarmEntity.SCHEDULE_TYPE_SPECIFIC_DATE ||
                    scheduleType == AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT
                ) {
                    // Specific Future Date Picker Button
                    val dateFormatted = remember(specificDateMillis) {
                        if (specificDateMillis != null) {
                            SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date(specificDateMillis!!))
                        } else "Choose Date"
                    }

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = specificDateMillis ?: System.currentTimeMillis()
                            }
                            val dpd = DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    specificDateMillis = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            dpd.datePicker.minDate = System.currentTimeMillis() - 1000L
                            dpd.show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_date_picker"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = dateFormatted)
                    }
                } else {
                    Text(
                        text = "Alarm will ring once at the next upcoming occurrence today or tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sound & Volume Controls
                Text(
                    text = "Sound & Volume",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Full Ringtone List + Custom MP3 Import
                SoundSelectorList(
                    soundOptions = soundOptions,
                    selectedToneTitle = toneTitle,
                    selectedToneUri = toneUri,
                    onSelectSound = { option ->
                        toneTitle = option.title
                        toneUri = option.uri
                        onPreviewTone(option.title, option.uri, volume)
                        isPreviewing = true
                    },
                    onAddCustomMp3Uri = { uri ->
                        onImportCustomMp3(uri) { imported ->
                            toneTitle = imported.title
                            toneUri = imported.uri
                            onPreviewTone(imported.title, imported.uri, volume)
                            isPreviewing = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Volume slider with live Preview Play / Stop Button
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
                                onPreviewTone(toneTitle, toneUri, volume)
                                isPreviewing = true
                            }
                        },
                        modifier = Modifier
                            .background(PrimaryViolet.copy(alpha = 0.2f), CircleShape)
                            .testTag("preview_sound_button")
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
                            text = "Volume: ${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                if (isPreviewing) onPreviewTone(toneTitle, toneUri, volume)
                            },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryIndigo,
                                activeTrackColor = PrimaryIndigo
                            ),
                            modifier = Modifier.testTag("volume_slider")
                        )
                    }
                }

                // Vibrate Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Vibrate on Alarm", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Switch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Re-Alarm & Snooze Rules
                Text(
                    text = "Snooze & Re-Alarm Rules",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Snooze Interval Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Snooze:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            FilterChip(
                                selected = snoozeMinutes == m,
                                onClick = { snoozeMinutes = m },
                                label = { Text("${m}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Max Re-Alarm Rings (if user takes no action)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Max auto re-rings:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 5).forEach { r ->
                            FilterChip(
                                selected = maxReAlarmRings == r,
                                onClick = { maxReAlarmRings = r },
                                label = { Text("${r}x", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Gap Between Re-Alarms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Gap between rings:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 5, 10).forEach { g ->
                            FilterChip(
                                selected = reAlarmGapMinutes == g,
                                onClick = { reAlarmGapMinutes = g },
                                label = { Text("${g}m", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save & Cancel Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onStopPreview()
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onStopPreview()
                            val base = initialAlarm ?: AlarmEntity()
                            val keepAsCalendar = !base.isManual && base.calendarEventId != null
                            val resolvedScheduleType = if (keepAsCalendar && scheduleType == AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT) {
                                AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT
                            } else {
                                scheduleType
                            }
                            val alarm = base.copy(
                                isManual = !keepAsCalendar,
                                title = title.ifBlank { "Alarm" },
                                hour = hour,
                                minute = minute,
                                scheduleType = resolvedScheduleType,
                                repeatDays = repeatDays,
                                specificDateMillis = if (resolvedScheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) null else specificDateMillis,
                                volume = volume,
                                vibrate = vibrate,
                                toneTitle = toneTitle,
                                toneUri = toneUri,
                                snoozeDurationMinutes = snoozeMinutes,
                                maxReAlarmRings = maxReAlarmRings,
                                reAlarmGapMinutes = reAlarmGapMinutes,
                                isEnabled = true,
                                isDismissed = false
                            )
                            onSave(alarm)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_alarm_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Alarm")
                    }
                }

                if (initialAlarm != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            onStopPreview()
                            onDelete(initialAlarm)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.example.ui.theme.AlertCoral
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_delete_alarm_button")
                    ) {
                        Text("Delete Alarm", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
