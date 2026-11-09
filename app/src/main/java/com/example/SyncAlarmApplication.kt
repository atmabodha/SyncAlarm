package com.example

import android.app.Application
import com.example.alarm.AlarmNotificationManager
import com.example.calendar.CalendarSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncAlarmApplication : Application() {

    lateinit var calendarSyncManager: CalendarSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize Notification Channels
        AlarmNotificationManager.createNotificationChannels(this)

        // Initialize CalendarSyncManager
        calendarSyncManager = CalendarSyncManager(this)

        CoroutineScope(Dispatchers.IO).launch {
            calendarSyncManager.syncCalendarEvents()
        }
    }
}
