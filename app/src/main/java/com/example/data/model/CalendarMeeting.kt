package com.example.data.model

data class CalendarMeeting(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String = "",
    val description: String = "",
    val isAllDay: Boolean = false,
    val repeatDays: Int = 0,
    val hasAlarmScheduled: Boolean = false,
    val scheduledOffsetMinutes: Int? = null
) {
    fun isRecurring(): Boolean = repeatDays > 0

    fun getFormattedStartTime(): String {
        val sdf = java.text.SimpleDateFormat("EEE, MMM dd • h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startTimeMillis))
    }

    fun getFormattedTimeOnly(): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startTimeMillis))
    }

    fun getRecurrenceSummary(): String {
        if (repeatDays == 0) return ""
        if (repeatDays == AlarmEntity.DAYS_EVERYDAY) return "Every day"
        if (repeatDays == AlarmEntity.DAYS_WEEKDAYS) return "Weekdays (Mon–Fri)"
        if (repeatDays == AlarmEntity.DAYS_WEEKENDS) return "Weekends (Sat, Sun)"

        val days = mutableListOf<String>()
        if ((repeatDays and AlarmEntity.DAY_MON) != 0) days.add("Mon")
        if ((repeatDays and AlarmEntity.DAY_TUE) != 0) days.add("Tue")
        if ((repeatDays and AlarmEntity.DAY_WED) != 0) days.add("Wed")
        if ((repeatDays and AlarmEntity.DAY_THU) != 0) days.add("Thu")
        if ((repeatDays and AlarmEntity.DAY_FRI) != 0) days.add("Fri")
        if ((repeatDays and AlarmEntity.DAY_SAT) != 0) days.add("Sat")
        if ((repeatDays and AlarmEntity.DAY_SUN) != 0) days.add("Sun")
        return days.joinToString(", ")
    }
}
