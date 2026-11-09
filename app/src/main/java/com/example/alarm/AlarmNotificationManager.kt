package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarMeeting

object AlarmNotificationManager {

    const val CHANNEL_RINGING_ID = "sync_alarm_ringing_channel"
    const val CHANNEL_UPCOMING_ID = "sync_alarm_upcoming_channel"
    const val CHANNEL_PROMPTS_ID = "sync_alarm_calendar_prompts_channel"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_EVENT_TITLE = "extra_event_title"
    const val EXTRA_EVENT_START_TIME = "extra_event_start_time"
    const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"
    const val EXTRA_REPEAT_DAYS = "extra_repeat_days"

    const val ACTION_TRIGGER_ALARM = "com.aistudio.syncalarm.ACTION_TRIGGER_ALARM"
    const val ACTION_DISMISS_ALARM = "com.aistudio.syncalarm.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.aistudio.syncalarm.ACTION_SNOOZE_ALARM"
    const val ACTION_DISMISS_UPCOMING = "com.aistudio.syncalarm.ACTION_DISMISS_UPCOMING"
    const val ACTION_SCHEDULE_CALENDAR_PROMPT = "com.aistudio.syncalarm.ACTION_SCHEDULE_CALENDAR_PROMPT"
    const val ACTION_IGNORE_CALENDAR_PROMPT = "com.aistudio.syncalarm.ACTION_IGNORE_CALENDAR_PROMPT"
    const val ACTION_REMOVE_DELETED_MEETING_ALARM = "com.aistudio.syncalarm.ACTION_REMOVE_DELETED_MEETING_ALARM"
    const val ACTION_KEEP_DELETED_MEETING_ALARM = "com.aistudio.syncalarm.ACTION_KEEP_DELETED_MEETING_ALARM"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Ringing Channel
            val ringingChannel = NotificationChannel(
                CHANNEL_RINGING_ID,
                "Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent audible alarms and full-screen notifications"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
            }

            // 2. Upcoming Alarms Channel
            val upcomingChannel = NotificationChannel(
                CHANNEL_UPCOMING_ID,
                "Upcoming Alarms",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows upcoming alarms with quick dismiss options"
                setShowBadge(false)
            }

            // 3. Calendar Meeting Prompts Channel
            val promptsChannel = NotificationChannel(
                CHANNEL_PROMPTS_ID,
                "Calendar Meeting Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interactive notifications to schedule or remove alarms for detected meetings"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(ringingChannel, upcomingChannel, promptsChannel)
            )
        }
    }

    /**
     * Shows notification prompting the user to add an alarm for a new meeting:
     * Offers 2 options: 5 mins before or 10 mins before (plus Skip).
     */
    fun postMeetingPromptNotification(context: Context, meeting: CalendarMeeting) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (20000 + (meeting.id % 10000)).toInt()

        // Action 1: 5 Mins Before
        val intent5m = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_SCHEDULE_CALENDAR_PROMPT
            putExtra(EXTRA_EVENT_ID, meeting.id)
            putExtra(EXTRA_EVENT_TITLE, meeting.title)
            putExtra(EXTRA_EVENT_START_TIME, meeting.startTimeMillis)
            putExtra(EXTRA_REPEAT_DAYS, meeting.repeatDays)
            putExtra(EXTRA_OFFSET_MINUTES, 5)
        }
        val pIntent5m = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 5,
            intent5m,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: 10 Mins Before
        val intent10m = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_SCHEDULE_CALENDAR_PROMPT
            putExtra(EXTRA_EVENT_ID, meeting.id)
            putExtra(EXTRA_EVENT_TITLE, meeting.title)
            putExtra(EXTRA_EVENT_START_TIME, meeting.startTimeMillis)
            putExtra(EXTRA_REPEAT_DAYS, meeting.repeatDays)
            putExtra(EXTRA_OFFSET_MINUTES, 10)
        }
        val pIntent10m = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 10,
            intent10m,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Ignore / Skip
        val intentSkip = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_IGNORE_CALENDAR_PROMPT
            putExtra(EXTRA_EVENT_ID, meeting.id)
        }
        val pIntentSkip = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 99,
            intentSkip,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content intent to open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pContentIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recurrenceInfo = if (meeting.isRecurring()) " • Repeats ${meeting.getRecurrenceSummary()}" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_PROMPTS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("New Meeting: ${meeting.title}")
            .setContentText("Starts ${meeting.getFormattedStartTime()}$recurrenceInfo. Set an alarm?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Meeting: ${meeting.title}\nStarts: ${meeting.getFormattedStartTime()}$recurrenceInfo\nChoose alarm gap before meeting:"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pContentIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "5 Mins Before", pIntent5m)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "10 Mins Before", pIntent10m)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Skip", pIntentSkip)
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (_: SecurityException) {
            // Handled when permission is not granted yet
        }
    }

    /**
     * Shows notification asking the user if an alarm should also be removed when its
     * corresponding meeting has been deleted from Google Calendar.
     */
    fun postMeetingDeletedPromptNotification(context: Context, alarm: AlarmEntity) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (30000 + (alarm.id % 10000)).toInt()

        val removeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_REMOVE_DELETED_MEETING_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pRemoveIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 1,
            removeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val keepIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_KEEP_DELETED_MEETING_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pKeepIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            keepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pContentIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PROMPTS_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Meeting Deleted: ${alarm.title}")
            .setContentText("Remove its ${alarm.getFormattedTime()} alarm as well?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "\"${alarm.title}\" was deleted from Google Calendar.\nWould you like to remove the scheduled ${alarm.getFormattedTime()} alarm?"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pContentIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_delete, "Remove Alarm", pRemoveIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Keep Alarm", pKeepIntent)
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Shows persistent Upcoming Alarm notification with a "Dismiss" action button.
     */
    fun postUpcomingAlarmNotification(context: Context, alarm: AlarmEntity) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (10000 + (alarm.id % 10000)).toInt()

        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_DISMISS_UPCOMING
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pDismissIntent = PendingIntent.getBroadcast(
            context,
            notifId + 500,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pContentIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeStr = java.text.SimpleDateFormat("EEE, MMM d • h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(alarm.triggerTimeMillis))

        val notification = NotificationCompat.Builder(context, CHANNEL_UPCOMING_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upcoming Alarm: ${alarm.title}")
            .setContentText("Alarm set for $triggerTimeStr")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pContentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", pDismissIntent)
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelUpcomingAlarmNotification(context: Context, alarmId: Long) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (10000 + (alarmId % 10000)).toInt()
        notifManager.cancel(notifId)
    }

    fun cancelMeetingPromptNotification(context: Context, eventId: Long) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (20000 + (eventId % 10000)).toInt()
        notifManager.cancel(notifId)
    }

    fun cancelMeetingDeletedPromptNotification(context: Context, alarmId: Long) {
        val notifManager = NotificationManagerCompat.from(context)
        val notifId = (30000 + (alarmId % 10000)).toInt()
        notifManager.cancel(notifId)
    }

    /**
     * Builds the active ringing notification with full screen intent, Snooze & Dismiss buttons.
     */
    fun buildRingingNotification(context: Context, alarm: AlarmEntity): Notification {
        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pDismissIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt() + 1000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pSnoozeIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt() + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_RINGING_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ALARM: ${alarm.title}")
            .setContentText("Ringing now • Tap to open")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (${alarm.snoozeDurationMinutes}m)", pSnoozeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", pDismissIntent)
            .build()
    }
}
