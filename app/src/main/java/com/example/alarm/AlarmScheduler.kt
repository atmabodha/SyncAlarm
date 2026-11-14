package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.model.AlarmEntity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return
        }

        val triggerTime = if (alarm.triggerTimeMillis > System.currentTimeMillis()) {
            alarm.triggerTimeMillis
        } else {
            computeNextTriggerTime(
                hour = alarm.hour,
                minute = alarm.minute,
                scheduleType = alarm.scheduleType,
                repeatDays = alarm.repeatDays,
                specificDateMillis = alarm.specificDateMillis,
                meetingStartTime = alarm.meetingStartTime,
                offsetMinutes = alarm.offsetMinutes
            )
        }

        // If trigger time is still in the past, cannot schedule
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w("AlarmScheduler", "Calculated trigger time in past: $triggerTime")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmNotificationManager.ACTION_TRIGGER_ALARM
            putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show showWhenLocked intent to open app when tapped from lockscreen
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }

            // Post upcoming alarm notification if within 2 hours
            val twoHoursMillis = 2 * 60 * 60 * 1000L
            if (triggerTime - System.currentTimeMillis() <= twoHoursMillis && !alarm.isDismissed) {
                AlarmNotificationManager.postUpcomingAlarmNotification(context, alarm.copy(triggerTimeMillis = triggerTime))
            }

            Log.d("AlarmScheduler", "Alarm scheduled for ${alarm.title} at $triggerTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Permission error scheduling exact alarm", e)
        }
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmNotificationManager.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        AlarmNotificationManager.cancelUpcomingAlarmNotification(context, alarmId)
    }

    companion object {
        fun computeNextTriggerTime(
            hour: Int,
            minute: Int,
            scheduleType: String,
            repeatDays: Int,
            specificDateMillis: Long?,
            meetingStartTime: Long? = null,
            offsetMinutes: Int = 0
        ): Long {
            // Calendar event alarm
            if (scheduleType == AlarmEntity.SCHEDULE_TYPE_CALENDAR_EVENT) {
                if (specificDateMillis != null) {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = specificDateMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return cal.timeInMillis
                }
                if (meetingStartTime != null) {
                    return meetingStartTime - (offsetMinutes * 60 * 1000L)
                }
            }

            // Specific future date alarm
            if (scheduleType == AlarmEntity.SCHEDULE_TYPE_SPECIFIC_DATE && specificDateMillis != null) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = specificDateMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }

            // Recurring days alarm (e.g. Mon, Thu)
            if (scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS && repeatDays > 0) {
                val now = Calendar.getInstance()
                // If linked to a Google Calendar recurring series, meetingStartTime is the next non-deleted instance.
                // Do not schedule an alarm earlier than (meetingStartTime - offsetMinutes) when a single occurrence was deleted.
                val minValidTriggerMillis = if (meetingStartTime != null && meetingStartTime > now.timeInMillis) {
                    meetingStartTime - (offsetMinutes * 60 * 1000L)
                } else {
                    0L
                }
                for (dayOffset in 0..30) {
                    val candidate = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, dayOffset)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (candidate.timeInMillis > now.timeInMillis &&
                        (minValidTriggerMillis <= now.timeInMillis || candidate.timeInMillis >= minValidTriggerMillis - 60_000L)
                    ) {
                        val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                        val bit = when (dayOfWeek) {
                            Calendar.MONDAY -> AlarmEntity.DAY_MON
                            Calendar.TUESDAY -> AlarmEntity.DAY_TUE
                            Calendar.WEDNESDAY -> AlarmEntity.DAY_WED
                            Calendar.THURSDAY -> AlarmEntity.DAY_THU
                            Calendar.FRIDAY -> AlarmEntity.DAY_FRI
                            Calendar.SATURDAY -> AlarmEntity.DAY_SAT
                            Calendar.SUNDAY -> AlarmEntity.DAY_SUN
                            else -> 0
                        }
                        if ((repeatDays and bit) != 0) {
                            return candidate.timeInMillis
                        }
                    }
                }
                if (minValidTriggerMillis > now.timeInMillis) {
                    return minValidTriggerMillis
                }
            }

            // Standard one-time next occurrence (today or tomorrow)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
