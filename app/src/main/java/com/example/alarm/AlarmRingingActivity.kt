package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.MidnightDark
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SyncAlarmTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    private var currentAlarmId by androidx.compose.runtime.mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnOverLockscreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        currentAlarmId = intent?.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L) ?: -1L

        setContent {
            SyncAlarmTheme {
                val alarmId = currentAlarmId
                var alarm by remember(alarmId) { mutableStateOf<AlarmEntity?>(null) }

                LaunchedEffect(alarmId) {
                    if (alarmId != -1L) {
                        val loaded = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(applicationContext).alarmDao().getAlarmById(alarmId)
                        }
                        alarm = loaded
                    }
                }

                AlarmRingingScreen(
                    alarm = alarm,
                    onDismiss = {
                        val dismissIntent = Intent(this@AlarmRingingActivity, AlarmActionReceiver::class.java).apply {
                            action = AlarmNotificationManager.ACTION_DISMISS_ALARM
                            putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarmId)
                        }
                        sendBroadcast(dismissIntent)
                        finishAndRemoveTask()
                    },
                    onSnooze = {
                        val snoozeIntent = Intent(this@AlarmRingingActivity, AlarmActionReceiver::class.java).apply {
                            action = AlarmNotificationManager.ACTION_SNOOZE_ALARM
                            putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarmId)
                        }
                        sendBroadcast(snoozeIntent)
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnOverLockscreen()
        val newId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
        if (newId != -1L) {
            currentAlarmId = newId
        }
    }

    /**
     * Displays the alarm screen directly ON TOP of the lock screen and wakes the display
     * WITHOUT calling requestDismissKeyguard, so the user never has to enter a PIN/pattern/biometric
     * just to see, snooze, or dismiss the ringing alarm.
     */
    private fun turnScreenOnOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }
}

@Composable
fun AlarmRingingScreen(
    alarm: AlarmEntity?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val currentTimeStr = remember { timeFormat.format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightDark, Color(0xFF1B1333), MidnightDark)
                )
            )
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Live Clock
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.testTag("ringing_clock_display")
                )
                Spacer(modifier = Modifier.height(8.dp))
                val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Glowing Alarm Bell
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AlertCoral.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = AlertCoral,
                    shadowElevation = 16.dp,
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Ringing Alarm",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title & Meeting Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = alarm?.title ?: "Alarm",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.testTag("ringing_alarm_title")
                )
                if (alarm?.scheduleType == AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT && alarm.meetingStartTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val meetingTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(alarm.meetingStartTime))
                    Text(
                        text = "Meeting begins at $meetingTimeStr (${alarm.offsetMinutes}m gap)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryIndigo
                    )
                }
                if (alarm != null && alarm.currentRingCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Auto-repeat ring ${alarm.currentRingCount + 1} of ${alarm.maxReAlarmRings}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertCoral
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Actions: Snooze and Dismiss
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Dismiss Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("dismiss_alarm_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "DISMISS ALARM",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Snooze Button
                val snoozeMins = alarm?.snoozeDurationMinutes ?: 10
                FilledTonalButton(
                    onClick = onSnooze,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PrimaryViolet.copy(alpha = 0.35f),
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("snooze_alarm_button")
                ) {
                    Icon(imageVector = Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Snooze ($snoozeMins mins)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
