package com.example.data.repository

import android.content.Context
import com.example.alarm.AlarmNotificationManager
import com.example.alarm.AlarmScheduler
import com.example.data.local.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarMeeting
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AlarmRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.alarmDao()
    private val scheduler = AlarmScheduler(context)
    private val settingsRepo = SettingsRepository(context)

    val allAlarms: Flow<List<AlarmEntity>> = dao.getAllAlarmsFlow()
    val activeUpcomingAlarms: Flow<List<AlarmEntity>> = dao.getActiveUpcomingAlarmsFlow()

    suspend fun saveAlarm(alarm: AlarmEntity): Long {
        val triggerTime = AlarmScheduler.computeNextTriggerTime(
            hour = alarm.hour,
            minute = alarm.minute,
            scheduleType = alarm.scheduleType,
            repeatDays = alarm.repeatDays,
            specificDateMillis = alarm.specificDateMillis,
            meetingStartTime = alarm.meetingStartTime,
            offsetMinutes = alarm.offsetMinutes
        )

        val alarmToSave = alarm.copy(
            triggerTimeMillis = triggerTime,
            isEnabled = true,
            isDismissed = false,
            currentRingCount = 0
        )

        val id = if (alarm.id != 0L) {
            dao.updateAlarm(alarmToSave)
            alarm.id
        } else {
            dao.insertAlarm(alarmToSave)
        }

        val currentDefaultSnooze = settingsRepo.settingsFlow.value.defaultSnoozeMinutes
        settingsRepo.setCustomSnooze(id, alarmToSave.snoozeDurationMinutes != currentDefaultSnooze)

        scheduler.scheduleAlarm(alarmToSave.copy(id = id))
        return id
    }

    suspend fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        if (!enabled) {
            scheduler.cancelAlarm(alarm.id)
            dao.setAlarmEnabled(alarm.id, false)
        } else {
            val triggerTime = AlarmScheduler.computeNextTriggerTime(
                hour = alarm.hour,
                minute = alarm.minute,
                scheduleType = alarm.scheduleType,
                repeatDays = alarm.repeatDays,
                specificDateMillis = alarm.specificDateMillis,
                meetingStartTime = alarm.meetingStartTime,
                offsetMinutes = alarm.offsetMinutes
            )
            val updated = alarm.copy(
                triggerTimeMillis = triggerTime,
                isEnabled = true,
                isDismissed = false,
                currentRingCount = 0
            )
            dao.updateAlarm(updated)
            scheduler.scheduleAlarm(updated)
        }
    }

    suspend fun dismissUpcomingAlarm(alarm: AlarmEntity) {
        scheduler.cancelAlarm(alarm.id)
        AlarmNotificationManager.cancelUpcomingAlarmNotification(context, alarm.id)

        if (alarm.scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS && alarm.repeatDays > 0) {
            val nextTrigger = AlarmScheduler.computeNextTriggerTime(
                hour = alarm.hour,
                minute = alarm.minute,
                scheduleType = alarm.scheduleType,
                repeatDays = alarm.repeatDays,
                specificDateMillis = null
            )
            val updated = alarm.copy(
                triggerTimeMillis = nextTrigger,
                isDismissed = false,
                currentRingCount = 0
            )
            dao.updateAlarm(updated)
            scheduler.scheduleAlarm(updated)
        } else {
            dao.setAlarmDismissed(alarm.id, true)
            dao.setAlarmEnabled(alarm.id, false)
        }
    }

    suspend fun deleteAlarm(alarm: AlarmEntity) {
        scheduler.cancelAlarm(alarm.id)
        settingsRepo.setCustomSnooze(alarm.id, false)
        dao.deleteAlarm(alarm)
    }

    suspend fun scheduleMeetingAlarm(meeting: CalendarMeeting, offsetMinutes: Int): Long {
        val settings = settingsRepo.settingsFlow.value
        val rawTriggerTime = meeting.startTimeMillis - (offsetMinutes * 60 * 1000L)

        val cal = Calendar.getInstance().apply { timeInMillis = rawTriggerTime }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val isRecurring = meeting.repeatDays > 0
        val adjustedRepeatDays = if (isRecurring) {
            AlarmEntity.adjustRepeatDaysForOffset(
                meetingRepeatDays = meeting.repeatDays,
                meetingStartMillis = meeting.startTimeMillis,
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

        val existing = dao.getAlarmByCalendarEventId(meeting.id)
            ?: dao.getAlarmByCalendarEventAndStartTime(meeting.id, meeting.startTimeMillis)

        val alarm = existing?.copy(
            isManual = false,
            title = meeting.title,
            hour = hour,
            minute = minute,
            meetingStartTime = meeting.startTimeMillis,
            specificDateMillis = if (isRecurring) null else triggerTime,
            offsetMinutes = offsetMinutes,
            triggerTimeMillis = triggerTime,
            scheduleType = scheduleType,
            repeatDays = adjustedRepeatDays,
            isEnabled = true,
            isDismissed = false
        ) ?: AlarmEntity(
            isManual = false,
            calendarEventId = meeting.id,
            title = meeting.title,
            hour = hour,
            minute = minute,
            meetingStartTime = meeting.startTimeMillis,
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
            dao.updateAlarm(alarm)
            existing.id
        } else {
            dao.insertAlarm(alarm)
        }

        scheduler.scheduleAlarm(alarm.copy(id = id))
        return id
    }

    suspend fun syncDefaultSnoozeOnStartupIfNeeded() {
        if (!settingsRepo.isInitialSnoozeMigrationDone()) {
            val defaultSnooze = settingsRepo.settingsFlow.value.defaultSnoozeMinutes
            val all = dao.getAllAlarmsList()
            all.forEach { alarm ->
                if (!settingsRepo.hasCustomSnooze(alarm.id) && alarm.snoozeDurationMinutes != defaultSnooze) {
                    dao.updateAlarm(alarm.copy(snoozeDurationMinutes = defaultSnooze))
                }
            }
            settingsRepo.markInitialSnoozeMigrationDone()
        }
    }

    suspend fun applySettingsToExistingAlarms(oldSettings: AppSettings, newSettings: AppSettings) {
        if (oldSettings.defaultSnoozeMinutes != newSettings.defaultSnoozeMinutes) {
            val all = dao.getAllAlarmsList()
            all.forEach { alarm ->
                if (!settingsRepo.hasCustomSnooze(alarm.id)) {
                    dao.updateAlarm(alarm.copy(snoozeDurationMinutes = newSettings.defaultSnoozeMinutes))
                }
            }
        }
        if (oldSettings.maxReAlarmRings != newSettings.maxReAlarmRings) {
            dao.updateAllAlarmsMaxReRings(newSettings.maxReAlarmRings)
        }
        if (oldSettings.reAlarmGapMinutes != newSettings.reAlarmGapMinutes) {
            dao.updateAllAlarmsReAlarmGap(newSettings.reAlarmGapMinutes)
        }
        if (oldSettings.defaultToneTitle != newSettings.defaultToneTitle ||
            oldSettings.defaultToneUri != newSettings.defaultToneUri
        ) {
            dao.updateAllAlarmsTone(newSettings.defaultToneTitle, newSettings.defaultToneUri)
        }
        if (oldSettings.defaultVolume != newSettings.defaultVolume) {
            dao.updateAllAlarmsVolume(newSettings.defaultVolume)
        }
        if (oldSettings.vibrateEnabled != newSettings.vibrateEnabled) {
            dao.updateAllAlarmsVibrate(newSettings.vibrateEnabled)
        }
    }
}
