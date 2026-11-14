package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")

        if (action == Intent.ACTION_PROVIDER_CHANGED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val syncManager = (context.applicationContext as? com.example.SyncAlarmApplication)?.calendarSyncManager
                        ?: com.example.calendar.CalendarSyncManager(context.applicationContext)
                    syncManager.syncCalendarEvents(forcePrompt = false)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error syncing calendar on provider change", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val scheduler = AlarmScheduler(context)
                    val enabledAlarms = db.alarmDao().getEnabledAlarms()

                    Log.d("BootReceiver", "Rescheduling ${enabledAlarms.size} alarms after reboot")
                    for (alarm in enabledAlarms) {
                        scheduler.scheduleAlarm(alarm)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
