package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("AlarmActionReceiver", "Handling action: $action")

        val db = AppDatabase.getInstance(context)
        val scheduler = AlarmScheduler(context)

        when (action) {
            AlarmNotificationManager.ACTION_DISMISS_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
                stopAudioService(context)
                if (alarmId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val alarm = db.alarmDao().getAlarmById(alarmId) ?: return@launch
                        advanceOrDisableAlarm(db, scheduler, alarm)
                    }
                }
            }

            AlarmNotificationManager.ACTION_SNOOZE_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
                stopAudioService(context)
                if (alarmId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val alarm = db.alarmDao().getAlarmById(alarmId) ?: return@launch
                        val snoozeMillis = alarm.snoozeDurationMinutes * 60 * 1000L
                        val nextTrigger = System.currentTimeMillis() + snoozeMillis
                        val snoozedAlarm = alarm.copy(
                            triggerTimeMillis = nextTrigger,
                            isDismissed = false
                        )
                        db.alarmDao().updateAlarm(snoozedAlarm)
                        scheduler.scheduleAlarm(snoozedAlarm)
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Snoozed for ${alarm.snoozeDurationMinutes} mins", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            AlarmNotificationManager.ACTION_DISMISS_UPCOMING -> {
                val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
                if (alarmId != -1L) {
                    scheduler.cancelAlarm(alarmId)
                    AlarmNotificationManager.cancelUpcomingAlarmNotification(context, alarmId)
                    CoroutineScope(Dispatchers.IO).launch {
                        val alarm = db.alarmDao().getAlarmById(alarmId) ?: return@launch
                        if (alarm.scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS && alarm.repeatDays > 0) {
                            val nextTrigger = AlarmScheduler.computeNextTriggerTime(
                                hour = alarm.hour,
                                minute = alarm.minute,
                                scheduleType = alarm.scheduleType,
                                repeatDays = alarm.repeatDays,
                                specificDateMillis = null
                            )
                            val updated = alarm.copy(triggerTimeMillis = nextTrigger, isDismissed = false)
                            db.alarmDao().updateAlarm(updated)
                            scheduler.scheduleAlarm(updated)
                        } else {
                            db.alarmDao().setAlarmDismissed(alarmId, true)
                            db.alarmDao().setAlarmEnabled(alarmId, false)
                        }
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Upcoming alarm dismissed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            AlarmNotificationManager.ACTION_SCHEDULE_CALENDAR_PROMPT -> {
                val eventId = intent.getLongExtra(AlarmNotificationManager.EXTRA_EVENT_ID, -1L)
                val title = intent.getStringExtra(AlarmNotificationManager.EXTRA_EVENT_TITLE) ?: "Meeting"
                val startTime = intent.getLongExtra(AlarmNotificationManager.EXTRA_EVENT_START_TIME, 0L)
                val extraRepeatDays = intent.getIntExtra(AlarmNotificationManager.EXTRA_REPEAT_DAYS, 0)
                val offsetMinutes = intent.getIntExtra(AlarmNotificationManager.EXTRA_OFFSET_MINUTES, 10)

                if (eventId != -1L && startTime > 0L) {
                    AlarmNotificationManager.cancelMeetingPromptNotification(context, eventId)

                    CoroutineScope(Dispatchers.IO).launch {
                        val syncManager = (context.applicationContext as? com.example.SyncAlarmApplication)?.calendarSyncManager
                            ?: com.example.calendar.CalendarSyncManager(context)
                        val resolvedStartTime = syncManager.getLatestMeetingStartTime(eventId, startTime)
                        val resolvedRepeatDays = syncManager.getMeetingRepeatDays(eventId, resolvedStartTime, extraRepeatDays)
                        SettingsRepository(context).markMeetingPrompted(eventId, resolvedStartTime)

                        val existing = db.alarmDao().getAlarmByCalendarEventId(eventId)
                            ?: db.alarmDao().getAlarmByCalendarEventAndStartTime(eventId, resolvedStartTime)
                        val rawTriggerTime = resolvedStartTime - (offsetMinutes * 60 * 1000L)
                        val settings = SettingsRepository(context).settingsFlow.value

                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = rawTriggerTime }
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val minute = cal.get(java.util.Calendar.MINUTE)

                        val isRecurring = resolvedRepeatDays > 0
                        val adjustedRepeatDays = if (isRecurring) {
                            AlarmEntity.adjustRepeatDaysForOffset(
                                meetingRepeatDays = resolvedRepeatDays,
                                meetingStartMillis = resolvedStartTime,
                                triggerMillis = rawTriggerTime
                            )
                        } else 0

                        val scheduleType = if (isRecurring) {
                            AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS
                        } else {
                            AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT
                        }

                        val triggerTime = if (isRecurring) {
                            AlarmScheduler.computeNextTriggerTime(
                                hour = hour,
                                minute = minute,
                                scheduleType = scheduleType,
                                repeatDays = adjustedRepeatDays,
                                specificDateMillis = null
                            )
                        } else {
                            rawTriggerTime
                        }

                        val alarmToSave = existing?.copy(
                            isManual = false,
                            title = title,
                            hour = hour,
                            minute = minute,
                            meetingStartTime = resolvedStartTime,
                            specificDateMillis = if (isRecurring) null else triggerTime,
                            offsetMinutes = offsetMinutes,
                            triggerTimeMillis = triggerTime,
                            scheduleType = scheduleType,
                            repeatDays = adjustedRepeatDays,
                            isEnabled = true,
                            isDismissed = false
                        ) ?: AlarmEntity(
                            isManual = false,
                            calendarEventId = eventId,
                            title = title,
                            hour = hour,
                            minute = minute,
                            meetingStartTime = resolvedStartTime,
                            specificDateMillis = if (isRecurring) null else triggerTime,
                            offsetMinutes = offsetMinutes,
                            triggerTimeMillis = triggerTime,
                            scheduleType = scheduleType,
                            repeatDays = adjustedRepeatDays,
                            isEnabled = true,
                            volume = settings.defaultVolume,
                            toneTitle = settings.defaultToneTitle,
                            toneUri = settings.defaultToneUri,
                            snoozeDurationMinutes = settings.defaultSnoozeMinutes,
                            maxReAlarmRings = settings.maxReAlarmRings,
                            reAlarmGapMinutes = settings.reAlarmGapMinutes
                        )

                        val id = if (existing != null) {
                            db.alarmDao().updateAlarm(alarmToSave)
                            existing.id
                        } else {
                            db.alarmDao().insertAlarm(alarmToSave)
                        }

                        val savedAlarm = alarmToSave.copy(id = id)
                        scheduler.scheduleAlarm(savedAlarm)
                        syncManager.syncCalendarEvents()
                        launch(Dispatchers.Main) {
                            val summaryMsg = if (isRecurring) {
                                "Recurring alarm set for ${savedAlarm.getFormattedTime()} • ${savedAlarm.getRepeatDaysSummary()}"
                            } else {
                                val dateTimeStr = java.text.SimpleDateFormat("EEE, MMM d • h:mm a", java.util.Locale.getDefault())
                                    .format(java.util.Date(triggerTime))
                                "Alarm set for $dateTimeStr ($offsetMinutes min before $title)"
                            }
                            Toast.makeText(context, summaryMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            AlarmNotificationManager.ACTION_IGNORE_CALENDAR_PROMPT -> {
                val eventId = intent.getLongExtra(AlarmNotificationManager.EXTRA_EVENT_ID, -1L)
                if (eventId != -1L) {
                    AlarmNotificationManager.cancelMeetingPromptNotification(context, eventId)
                    SettingsRepository(context).markMeetingPrompted(eventId)
                }
            }

            AlarmNotificationManager.ACTION_REMOVE_DELETED_MEETING_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
                if (alarmId != -1L) {
                    AlarmNotificationManager.cancelMeetingDeletedPromptNotification(context, alarmId)
                    SettingsRepository(context).markDeletionPrompted(alarmId)
                    scheduler.cancelAlarm(alarmId)
                    CoroutineScope(Dispatchers.IO).launch {
                        val alarm = db.alarmDao().getAlarmById(alarmId)
                        db.alarmDao().deleteAlarmById(alarmId)
                        val syncManager = (context.applicationContext as? com.example.SyncAlarmApplication)?.calendarSyncManager
                            ?: com.example.calendar.CalendarSyncManager(context.applicationContext)
                        syncManager.syncCalendarEvents(forcePrompt = false)
                        launch(Dispatchers.Main) {
                            val titleStr = alarm?.title ?: "deleted meeting"
                            Toast.makeText(context, "Deleted alarm for $titleStr", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            AlarmNotificationManager.ACTION_KEEP_DELETED_MEETING_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
                if (alarmId != -1L) {
                    AlarmNotificationManager.cancelMeetingDeletedPromptNotification(context, alarmId)
                    SettingsRepository(context).markDeletionPrompted(alarmId)
                    CoroutineScope(Dispatchers.IO).launch {
                        // Detach from deleted calendar event so it remains as a standalone alarm
                        val alarm = db.alarmDao().getAlarmById(alarmId)
                        if (alarm != null) {
                            val preservedScheduleType = if (alarm.scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) {
                                AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS
                            } else {
                                AlarmEntity.SCHEDULE_TYPE_ONE_TIME
                            }
                            db.alarmDao().updateAlarm(
                                alarm.copy(
                                    isManual = true,
                                    calendarEventId = null,
                                    scheduleType = preservedScheduleType
                                )
                            )
                        }
                        val syncManager = (context.applicationContext as? com.example.SyncAlarmApplication)?.calendarSyncManager
                            ?: com.example.calendar.CalendarSyncManager(context.applicationContext)
                        syncManager.syncCalendarEvents(forcePrompt = false)
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "Alarm kept active", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun stopAudioService(context: Context) {
        val stopIntent = Intent(context, AlarmAudioService::class.java)
        context.stopService(stopIntent)
        androidx.core.app.NotificationManagerCompat.from(context).cancel(AlarmAudioService.NOTIFICATION_ID)
    }

    private suspend fun advanceOrDisableAlarm(db: AppDatabase, scheduler: AlarmScheduler, alarm: AlarmEntity) {
        if (alarm.scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS && alarm.repeatDays > 0) {
            val nextTrigger = AlarmScheduler.computeNextTriggerTime(
                hour = alarm.hour,
                minute = alarm.minute,
                scheduleType = alarm.scheduleType,
                repeatDays = alarm.repeatDays,
                specificDateMillis = null
            )
            val advanced = alarm.copy(
                triggerTimeMillis = nextTrigger,
                currentRingCount = 0,
                isDismissed = false
            )
            db.alarmDao().updateAlarm(advanced)
            scheduler.scheduleAlarm(advanced)
        } else {
            db.alarmDao().setAlarmEnabled(alarm.id, false)
        }
    }
}
