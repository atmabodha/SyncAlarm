package com.example.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmAudioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var autoSilenceTimer: CountDownTimer? = null
    private var currentAlarm: AlarmEntity? = null
    private var startJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmNotificationManager.EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Stop any existing playback/timer before starting new alarm audio
        startJob?.cancel()
        stopAudio()

        // Acquire CPU and Screen WakeLocks so the screen turns on immediately when locked
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncAlarm:AudioServiceLock").apply {
            acquire(5 * 60 * 1000L) // 5 minutes max
        }
        @Suppress("DEPRECATION")
        screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "SyncAlarm:ScreenWakeLock"
        ).apply {
            acquire(60 * 1000L)
        }

        startJob = CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)
            val alarm = db.alarmDao().getAlarmById(alarmId)
            if (alarm == null) {
                stopSelf()
                return@launch
            }

            currentAlarm = alarm

            // Ensure channels are up to date (and legacy noisy channel is deleted)
            AlarmNotificationManager.createNotificationChannels(applicationContext)

            // Start foreground with high-priority full-screen alarm notification
            val notification = AlarmNotificationManager.buildRingingNotification(applicationContext, alarm)
            startForeground(NOTIFICATION_ID, notification)

            // Start audio, vibration, and launch full-screen lockscreen activity on Main thread
            launch(Dispatchers.Main) {
                startAudioAndVibration(alarm)
                startAutoSilenceTimer(alarm)
                try {
                    val activityIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(AlarmNotificationManager.EXTRA_ALARM_ID, alarm.id)
                    }
                    startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.w("AlarmAudioService", "Full-screen intent handled via NotificationManager: ${e.message}")
                }
            }
        }

        return START_NOT_STICKY
    }

    private var audioModeListener: AudioManager.OnModeChangedListener? = null

    private fun isDeviceInCallOrVoip(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val mode = audioManager.mode
        return mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_CALL_SCREENING
    }

    private fun registerCallModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioModeListener == null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val listener = AudioManager.OnModeChangedListener { mode ->
                if (mode == AudioManager.MODE_IN_CALL ||
                    mode == AudioManager.MODE_IN_COMMUNICATION ||
                    mode == AudioManager.MODE_RINGTONE ||
                    mode == AudioManager.MODE_CALL_SCREENING
                ) {
                    Log.d("AlarmAudioService", "Call or VoIP session detected (mode=$mode), silencing alarm audio to avoid dropping call")
                    try {
                        mediaPlayer?.pause()
                        vibrator?.cancel()
                    } catch (_: Exception) {}
                }
            }
            try {
                audioManager.addOnModeChangedListener(mainExecutor, listener)
                audioModeListener = listener
            } catch (e: Exception) {
                Log.w("AlarmAudioService", "Could not register OnModeChangedListener: ${e.message}")
            }
        }
    }

    private fun unregisterCallModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = audioModeListener ?: return
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                audioManager?.removeOnModeChangedListener(listener)
            } catch (_: Exception) {}
            audioModeListener = null
        }
    }

    private fun startAudioAndVibration(alarm: AlarmEntity) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null

        registerCallModeListener()

        // If the user is currently in a WhatsApp/VoIP or cellular call, do not hijack audio with USAGE_ALARM
        if (isDeviceInCallOrVoip()) {
            Log.d("AlarmAudioService", "Device is in an active call/VoIP session; skipping loud USAGE_ALARM playback")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                AlarmSoundSynthesizer.setMediaPlayerDataSource(
                    applicationContext,
                    this,
                    alarm.toneTitle,
                    alarm.toneUri
                )
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                val vol = alarm.volume.coerceIn(0.1f, 1.0f)
                setVolume(vol, vol)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmAudioService", "Error playing alarm audio, falling back to system default", e)
            try {
                mediaPlayer = MediaPlayer.create(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
                    isLooping = true
                    start()
                }
            } catch (ex: Exception) {
                Log.e("AlarmAudioService", "Fallback audio failed", ex)
            }
        }

        if (alarm.vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    /**
     * Auto-silences after configured duration (e.g. 60s).
     * If no user action is taken, checks maxReAlarmRings and schedules re-alarm after reAlarmGapMinutes!
     */
    private fun startAutoSilenceTimer(alarm: AlarmEntity) {
        val settings = SettingsRepository(applicationContext).settingsFlow.value
        val timeoutMillis = (settings.autoSilenceSeconds * 1000L).coerceAtLeast(15000L)

        autoSilenceTimer = object : CountDownTimer(timeoutMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // If an incoming WhatsApp / VoIP or phone call arrives while the alarm is ringing, yield audio immediately
                if (isDeviceInCallOrVoip()) {
                    try {
                        if (mediaPlayer?.isPlaying == true) {
                            mediaPlayer?.pause()
                        }
                        vibrator?.cancel()
                    } catch (_: Exception) {}
                }
            }

            override fun onFinish() {
                Log.d("AlarmAudioService", "Alarm auto-silenced with no user action.")
                handleAutoReAlarm(alarm)
            }
        }.start()
    }

    private fun handleAutoReAlarm(alarm: AlarmEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)
            val updatedRingCount = alarm.currentRingCount + 1

            if (updatedRingCount < alarm.maxReAlarmRings) {
                val gapMillis = alarm.reAlarmGapMinutes * 60 * 1000L
                val nextTrigger = System.currentTimeMillis() + gapMillis

                val rescheduledAlarm = alarm.copy(
                    currentRingCount = updatedRingCount,
                    triggerTimeMillis = nextTrigger,
                    isDismissed = false
                )
                db.alarmDao().updateAlarm(rescheduledAlarm)
                AlarmScheduler(applicationContext).scheduleAlarm(rescheduledAlarm)
                Log.d("AlarmAudioService", "Re-alarm scheduled (${updatedRingCount}/${alarm.maxReAlarmRings}) in ${alarm.reAlarmGapMinutes}m")
            } else {
                // Reached max re-alarm rings; reset counter and advance recurring or disable one-time
                resetOrAdvanceAlarm(db, alarm)
            }

            stopAudio()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    private suspend fun resetOrAdvanceAlarm(db: AppDatabase, alarm: AlarmEntity) {
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
            AlarmScheduler(applicationContext).scheduleAlarm(advanced)
        } else {
            // Disable one-time / calendar alarm
            db.alarmDao().setAlarmEnabled(alarm.id, false)
        }
    }

    private fun stopAudio() {
        try {
            unregisterCallModeListener()
            autoSilenceTimer?.cancel()
            autoSilenceTimer = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
            vibrator = null
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            screenWakeLock?.let {
                if (it.isHeld) it.release()
            }
            screenWakeLock = null
            androidx.core.app.NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("AlarmAudioService", "Error stopping audio", e)
        }
    }

    override fun onDestroy() {
        startJob?.cancel()
        stopAudio()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 9999
    }
}
