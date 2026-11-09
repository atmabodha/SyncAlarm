package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val isManual: Boolean = true,
    val calendarEventId: Long? = null,
    val title: String = "Alarm",
    val hour: Int = 8,
    val minute: Int = 0,
    val triggerTimeMillis: Long = 0L,
    val scheduleType: String = SCHEDULE_TYPE_ONE_TIME,
    val repeatDays: Int = 0, // Bitmask: 1=Mon, 2=Tue, 4=Wed, 8=Thu, 16=Fri, 32=Sat, 64=Sun
    val specificDateMillis: Long? = null,
    val meetingStartTime: Long? = null,
    val offsetMinutes: Int = 0, // 0 for manual, 5 or 10 for calendar meetings
    val isEnabled: Boolean = true,
    val isDismissed: Boolean = false, // If user dismissed the upcoming occurrence
    val volume: Float = 0.85f,
    val toneUri: String? = null,
    val toneTitle: String = "Default Chime",
    val vibrate: Boolean = true,
    val snoozeDurationMinutes: Int = 10,
    val maxReAlarmRings: Int = 3,
    val reAlarmGapMinutes: Int = 5,
    val currentRingCount: Int = 0
) {
    companion object {
        const val SCHEDULE_TYPE_ONE_TIME = "ONE_TIME"
        const val SCHEDULE_TYPE_RECURRING_DAYS = "RECURRING_DAYS"
        const val SCHEDULE_TYPE_SPECIFIC_DATE = "SPECIFIC_DATE"
        const val SCHEDULE_TYPE_CALENDAR_EVENT = "CALENDAR_EVENT"

        // Bitmask values for days of week
        const val DAY_MON = 1
        const val DAY_TUE = 2
        const val DAY_WED = 4
        const val DAY_THU = 8
        const val DAY_FRI = 16
        const val DAY_SAT = 32
        const val DAY_SUN = 64

        const val DAYS_WEEKDAYS = DAY_MON or DAY_TUE or DAY_WED or DAY_THU or DAY_FRI
        const val DAYS_WEEKENDS = DAY_SAT or DAY_SUN
        const val DAYS_EVERYDAY = DAYS_WEEKDAYS or DAYS_WEEKENDS

        fun calendarDayOfWeekToBit(calDayOfWeek: Int): Int {
            return when (calDayOfWeek) {
                java.util.Calendar.MONDAY -> DAY_MON
                java.util.Calendar.TUESDAY -> DAY_TUE
                java.util.Calendar.WEDNESDAY -> DAY_WED
                java.util.Calendar.THURSDAY -> DAY_THU
                java.util.Calendar.FRIDAY -> DAY_FRI
                java.util.Calendar.SATURDAY -> DAY_SAT
                java.util.Calendar.SUNDAY -> DAY_SUN
                else -> 0
            }
        }

        /**
         * If the alarm trigger time is on the previous calendar day relative to the meeting start time
         * (e.g. a 12:05 AM meeting with a 10-minute lead time triggers at 11:55 PM the night before),
         * shifts the repeatDays bitmask back by 1 day. Otherwise returns repeatDays unchanged.
         */
        fun adjustRepeatDaysForOffset(meetingRepeatDays: Int, meetingStartMillis: Long, triggerMillis: Long): Int {
            if (meetingRepeatDays <= 0) return 0
            val meetingCal = java.util.Calendar.getInstance().apply { timeInMillis = meetingStartMillis }
            val triggerCal = java.util.Calendar.getInstance().apply { timeInMillis = triggerMillis }
            if (meetingCal.get(java.util.Calendar.DAY_OF_WEEK) == triggerCal.get(java.util.Calendar.DAY_OF_WEEK)) {
                return meetingRepeatDays
            }
            var shifted = 0
            if ((meetingRepeatDays and DAY_MON) != 0) shifted = shifted or DAY_SUN
            if ((meetingRepeatDays and DAY_TUE) != 0) shifted = shifted or DAY_MON
            if ((meetingRepeatDays and DAY_WED) != 0) shifted = shifted or DAY_TUE
            if ((meetingRepeatDays and DAY_THU) != 0) shifted = shifted or DAY_WED
            if ((meetingRepeatDays and DAY_FRI) != 0) shifted = shifted or DAY_THU
            if ((meetingRepeatDays and DAY_SAT) != 0) shifted = shifted or DAY_FRI
            if ((meetingRepeatDays and DAY_SUN) != 0) shifted = shifted or DAY_SAT
            return shifted
        }
    }

    fun isDaySelected(dayBit: Int): Boolean {
        return (repeatDays and dayBit) != 0
    }

    fun getFormattedTime(): String {
        val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format("%02d:%02d %s", h12, minute, amPm)
    }

    fun getRepeatDaysSummary(): String {
        if (scheduleType == SCHEDULE_TYPE_RECURRING_DAYS && repeatDays > 0) {
            val daysLabel = when (repeatDays) {
                DAYS_EVERYDAY -> "Every day"
                DAYS_WEEKDAYS -> "Weekdays (Mon–Fri)"
                DAYS_WEEKENDS -> "Weekends"
                else -> {
                    val days = mutableListOf<String>()
                    if (isDaySelected(DAY_MON)) days.add("Mon")
                    if (isDaySelected(DAY_TUE)) days.add("Tue")
                    if (isDaySelected(DAY_WED)) days.add("Wed")
                    if (isDaySelected(DAY_THU)) days.add("Thu")
                    if (isDaySelected(DAY_FRI)) days.add("Fri")
                    if (isDaySelected(DAY_SAT)) days.add("Sat")
                    if (isDaySelected(DAY_SUN)) days.add("Sun")
                    days.joinToString(", ")
                }
            }
            return if (!isManual && calendarEventId != null && offsetMinutes > 0) {
                "$daysLabel • Calendar (${offsetMinutes}m before)"
            } else {
                daysLabel
            }
        }
        if (scheduleType == SCHEDULE_TYPE_CALENDAR_EVENT) {
            val datePrefix = if (triggerTimeMillis > 0L) {
                val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
                "${sdf.format(java.util.Date(triggerTimeMillis))} • "
            } else ""
            return "${datePrefix}Calendar ($offsetMinutes min before)"
        }
        if (scheduleType == SCHEDULE_TYPE_SPECIFIC_DATE && specificDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("EEE, MMM dd, yyyy", java.util.Locale.getDefault())
            return "On ${sdf.format(java.util.Date(specificDateMillis))}"
        }
        if (triggerTimeMillis > 0L) {
            val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
            return "Once (${sdf.format(java.util.Date(triggerTimeMillis))})"
        }
        return "Once"
    }
}
