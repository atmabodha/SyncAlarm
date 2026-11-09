package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L)
        Log.d("AlarmReceiver", "Received alarm trigger for ID: $alarmId")

        if (alarmId == -1L) return

        // 1. Start Audio Foreground Service
        val serviceIntent = Intent(context, AlarmAudioService::class.java).apply {
            putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarmId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Launch full-screen ringing activity
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarmId)
        }
        context.startActivity(activityIntent)
    }
}
