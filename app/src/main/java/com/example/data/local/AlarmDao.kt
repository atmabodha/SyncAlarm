package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY isEnabled DESC, triggerTimeMillis ASC")
    fun getAllAlarmsFlow(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun getAllAlarmsList(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 AND isDismissed = 0 ORDER BY triggerTimeMillis ASC")
    fun getActiveUpcomingAlarmsFlow(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun getAlarmByIdFlow(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE calendarEventId = :eventId LIMIT 1")
    suspend fun getAlarmByCalendarEventId(eventId: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE calendarEventId = :eventId AND ABS(meetingStartTime - :startTime) < 60000 LIMIT 1")
    suspend fun getAlarmByCalendarEventAndStartTime(eventId: Long, startTime: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE calendarEventId IS NOT NULL")
    suspend fun getCalendarLinkedAlarms(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long)

    @Query("UPDATE alarms SET isDismissed = :dismissed WHERE id = :id")
    suspend fun setAlarmDismissed(id: Long, dismissed: Boolean)

    @Query("UPDATE alarms SET isEnabled = :enabled, currentRingCount = 0, isDismissed = 0 WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET currentRingCount = :count WHERE id = :id")
    suspend fun updateRingCount(id: Long, count: Int)

    @Query("UPDATE alarms SET snoozeDurationMinutes = :snoozeMinutes")
    suspend fun updateAllAlarmsSnooze(snoozeMinutes: Int)

    @Query("UPDATE alarms SET maxReAlarmRings = :maxRings")
    suspend fun updateAllAlarmsMaxReRings(maxRings: Int)

    @Query("UPDATE alarms SET reAlarmGapMinutes = :gapMinutes")
    suspend fun updateAllAlarmsReAlarmGap(gapMinutes: Int)

    @Query("UPDATE alarms SET toneTitle = :toneTitle, toneUri = :toneUri")
    suspend fun updateAllAlarmsTone(toneTitle: String, toneUri: String?)

    @Query("UPDATE alarms SET volume = :volume")
    suspend fun updateAllAlarmsVolume(volume: Float)

    @Query("UPDATE alarms SET vibrate = :vibrate")
    suspend fun updateAllAlarmsVibrate(vibrate: Boolean)
}
