package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testAlarmFormattedTime() {
        val morningAlarm = AlarmEntity(hour = 7, minute = 30)
        assertEquals("07:30 AM", morningAlarm.getFormattedTime())

        val eveningAlarm = AlarmEntity(hour = 19, minute = 45)
        assertEquals("07:45 PM", eveningAlarm.getFormattedTime())

        val midnightAlarm = AlarmEntity(hour = 0, minute = 5)
        assertEquals("12:05 AM", midnightAlarm.getFormattedTime())
    }

    @Test
    fun testRecurringDaysSummary() {
        val monThuAlarm = AlarmEntity(
            scheduleType = AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS,
            repeatDays = AlarmEntity.DAY_MON or AlarmEntity.DAY_THU
        )
        assertEquals("Mon, Thu", monThuAlarm.getRepeatDaysSummary())

        val weekdaysAlarm = AlarmEntity(
            scheduleType = AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS,
            repeatDays = AlarmEntity.DAYS_WEEKDAYS
        )
        assertEquals("Weekdays", weekdaysAlarm.getRepeatDaysSummary())
    }

    @Test
    fun testTriggerTimeCalculation() {
        val now = System.currentTimeMillis()
        val trigger = AlarmScheduler.computeNextTriggerTime(
            hour = 23,
            minute = 59,
            scheduleType = AlarmEntity.SCHEDULE_TYPE_ONE_TIME,
            repeatDays = 0,
            specificDateMillis = null
        )
        assertTrue(trigger >= now)
    }
}
