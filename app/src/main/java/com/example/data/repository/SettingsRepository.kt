package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val defaultLeadTimeMinutes: Int = 10,
    val defaultSnoozeMinutes: Int = 10,
    val maxReAlarmRings: Int = 3,
    val reAlarmGapMinutes: Int = 5,
    val autoSilenceSeconds: Int = 60,
    val defaultVolume: Float = 0.85f,
    val defaultToneTitle: String = "Gentle Digital Chime",
    val defaultToneUri: String? = null,
    val vibrateEnabled: Boolean = true,
    val promptCalendarMeetings: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_alarm_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            defaultLeadTimeMinutes = prefs.getInt("default_lead_time", 10),
            defaultSnoozeMinutes = prefs.getInt("default_snooze_minutes", 10),
            maxReAlarmRings = prefs.getInt("max_re_alarm_rings", 3),
            reAlarmGapMinutes = prefs.getInt("re_alarm_gap_minutes", 5),
            autoSilenceSeconds = prefs.getInt("auto_silence_seconds", 60),
            defaultVolume = prefs.getFloat("default_volume", 0.85f),
            defaultToneTitle = prefs.getString("default_tone_title", "Gentle Digital Chime") ?: "Gentle Digital Chime",
            defaultToneUri = prefs.getString("default_tone_uri", null),
            vibrateEnabled = prefs.getBoolean("vibrate_enabled", true),
            promptCalendarMeetings = prefs.getBoolean("prompt_calendar_meetings", true)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putInt("default_lead_time", newSettings.defaultLeadTimeMinutes)
            .putInt("default_snooze_minutes", newSettings.defaultSnoozeMinutes)
            .putInt("max_re_alarm_rings", newSettings.maxReAlarmRings)
            .putInt("re_alarm_gap_minutes", newSettings.reAlarmGapMinutes)
            .putInt("auto_silence_seconds", newSettings.autoSilenceSeconds)
            .putFloat("default_volume", newSettings.defaultVolume)
            .putString("default_tone_title", newSettings.defaultToneTitle)
            .putString("default_tone_uri", newSettings.defaultToneUri)
            .putBoolean("vibrate_enabled", newSettings.vibrateEnabled)
            .putBoolean("prompt_calendar_meetings", newSettings.promptCalendarMeetings)
            .apply()
        _settingsFlow.value = newSettings
    }

    fun isMeetingPrompted(eventId: Long, startTimeMillis: Long = -1L, title: String? = null): Boolean {
        val wasPrompted = prefs.getBoolean("prompted_event_v3_$eventId", false)
        if (!wasPrompted) return false
        if (startTimeMillis <= 0L) return true
        val promptedStart = prefs.getLong("prompted_event_start_v3_$eventId", -1L)
        if (promptedStart <= 0L) return true
        val sameTime = kotlin.math.abs(promptedStart - startTimeMillis) < 60_000L
        if (!sameTime) return false
        if (!title.isNullOrBlank()) {
            val prevTitle = prefs.getString("prompted_event_title_v3_$eventId", null)
            if (prevTitle == "Meeting" && title != "Meeting") {
                return false
            }
        }
        return true
    }

    fun markMeetingPrompted(eventId: Long, startTimeMillis: Long = -1L, title: String? = null) {
        val editor = prefs.edit().putBoolean("prompted_event_v3_$eventId", true)
        if (startTimeMillis > 0L) {
            editor.putLong("prompted_event_start_v3_$eventId", startTimeMillis)
        }
        if (!title.isNullOrBlank()) {
            editor.putString("prompted_event_title_v3_$eventId", title)
        }
        editor.apply()
    }

    fun clearMeetingPrompted(eventId: Long) {
        prefs.edit()
            .remove("prompted_event_v3_$eventId")
            .remove("prompted_event_start_v3_$eventId")
            .remove("prompted_event_title_v3_$eventId")
            .apply()
    }

    fun isDeletionPrompted(alarmId: Long): Boolean {
        return prefs.getBoolean("prompted_deletion_alarm_$alarmId", false)
    }

    fun markDeletionPrompted(alarmId: Long) {
        prefs.edit().putBoolean("prompted_deletion_alarm_$alarmId", true).apply()
    }

    fun getCustomTones(): List<com.example.alarm.AlarmSoundOption> {
        val raw = prefs.getString("custom_mp3_tones", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 2) {
                com.example.alarm.AlarmSoundOption(
                    title = parts[0],
                    uri = parts[1],
                    category = "Custom MP3"
                )
            } else null
        }
    }

    fun addCustomTone(option: com.example.alarm.AlarmSoundOption) {
        val current = getCustomTones().toMutableList()
        current.removeAll { it.uri == option.uri || it.title == option.title }
        current.add(0, option)
        val serialized = current.joinToString("\n") { "${it.title}||${it.uri}" }
        prefs.edit().putString("custom_mp3_tones", serialized).apply()
    }

    fun hasCustomSnooze(alarmId: Long): Boolean {
        return prefs.getBoolean("custom_snooze_alarm_$alarmId", false)
    }

    fun setCustomSnooze(alarmId: Long, isCustom: Boolean) {
        prefs.edit().putBoolean("custom_snooze_alarm_$alarmId", isCustom).apply()
    }

    fun isInitialSnoozeMigrationDone(): Boolean {
        return prefs.getBoolean("initial_snooze_sync_done", false)
    }

    fun markInitialSnoozeMigrationDone() {
        prefs.edit().putBoolean("initial_snooze_sync_done", true).apply()
    }
}
