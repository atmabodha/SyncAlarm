package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AlarmEntity
import com.example.ui.screens.AddEditAlarmDialog
import com.example.ui.screens.CalendarMeetingsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MidnightDark
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SyncAlarmTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SyncAlarmTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }

    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val nextUpcomingAlarm by viewModel.nextUpcomingAlarm.collectAsStateWithLifecycle()
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    val deletedMeetingAlarms by viewModel.deletedMeetingAlarms.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val soundOptions by viewModel.soundOptions.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isCalendarSyncing.collectAsStateWithLifecycle()

    // Permissions launcher for Notifications & Calendar
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val calendarGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        if (calendarGranted) {
            viewModel.syncCalendar()
        }
    }

    // Auto-sync whenever the user switches back from Google Calendar to SyncAlarm
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.syncCalendar()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.syncCalendar()
        }

        // On Android 14+ (API 34+), verify full-screen lockscreen intent access is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        containerColor = MidnightDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "SyncAlarm"
                            1 -> "Google Calendar"
                            else -> "Settings"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = "Alarms") },
                    label = { Text("Alarms") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryIndigo,
                        indicatorColor = PrimaryIndigo
                    ),
                    modifier = Modifier.testTag("nav_alarms")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.syncCalendar()
                    },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                    label = { Text("Meetings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryIndigo,
                        indicatorColor = PrimaryIndigo
                    ),
                    modifier = Modifier.testTag("nav_meetings")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryIndigo,
                        indicatorColor = PrimaryIndigo
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    alarms = alarms,
                    nextUpcomingAlarm = nextUpcomingAlarm,
                    onAddAlarmClick = {
                        editingAlarm = null
                        showAddEditDialog = true
                    },
                    onEditAlarmClick = { alarm ->
                        editingAlarm = alarm
                        showAddEditDialog = true
                    },
                    onToggleAlarm = { alarm, enabled ->
                        viewModel.toggleAlarm(alarm, enabled)
                    },
                    onDismissUpcomingAlarm = { alarm ->
                        viewModel.dismissUpcomingAlarm(alarm)
                    },
                    onDeleteAlarm = { alarm ->
                        viewModel.deleteAlarm(alarm)
                    }
                )

                1 -> CalendarMeetingsScreen(
                    meetings = meetings,
                    isSyncing = isSyncing,
                    onSyncCalendar = { viewModel.syncCalendar() },
                    onSimulateMeeting = { title, delay ->
                        viewModel.injectSampleMeeting(title, delay)
                    },
                    onScheduleMeetingAlarm = { meeting, offset ->
                        viewModel.scheduleMeetingAlarm(meeting, offset)
                    },
                    onDeleteMeeting = { meeting ->
                        viewModel.deleteMeeting(meeting)
                    }
                )

                2 -> SettingsScreen(
                    settings = settings,
                    soundOptions = soundOptions,
                    onUpdateSettings = { newSettings ->
                        viewModel.updateSettings(newSettings)
                    },
                    onImportCustomMp3 = { uri, onImported ->
                        viewModel.importCustomAudio(uri, onImported)
                    },
                    onPreviewTone = { toneTitle, toneUri, volume ->
                        viewModel.playTonePreview(context, toneTitle, toneUri, volume)
                    },
                    onStopPreview = {
                        viewModel.stopTonePreview()
                    }
                )
            }

            // Automatic prompt when a Google Calendar meeting with an active alarm has been deleted
            val pendingDeletedAlarm = deletedMeetingAlarms.firstOrNull()
            if (pendingDeletedAlarm != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        viewModel.keepDeletedMeetingAlarm(pendingDeletedAlarm)
                    },
                    containerColor = SurfaceCard,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary,
                    title = {
                        Text(
                            text = "Meeting Deleted in Google Calendar",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "\"${pendingDeletedAlarm.title}\" was deleted from your Google Calendar.\n\nShould the ${pendingDeletedAlarm.getFormattedTime()} alarm set for this meeting be deleted as well?"
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.confirmDeleteMeetingAlarm(pendingDeletedAlarm)
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.AlertCoral
                            ),
                            modifier = Modifier.testTag("confirm_delete_meeting_alarm_btn")
                        ) {
                            Text("Delete Alarm", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                viewModel.keepDeletedMeetingAlarm(pendingDeletedAlarm)
                            },
                            modifier = Modifier.testTag("keep_deleted_meeting_alarm_btn")
                        ) {
                            Text("Keep Alarm", color = TextPrimary)
                        }
                    }
                )
            }

            if (showAddEditDialog) {
                AddEditAlarmDialog(
                    initialAlarm = editingAlarm,
                    defaultSettings = settings,
                    soundOptions = soundOptions,
                    onDismissRequest = {
                        showAddEditDialog = false
                        editingAlarm = null
                    },
                    onSave = { alarm ->
                        viewModel.saveAlarm(alarm)
                        showAddEditDialog = false
                        editingAlarm = null
                    },
                    onDelete = { alarm ->
                        viewModel.deleteAlarm(alarm)
                        showAddEditDialog = false
                        editingAlarm = null
                    },
                    onImportCustomMp3 = { uri, onImported ->
                        viewModel.importCustomAudio(uri, onImported)
                    },
                    onPreviewTone = { toneTitle, toneUri, volume ->
                        viewModel.playTonePreview(context, toneTitle, toneUri, volume)
                    },
                    onStopPreview = {
                        viewModel.stopTonePreview()
                    }
                )
            }
        }
    }
}
