package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarMeeting
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CalendarMeetingsScreen(
    meetings: List<CalendarMeeting>,
    isSyncing: Boolean,
    onSyncCalendar: () -> Unit,
    onScheduleMeetingAlarm: (CalendarMeeting, Int) -> Unit,
    onSimulateMeeting: (String, Int) -> Unit,
    onDeleteMeeting: (CalendarMeeting) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Info & Sync Controls Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = AccentEmerald.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Google Calendar Sync",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Auto-detects newly added meetings",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onSyncCalendar,
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("sync_calendar_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryIndigo,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Now",
                                tint = PrimaryIndigo
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulate New Meeting Button (Quick tester for the notification prompt)
                OutlinedButton(
                    onClick = {
                        val sampleTitles = listOf("Design Sprint Review", "Client Strategy Call", "Team Standup", "Product Roadmap Sync")
                        val randomTitle = sampleTitles.random()
                        onSimulateMeeting(randomTitle, 15) // Meeting in 15 mins
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_meeting_prompt_button")
                ) {
                    Icon(imageVector = Icons.Default.AddAlert, contentDescription = null, tint = AccentEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate New Calendar Meeting (+ Prompt)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Meetings List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Upcoming Meetings (${meetings.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (meetings.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Meetings Found",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add meetings to your Google Calendar or tap 'Simulate' above to test notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(meetings, key = { "${it.id}_${it.startTimeMillis}" }) { meeting ->
                    MeetingCard(
                        meeting = meeting,
                        onScheduleAlarm = { offset -> onScheduleMeetingAlarm(meeting, offset) },
                        onDeleteMeeting = { onDeleteMeeting(meeting) }
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingCard(
    meeting: CalendarMeeting,
    onScheduleAlarm: (Int) -> Unit,
    onDeleteMeeting: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meeting_card_${meeting.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meeting.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = meeting.getFormattedStartTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (meeting.isRecurring()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryIndigo.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Repeats: ${meeting.getRecurrenceSummary()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryIndigo
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (meeting.location.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = meeting.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meeting.hasAlarmScheduled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentEmerald.copy(alpha = 0.2f),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlarmOn,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "-${meeting.scheduledOffsetMinutes ?: 10}m Set",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentEmerald
                                    )
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDeleteMeeting,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_meeting_${meeting.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Meeting",
                            tint = AlertCoral.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Alarm gap scheduling action buttons: 5 mins or 10 mins before
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onScheduleAlarm(5) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (meeting.scheduledOffsetMinutes == 5) PrimaryIndigo else SurfaceElevated,
                        contentColor = if (meeting.scheduledOffsetMinutes == 5) Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_5m_${meeting.id}")
                ) {
                    Text("Alarm -5m", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { onScheduleAlarm(10) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (meeting.scheduledOffsetMinutes == 10) PrimaryIndigo else SurfaceElevated,
                        contentColor = if (meeting.scheduledOffsetMinutes == 10) Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_10m_${meeting.id}")
                ) {
                    Text("Alarm -10m", fontSize = 12.sp)
                }
            }
        }
    }
}
