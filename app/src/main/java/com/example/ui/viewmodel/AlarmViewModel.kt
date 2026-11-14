package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncAlarmApplication
import com.example.alarm.AlarmSoundOption
import com.example.alarm.AlarmSoundSynthesizer
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarMeeting
import com.example.data.repository.AlarmRepository
import com.example.data.repository.AppSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmRepo = AlarmRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val calendarSyncManager = (application as SyncAlarmApplication).calendarSyncManager

    val alarms: StateFlow<List<AlarmEntity>> = alarmRepo.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow

    val meetings: StateFlow<List<CalendarMeeting>> = calendarSyncManager.meetingsFlow

    val deletedMeetingAlarms: StateFlow<List<AlarmEntity>> = calendarSyncManager.deletedMeetingAlarmsFlow

    private val _soundOptions = MutableStateFlow<List<AlarmSoundOption>>(emptyList())
    val soundOptions: StateFlow<List<AlarmSoundOption>> = _soundOptions.asStateFlow()

    // Derived next upcoming active alarm for the Hero Card
    val nextUpcomingAlarm: StateFlow<AlarmEntity?> = alarms.combine(settings) { list, _ ->
        val now = System.currentTimeMillis()
        list.filter { it.isEnabled && !it.isDismissed && it.triggerTimeMillis > now }
            .minByOrNull { it.triggerTimeMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isCalendarSyncing = MutableStateFlow(false)
    val isCalendarSyncing: StateFlow<Boolean> = _isCalendarSyncing.asStateFlow()

    private var previewPlayer: MediaPlayer? = null

    init {
        refreshSoundOptions()
    }

    fun refreshSoundOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val customTones = settingsRepo.getCustomTones()
            val allOptions = AlarmSoundSynthesizer.getAllSoundOptions(app, customTones)
            _soundOptions.value = allOptions
        }
    }

    fun importCustomAudio(sourceUri: Uri, onImported: (AlarmSoundOption) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val imported = AlarmSoundSynthesizer.importCustomAudioFile(app, sourceUri)
            if (imported != null) {
                settingsRepo.addCustomTone(imported)
                val customTones = settingsRepo.getCustomTones()
                _soundOptions.value = AlarmSoundSynthesizer.getAllSoundOptions(app, customTones)
                withContext(Dispatchers.Main) {
                    onImported(imported)
                }
            }
        }
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmRepo.saveAlarm(alarm)
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepo.toggleAlarm(alarm, enabled)
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun dismissUpcomingAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmRepo.dismissUpcomingAlarm(alarm)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            com.example.alarm.AlarmNotificationManager.cancelMeetingDeletedPromptNotification(getApplication(), alarm.id)
            settingsRepo.markDeletionPrompted(alarm.id)
            alarmRepo.deleteAlarm(alarm)
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun confirmDeleteMeetingAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            com.example.alarm.AlarmNotificationManager.cancelMeetingDeletedPromptNotification(getApplication(), alarm.id)
            settingsRepo.markDeletionPrompted(alarm.id)
            alarmRepo.deleteAlarm(alarm)
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun keepDeletedMeetingAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            com.example.alarm.AlarmNotificationManager.cancelMeetingDeletedPromptNotification(getApplication(), alarm.id)
            settingsRepo.markDeletionPrompted(alarm.id)
            val preservedScheduleType = if (alarm.scheduleType == AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) {
                AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS
            } else {
                AlarmEntity.SCHEDULE_TYPE_ONE_TIME
            }
            alarmRepo.saveAlarm(
                alarm.copy(
                    isManual = true,
                    calendarEventId = null,
                    scheduleType = preservedScheduleType
                )
            )
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun scheduleMeetingAlarm(meeting: CalendarMeeting, offsetMinutes: Int) {
        viewModelScope.launch {
            alarmRepo.scheduleMeetingAlarm(meeting, offsetMinutes)
            calendarSyncManager.syncCalendarEvents()
        }
    }

    fun syncCalendar() {
        viewModelScope.launch {
            _isCalendarSyncing.value = true
            try {
                calendarSyncManager.syncCalendarEvents(forcePrompt = true)
            } finally {
                _isCalendarSyncing.value = false
            }
        }
    }

    fun injectSampleMeeting(title: String, delayMinutes: Int) {
        viewModelScope.launch {
            calendarSyncManager.injectSampleMeeting(title, delayMinutes)
        }
    }

    fun deleteMeeting(meeting: CalendarMeeting) {
        viewModelScope.launch {
            calendarSyncManager.onMeetingDeleted(meeting.id)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsRepo.updateSettings(newSettings)
    }

    fun playTonePreview(context: Context, toneTitle: String?, toneUri: String? = null, volume: Float) {
        stopTonePreview()
        try {
            val resolvedTitle = if (toneTitle.isNullOrBlank()) {
                settings.value.defaultToneTitle
            } else {
                toneTitle
            }
            previewPlayer = MediaPlayer().apply {
                AlarmSoundSynthesizer.setMediaPlayerDataSource(
                    context = context,
                    mediaPlayer = this,
                    toneTitle = resolvedTitle,
                    toneUri = toneUri
                )
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val vol = volume.coerceIn(0.1f, 1.0f)
                setVolume(vol, vol)
                isLooping = false
                setOnCompletionListener {
                    stopTonePreview()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmViewModel", "Error previewing sound", e)
        }
    }

    fun stopTonePreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopTonePreview()
    }
}
