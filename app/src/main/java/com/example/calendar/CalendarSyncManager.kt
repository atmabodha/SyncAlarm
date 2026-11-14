package com.example.calendar

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import com.example.alarm.AlarmNotificationManager
import com.example.alarm.AlarmScheduler
import com.example.data.local.AppDatabase
import com.example.data.model.CalendarMeeting
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

class CalendarSyncManager(private val context: Context) {

    private val settingsRepo = SettingsRepository(context)
    private val db = AppDatabase.getInstance(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var observerDebounceJob: Job? = null
    @Volatile
    private var lastManualSyncRequestMillis: Long = 0L

    private val simulatedMeetings = mutableListOf<CalendarMeeting>()

    private val _meetingsFlow = MutableStateFlow<List<CalendarMeeting>>(emptyList())
    val meetingsFlow: StateFlow<List<CalendarMeeting>> = _meetingsFlow.asStateFlow()

    private val _deletedMeetingAlarmsFlow = MutableStateFlow<List<com.example.data.model.AlarmEntity>>(emptyList())
    val deletedMeetingAlarmsFlow: StateFlow<List<com.example.data.model.AlarmEntity>> = _deletedMeetingAlarmsFlow.asStateFlow()

    private var contentObserver: ContentObserver? = null

    init {
        registerCalendarObserver()
    }

    fun hasCalendarPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun registerCalendarObserver() {
        if (!hasCalendarPermission()) {
            return
        }
        if (contentObserver == null) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    // Fast 800ms debounce so deleting a meeting in Google Calendar raises the prompt notification almost immediately
                    observerDebounceJob?.cancel()
                    observerDebounceJob = syncScope.launch {
                        delay(800L)
                        syncCalendarEvents(forcePrompt = false)
                    }
                }
            }
            try {
                context.contentResolver.registerContentObserver(
                    CalendarContract.CONTENT_URI,
                    true,
                    observer
                )
                contentObserver = observer
            } catch (e: SecurityException) {
                Log.w("CalendarSyncManager", "Calendar permission not yet granted for observer")
            } catch (e: Exception) {
                Log.w("CalendarSyncManager", "Could not register Calendar ContentObserver: ${e.message}")
            }
        }
    }

    /**
     * Checks whether a calendar entry has no specific time of day (e.g., all-day event, holiday, birthday).
     * Note: Google Calendar stores timed recurring event durations in seconds such as "P3600S" or "P1800S",
     * so any duration containing H, M, or S is a timed meeting, whereas whole-day durations like "P1D" or "P1W" are all-day.
     */
    private fun isUntimedOrAllDayEvent(
        allDayFlag: Int,
        startMillis: Long,
        endMillis: Long,
        eventTimezone: String?,
        durationStr: String?
    ): Boolean {
        if (allDayFlag != 0) return true
        if (startMillis <= 0L) return true

        if (!durationStr.isNullOrBlank()) {
            val upper = durationStr.trim().uppercase()
            // If duration specifies hours, minutes, or seconds (e.g. "P3600S", "PT1H", "PT30M"), it is a timed meeting
            if (upper.contains("H") || upper.contains("M") || upper.contains("S")) {
                return false
            }
            // Whole-day or whole-week duration without time units (e.g. "P1D", "P2D", "P1W") is an all-day event
            if (upper.matches(Regex("^P\\d+[DW]$"))) {
                return true
            }
        }

        // Google Calendar stores all-day / holiday entries in UTC midnight boundaries even when local timezone is non-UTC
        if (eventTimezone.equals("UTC", ignoreCase = true) &&
            java.util.TimeZone.getDefault().rawOffset != 0 &&
            startMillis % 86400000L == 0L
        ) {
            val span = endMillis - startMillis
            if (span >= 86400000L && span % 86400000L == 0L) {
                return true
            }
        }
        return false
    }

    fun requestSystemCalendarSync() {
        val now = System.currentTimeMillis()
        if (now - lastManualSyncRequestMillis < 60_000L) {
            return
        }
        lastManualSyncRequestMillis = now
        try {
            val extras = android.os.Bundle().apply {
                putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
            }
            android.content.ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
        } catch (e: Exception) {
            Log.w("CalendarSyncManager", "Could not request system calendar sync: ${e.message}")
        }
    }

    /**
     * Parses an RFC 2445 RRULE string (e.g. "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" or "FREQ=DAILY")
     * into an AlarmEntity repeatDays bitmask.
     */
    fun parseRRuleToDaysBitmask(rrule: String?, startMillis: Long): Int {
        if (rrule.isNullOrBlank()) return 0
        val upper = rrule.trim().uppercase()

        val byDayMatch = Regex("BYDAY=([^;]+)").find(upper)
        if (byDayMatch != null) {
            val daysPart = byDayMatch.groupValues[1]
            var mask = 0
            for (token in daysPart.split(",")) {
                val t = token.trim()
                when {
                    t.endsWith("MO") -> mask = mask or com.example.data.model.AlarmEntity.DAY_MON
                    t.endsWith("TU") -> mask = mask or com.example.data.model.AlarmEntity.DAY_TUE
                    t.endsWith("WE") -> mask = mask or com.example.data.model.AlarmEntity.DAY_WED
                    t.endsWith("TH") -> mask = mask or com.example.data.model.AlarmEntity.DAY_THU
                    t.endsWith("FR") -> mask = mask or com.example.data.model.AlarmEntity.DAY_FRI
                    t.endsWith("SA") -> mask = mask or com.example.data.model.AlarmEntity.DAY_SAT
                    t.endsWith("SU") -> mask = mask or com.example.data.model.AlarmEntity.DAY_SUN
                }
            }
            if (mask != 0) return mask
        }

        if (upper.contains("FREQ=DAILY")) {
            return com.example.data.model.AlarmEntity.DAYS_EVERYDAY
        }

        if (upper.contains("FREQ=WEEKLY")) {
            val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
            return com.example.data.model.AlarmEntity.calendarDayOfWeekToBit(cal.get(Calendar.DAY_OF_WEEK))
        }

        return 0
    }

    /**
     * Resolves the repeatDays bitmask for a calendar event ID (combining its RRULE and upcoming occurrences).
     */
    fun getMeetingRepeatDays(eventId: Long, fallbackStartTime: Long, fallbackRepeatDays: Int = 0): Int {
        val cached = _meetingsFlow.value.firstOrNull { it.id == eventId }?.repeatDays ?: 0
        if (cached > 0) return cached or fallbackRepeatDays
        if (!hasCalendarPermission() || eventId >= 1_600_000_000_000L) {
            return fallbackRepeatDays
        }
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.DTSTART
            )
            val selection = "${CalendarContract.Events._ID} = ?"
            val selectionArgs = arrayOf(eventId.toString())
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                    val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null
                    val dtStart = if (startCol != -1) cursor.getLong(startCol) else fallbackStartTime
                    val parsed = parseRRuleToDaysBitmask(rrule, if (dtStart > 0L) dtStart else fallbackStartTime)
                    if (parsed > 0) return parsed or fallbackRepeatDays
                }
            }
        } catch (e: Exception) {
            Log.w("CalendarSyncManager", "Error querying RRULE for event $eventId: ${e.message}")
        }
        return fallbackRepeatDays
    }

    /**
     * Queries the latest upcoming start timestamp for a specific Google Calendar event ID.
     * Used when scheduling from a notification to ensure any date/time edits made in Google Calendar are honored.
     */
    fun getLatestMeetingStartTime(eventId: Long, fallbackStartTime: Long): Long {
        if (!hasCalendarPermission() || eventId >= 1_600_000_000_000L) {
            return fallbackStartTime
        }
        try {
            val now = System.currentTimeMillis() - (12L * 60 * 60 * 1000L)
            val ninetyDaysLater = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000L)
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, now)
            ContentUris.appendId(builder, ninetyDaysLater)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Events.DELETED
            )
            val selection = "${CalendarContract.Instances.EVENT_ID} = ?"
            val selectionArgs = arrayOf(eventId.toString())
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

            val validBegins = mutableListOf<Long>()
            context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val beginCol = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val deletedCol = cursor.getColumnIndex(CalendarContract.Events.DELETED)
                while (cursor.moveToNext()) {
                    val deleted = if (deletedCol != -1) cursor.getInt(deletedCol) else 0
                    if (deleted != 0) continue
                    if (beginCol != -1) {
                        val begin = cursor.getLong(beginCol)
                        if (begin >= now) {
                            validBegins.add(begin)
                        }
                    }
                }
            }
            if (validBegins.isNotEmpty()) {
                val futureMatch = validBegins.firstOrNull { it >= System.currentTimeMillis() - 60_000L }
                if (futureMatch != null) return futureMatch
                val exactMatch = validBegins.firstOrNull { kotlin.math.abs(it - fallbackStartTime) < 60_000L }
                return exactMatch ?: validBegins.first()
            }
        } catch (e: Exception) {
            Log.w("CalendarSyncManager", "Error fetching latest start time for event $eventId: ${e.message}")
        }
        return fallbackStartTime
    }

    /**
     * Checks if a real Google Calendar event row was completely deleted, canceled, or declined in the provider.
     * Note: Does NOT treat a single canceled occurrence (ORIGINAL_ID exception row) as a full series deletion.
     */
    private fun isCalendarEventDeletedOrMissing(eventId: Long): Boolean {
        if (!hasCalendarPermission()) return false
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.DELETED,
                CalendarContract.Events.STATUS,
                CalendarContract.Events.SELF_ATTENDEE_STATUS
            )
            val selection = "${CalendarContract.Events._ID} = ?"
            val selectionArgs = arrayOf(eventId.toString())

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return true // Completely removed from Calendar provider
                }
                val deletedCol = cursor.getColumnIndex(CalendarContract.Events.DELETED)
                if (deletedCol != -1 && cursor.getInt(deletedCol) != 0) {
                    return true // Marked as deleted
                }
                val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)
                if (statusCol != -1 && cursor.getInt(statusCol) == CalendarContract.Events.STATUS_CANCELED) {
                    return true // Event canceled
                }
                val attendeeCol = cursor.getColumnIndex(CalendarContract.Events.SELF_ATTENDEE_STATUS)
                if (attendeeCol != -1 && cursor.getInt(attendeeCol) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) {
                    return true // Event declined / removed by user in Google Calendar
                }
                return false
            }
            return true
        } catch (e: Exception) {
            Log.w("CalendarSyncManager", "Error checking if event $eventId is deleted: ${e.message}")
            return false
        }
    }

    private data class RawCandidateOccurrence(
        val id: Long,
        val title: String,
        val start: Long,
        val end: Long,
        val loc: String,
        val desc: String,
        val rruleBits: Int,
        val dayOfWeekBit: Int,
        val hour: Int,
        val minute: Int
    )

    private fun resolvedRepeatDaysFromCounts(
        isMultiOccurrenceById: Boolean,
        isMultiOccurrenceByTitleTime: Boolean,
        idMask: Int?,
        titleMask: Int?,
        rruleBits: Int
    ): Int {
        return when {
            isMultiOccurrenceById && isMultiOccurrenceByTitleTime ->
                (idMask ?: 0) or (titleMask ?: 0)
            isMultiOccurrenceById ->
                idMask ?: rruleBits
            isMultiOccurrenceByTitleTime ->
                titleMask ?: 0
            else -> 0
        }
    }

    /**
     * Checks if an existing alarm's meeting start time-of-day (or alarm time + offsetMinutes) matches
     * the candidate meeting's hour and minute.
     */
    private fun isSameMeetingTimeOfDay(
        alarm: com.example.data.model.AlarmEntity,
        meetingHour: Int,
        meetingMinute: Int
    ): Boolean {
        val mStart = alarm.meetingStartTime
        if (mStart != null && mStart > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = mStart }
            if (cal.get(Calendar.HOUR_OF_DAY) == meetingHour && cal.get(Calendar.MINUTE) == meetingMinute) {
                return true
            }
        }
        val expectedAlarmTotalMins = ((meetingHour * 60 + meetingMinute - alarm.offsetMinutes) % 1440 + 1440) % 1440
        val actualAlarmTotalMins = alarm.hour * 60 + alarm.minute
        return expectedAlarmTotalMins == actualAlarmTotalMins
    }

    suspend fun syncCalendarEvents(forcePrompt: Boolean = false): List<CalendarMeeting> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val meetings = mutableListOf<CalendarMeeting>()
            val seenEventIdsFromInstances = mutableSetOf<Long>()
            val seenInstanceKeys = mutableSetOf<String>()
            val candidates = mutableListOf<RawCandidateOccurrence>()
            val hasPermission = hasCalendarPermission()

        if (hasPermission) {
            registerCalendarObserver()
            if (forcePrompt) {
                requestSystemCalendarSync()
            }
            try {
                val now = System.currentTimeMillis()
                val startOfToday = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                // Include all of today (and at least 12h back for timezone differences) through 90 days ahead
                val queryStart = minOf(startOfToday, now - (12L * 60 * 60 * 1000L))
                val ninetyDaysLater = now + (90L * 24 * 60 * 60 * 1000L)

                fun collectCandidate(
                    id: Long,
                    title: String,
                    start: Long,
                    end: Long,
                    loc: String,
                    desc: String,
                    allDayFlag: Int,
                    deletedFlag: Int,
                    statusFlag: Int,
                    selfAttendeeStatus: Int,
                    eventTz: String?,
                    durationStr: String?,
                    rruleStr: String?,
                    isFromFallbackEventsTable: Boolean
                ) {
                    if (deletedFlag != 0) return
                    if (statusFlag == CalendarContract.Events.STATUS_CANCELED) return
                    if (selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) return
                    val effectiveEnd = if (end > start) end else start + 1800_000L
                    if (effectiveEnd < now - (15L * 60 * 1000L) && start < now - (15L * 60 * 1000L)) return
                    if (start > ninetyDaysLater) return
                    if (isUntimedOrAllDayEvent(allDayFlag, start, end, eventTz, durationStr)) return

                    if (isFromFallbackEventsTable && seenEventIdsFromInstances.contains(id)) return

                    val instanceKey = "${id}_${start / 60000L}"
                    if (!seenInstanceKeys.add(instanceKey)) return

                    if (!isFromFallbackEventsTable) {
                        seenEventIdsFromInstances.add(id)
                    }

                    val cal = Calendar.getInstance().apply { timeInMillis = start }
                    val dayBit = com.example.data.model.AlarmEntity.calendarDayOfWeekToBit(cal.get(Calendar.DAY_OF_WEEK))
                    val rruleBits = parseRRuleToDaysBitmask(rruleStr, start)

                    candidates.add(
                        RawCandidateOccurrence(
                            id = id,
                            title = title,
                            start = start,
                            end = effectiveEnd,
                            loc = loc,
                            desc = desc,
                            rruleBits = rruleBits,
                            dayOfWeekBit = dayBit,
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE)
                        )
                    )
                }

                // 1. Query CalendarContract.Instances so recurring and moved events resolve to their exact upcoming date/time
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, queryStart)
                ContentUris.appendId(builder, ninetyDaysLater)

                val instanceProjection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_TIMEZONE,
                    CalendarContract.Events.DURATION,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.DELETED,
                    CalendarContract.Events.STATUS,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS
                )

                val instanceSortOrder = "${CalendarContract.Instances.BEGIN} ASC"

                context.contentResolver.query(
                    builder.build(),
                    instanceProjection,
                    null,
                    null,
                    instanceSortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleCol = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val startCol = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endCol = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val locCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    val descCol = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                    val allDayCol = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val tzCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_TIMEZONE)
                    val durCol = cursor.getColumnIndex(CalendarContract.Events.DURATION)
                    val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                    val delCol = cursor.getColumnIndex(CalendarContract.Events.DELETED)
                    val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)
                    val attendeeCol = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Meeting"
                        val start = cursor.getLong(startCol)
                        val end = if (endCol != -1) cursor.getLong(endCol) else 0L
                        val loc = if (locCol != -1) cursor.getString(locCol) ?: "" else ""
                        val desc = if (descCol != -1) cursor.getString(descCol) ?: "" else ""
                        val allDayFlag = if (allDayCol != -1) cursor.getInt(allDayCol) else 0
                        val eventTz = if (tzCol != -1) cursor.getString(tzCol) else null
                        val durationStr = if (durCol != -1) cursor.getString(durCol) else null
                        val rruleStr = if (rruleCol != -1) cursor.getString(rruleCol) else null
                        val deletedFlag = if (delCol != -1) cursor.getInt(delCol) else 0
                        val statusFlag = if (statusCol != -1) cursor.getInt(statusCol) else 0
                        val selfAttendeeStatus = if (attendeeCol != -1) cursor.getInt(attendeeCol) else 0

                        collectCandidate(
                            id = id,
                            title = title,
                            start = start,
                            end = end,
                            loc = loc,
                            desc = desc,
                            allDayFlag = allDayFlag,
                            deletedFlag = deletedFlag,
                            statusFlag = statusFlag,
                            selfAttendeeStatus = selfAttendeeStatus,
                            eventTz = eventTz,
                            durationStr = durationStr,
                            rruleStr = rruleStr,
                            isFromFallbackEventsTable = false
                        )
                    }
                }

                // 2. Fallback query on CalendarContract.Events for any newly added events not yet expanded in Instances
                val eventsProjection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_TIMEZONE,
                    CalendarContract.Events.DURATION,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.DELETED,
                    CalendarContract.Events.STATUS,
                    CalendarContract.Events.SELF_ATTENDEE_STATUS
                )
                val eventsSelection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
                val eventsArgs = arrayOf(queryStart.toString(), ninetyDaysLater.toString())
                val eventsSort = "${CalendarContract.Events.DTSTART} ASC"

                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    eventsProjection,
                    eventsSelection,
                    eventsArgs,
                    eventsSort
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(CalendarContract.Events._ID)
                    val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endCol = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                    val locCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val descCol = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val allDayCol = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    val tzCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                    val durCol = cursor.getColumnIndex(CalendarContract.Events.DURATION)
                    val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                    val delCol = cursor.getColumnIndex(CalendarContract.Events.DELETED)
                    val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)
                    val attendeeCol = cursor.getColumnIndex(CalendarContract.Events.SELF_ATTENDEE_STATUS)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Meeting"
                        val start = cursor.getLong(startCol)
                        val end = if (endCol != -1) cursor.getLong(endCol) else 0L
                        val loc = if (locCol != -1) cursor.getString(locCol) ?: "" else ""
                        val desc = if (descCol != -1) cursor.getString(descCol) ?: "" else ""
                        val allDayFlag = if (allDayCol != -1) cursor.getInt(allDayCol) else 0
                        val eventTz = if (tzCol != -1) cursor.getString(tzCol) else null
                        val durationStr = if (durCol != -1) cursor.getString(durCol) else null
                        val rruleStr = if (rruleCol != -1) cursor.getString(rruleCol) else null
                        val deletedFlag = if (delCol != -1) cursor.getInt(delCol) else 0
                        val statusFlag = if (statusCol != -1) cursor.getInt(statusCol) else 0
                        val selfAttendeeStatus = if (attendeeCol != -1) cursor.getInt(attendeeCol) else 0

                        collectCandidate(
                            id = id,
                            title = title,
                            start = start,
                            end = end,
                            loc = loc,
                            desc = desc,
                            allDayFlag = allDayFlag,
                            deletedFlag = deletedFlag,
                            statusFlag = statusFlag,
                            selfAttendeeStatus = selfAttendeeStatus,
                            eventTz = eventTz,
                            durationStr = durationStr,
                            rruleStr = rruleStr,
                            isFromFallbackEventsTable = true
                        )
                    }
                }

                // 3. Consolidate recurring series (by event ID and by matching title+time across multiple days of the week)
                val activeCandidateEventIds = candidates.map { it.id }.toSet()
                val eventIdDaysMask = mutableMapOf<Long, Int>()
                val eventIdCount = mutableMapOf<Long, Int>()
                val titleTimeDaysMask = mutableMapOf<String, Int>()
                val titleTimeCount = mutableMapOf<String, Int>()

                for (c in candidates) {
                    eventIdCount[c.id] = (eventIdCount[c.id] ?: 0) + 1
                    val prevMask = eventIdDaysMask[c.id] ?: 0
                    eventIdDaysMask[c.id] = prevMask or c.rruleBits or c.dayOfWeekBit

                    val titleTimeKey = "${c.title.trim().lowercase()}_${c.hour}_${c.minute}"
                    titleTimeCount[titleTimeKey] = (titleTimeCount[titleTimeKey] ?: 0) + 1
                    titleTimeDaysMask[titleTimeKey] = (titleTimeDaysMask[titleTimeKey] ?: 0) or c.rruleBits or c.dayOfWeekBit
                }

                val consolidatedEventIds = mutableSetOf<Long>()
                val consolidatedRecurringTitleTimeKeys = mutableSetOf<String>()
                val matchedAlarmIds = mutableSetOf<Long>()
                val allCalendarLinkedAlarms = db.alarmDao().getCalendarLinkedAlarms()

                // Sort candidates so we pick the nearest upcoming future occurrence (or ongoing occurrence) first
                val sortedCandidates = candidates.sortedWith(
                    compareBy<RawCandidateOccurrence> { it.start < now }.thenBy { it.start }
                )

                for (c in sortedCandidates) {
                    val titleTimeKey = "${c.title.trim().lowercase()}_${c.hour}_${c.minute}"
                    val isMultiOccurrenceById = (eventIdCount[c.id] ?: 0) > 1 || c.rruleBits > 0
                    val isMultiOccurrenceByTitleTime = (titleTimeCount[titleTimeKey] ?: 0) > 1

                    // Also check if an existing recurring alarm matches this event's title & time-of-day
                    // (e.g., when a recurring series was split or had one occurrence deleted in Google Calendar)
                    val matchingExistingAlarm = db.alarmDao().getAlarmByCalendarEventId(c.id)
                        ?: allCalendarLinkedAlarms.firstOrNull { alarm ->
                            alarm.id !in matchedAlarmIds &&
                                (resolvedRepeatDaysFromCounts(isMultiOccurrenceById, isMultiOccurrenceByTitleTime, eventIdDaysMask[c.id], titleTimeDaysMask[titleTimeKey], c.rruleBits) > 0 ||
                                    alarm.scheduleType == com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) &&
                                alarm.title.trim().equals(c.title.trim(), ignoreCase = true) &&
                                isSameMeetingTimeOfDay(alarm, c.hour, c.minute)
                        }

                    val baseRepeatDays = resolvedRepeatDaysFromCounts(
                        isMultiOccurrenceById = isMultiOccurrenceById,
                        isMultiOccurrenceByTitleTime = isMultiOccurrenceByTitleTime,
                        idMask = eventIdDaysMask[c.id],
                        titleMask = titleTimeDaysMask[titleTimeKey],
                        rruleBits = c.rruleBits
                    )
                    // If this series already had a recurring alarm and still has upcoming occurrences, preserve its recurring nature
                    val resolvedRepeatDays = if (baseRepeatDays > 0) {
                        baseRepeatDays
                    } else if (matchingExistingAlarm?.scheduleType == com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS &&
                        matchingExistingAlarm.repeatDays > 0
                    ) {
                        c.dayOfWeekBit
                    } else {
                        0
                    }

                    if (!consolidatedEventIds.add(c.id)) continue
                    if (resolvedRepeatDays > 0 && !consolidatedRecurringTitleTimeKeys.add(titleTimeKey)) continue

                    var existingAlarm = matchingExistingAlarm

                    if (existingAlarm != null) {
                        matchedAlarmIds.add(existingAlarm.id)
                        // Ensure any stale deleted-meeting notification for this alarm is cleared since the series is still active
                        AlarmNotificationManager.cancelMeetingDeletedPromptNotification(context, existingAlarm.id)

                        val newTriggerTime = c.start - (existingAlarm.offsetMinutes * 60 * 1000L)
                        val cal = Calendar.getInstance().apply { timeInMillis = newTriggerTime }
                        val alarmHour = cal.get(Calendar.HOUR_OF_DAY)
                        val alarmMinute = cal.get(Calendar.MINUTE)

                        if (resolvedRepeatDays > 0) {
                            val adjustedRepeatDays = com.example.data.model.AlarmEntity.adjustRepeatDaysForOffset(
                                meetingRepeatDays = resolvedRepeatDays,
                                meetingStartMillis = c.start,
                                triggerMillis = newTriggerTime
                            )
                            val nextRecurringTrigger = AlarmScheduler.computeNextTriggerTime(
                                hour = alarmHour,
                                minute = alarmMinute,
                                scheduleType = com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS,
                                repeatDays = adjustedRepeatDays,
                                specificDateMillis = null,
                                meetingStartTime = c.start,
                                offsetMinutes = existingAlarm.offsetMinutes
                            )
                            if (existingAlarm.calendarEventId != c.id ||
                                existingAlarm.scheduleType != com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS ||
                                existingAlarm.repeatDays != adjustedRepeatDays ||
                                existingAlarm.hour != alarmHour ||
                                existingAlarm.minute != alarmMinute ||
                                kotlin.math.abs(existingAlarm.triggerTimeMillis - nextRecurringTrigger) >= 60_000L ||
                                kotlin.math.abs((existingAlarm.meetingStartTime ?: 0L) - c.start) >= 60_000L
                            ) {
                                val updatedAlarm = existingAlarm.copy(
                                    calendarEventId = c.id,
                                    title = c.title,
                                    hour = alarmHour,
                                    minute = alarmMinute,
                                    scheduleType = com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS,
                                    repeatDays = adjustedRepeatDays,
                                    meetingStartTime = c.start,
                                    specificDateMillis = null,
                                    triggerTimeMillis = nextRecurringTrigger
                                )
                                db.alarmDao().updateAlarm(updatedAlarm)
                                if (updatedAlarm.isEnabled) {
                                    AlarmScheduler(context).scheduleAlarm(updatedAlarm)
                                }
                                existingAlarm = updatedAlarm
                            }
                        } else if (existingAlarm.calendarEventId != c.id ||
                            kotlin.math.abs((existingAlarm.meetingStartTime ?: 0L) - c.start) >= 60_000L
                        ) {
                            val updatedAlarm = existingAlarm.copy(
                                calendarEventId = c.id,
                                title = c.title,
                                hour = alarmHour,
                                minute = alarmMinute,
                                meetingStartTime = c.start,
                                specificDateMillis = newTriggerTime,
                                triggerTimeMillis = newTriggerTime
                            )
                            db.alarmDao().updateAlarm(updatedAlarm)
                            if (updatedAlarm.isEnabled) {
                                AlarmScheduler(context).scheduleAlarm(updatedAlarm)
                            }
                            existingAlarm = updatedAlarm
                        }
                    }

                    meetings.add(
                        CalendarMeeting(
                            id = c.id,
                            title = c.title,
                            startTimeMillis = c.start,
                            endTimeMillis = c.end,
                            location = c.loc,
                            description = c.desc,
                            isAllDay = false,
                            repeatDays = resolvedRepeatDays,
                            hasAlarmScheduled = existingAlarm?.isEnabled == true,
                            scheduledOffsetMinutes = existingAlarm?.offsetMinutes
                        )
                    )
                }

                // Detect if any previously synced Google Calendar meeting with an active alarm was completely deleted.
                // Any alarm in matchedAlarmIds still has active upcoming occurrences in Google Calendar (e.g. a recurring series
                // where only one single occurrence was deleted) and MUST NOT be deleted or flagged as missing.
                val calendarAlarms = db.alarmDao().getCalendarLinkedAlarms()
                val detectedDeletedAlarms = mutableListOf<com.example.data.model.AlarmEntity>()
                for (alarm in calendarAlarms) {
                    if (alarm.id in matchedAlarmIds) continue
                    val eventId = alarm.calendarEventId ?: continue
                    val isSimulated = eventId >= 1_600_000_000_000L
                    if (!isSimulated && alarm.isEnabled) {
                        val eventRowDeleted = isCalendarEventDeletedOrMissing(eventId)
                        val expectedTime = alarm.meetingStartTime ?: alarm.triggerTimeMillis
                        val noActiveInstanceInCalendar = if (alarm.scheduleType == com.example.data.model.AlarmEntity.SCHEDULE_TYPE_RECURRING_DAYS) {
                            eventId !in activeCandidateEventIds &&
                                candidates.none {
                                    it.title.trim().equals(alarm.title.trim(), ignoreCase = true) &&
                                        isSameMeetingTimeOfDay(alarm, it.hour, it.minute)
                                }
                        } else {
                            expectedTime <= ninetyDaysLater && eventId !in activeCandidateEventIds
                        }

                        if (eventRowDeleted || noActiveInstanceInCalendar) {
                            if (!settingsRepo.isDeletionPrompted(alarm.id)) {
                                Log.d("CalendarSyncManager", "Detected deleted calendar meeting for alarm '${alarm.title}' (eventId=$eventId). Prompting user.")
                                AlarmNotificationManager.cancelMeetingPromptNotification(context, eventId)
                                AlarmNotificationManager.postMeetingDeletedPromptNotification(context, alarm)
                                detectedDeletedAlarms.add(alarm)
                            }
                        }
                    }
                }
                _deletedMeetingAlarmsFlow.value = detectedDeletedAlarms
            } catch (e: Exception) {
                Log.e("CalendarSyncManager", "Error querying calendar content provider", e)
            }
        }

        // Merge any active simulated meetings and update their alarm status
        val simSnapshot = synchronized(simulatedMeetings) { simulatedMeetings.toList() }
        val updatedSims = mutableListOf<CalendarMeeting>()
        for (sim in simSnapshot) {
            val existingAlarm = db.alarmDao().getAlarmByCalendarEventId(sim.id)
            val updatedSim = sim.copy(
                hasAlarmScheduled = existingAlarm?.isEnabled == true,
                scheduledOffsetMinutes = existingAlarm?.offsetMinutes
            )
            updatedSims.add(updatedSim)
            if (meetings.none { it.id == updatedSim.id && it.startTimeMillis == updatedSim.startTimeMillis }) {
                meetings.add(updatedSim)
            }
        }
        synchronized(simulatedMeetings) {
            for (updatedSim in updatedSims) {
                val idx = simulatedMeetings.indexOfFirst { it.id == updatedSim.id }
                if (idx != -1) {
                    simulatedMeetings[idx] = updatedSim
                }
            }
        }

        meetings.sortBy { it.startTimeMillis }

        // Check for new or rescheduled timed meetings to prompt (never prompt for all-day / untimed entries)
        // Only prompt for upcoming meetings starting within the next 48 hours (max 3 per pass) to avoid flooding system notifications
        val settings = settingsRepo.settingsFlow.value
        if (settings.promptCalendarMeetings) {
            val nowMs = System.currentTimeMillis()
            val promptHorizonMs = nowMs + (48L * 60 * 60 * 1000L)
            val promptedEventIdsInPass = mutableSetOf<Long>()
            var notificationsPostedInPass = 0
            for (meeting in meetings) {
                if (notificationsPostedInPass >= 3) break
                if (meeting.startTimeMillis in (nowMs + 1)..promptHorizonMs && !meeting.isAllDay) {
                    if (!promptedEventIdsInPass.add(meeting.id)) continue

                    val alreadyPromptedForThisTime = settingsRepo.isMeetingPrompted(meeting.id, meeting.startTimeMillis)
                    val hasAlarm = db.alarmDao().getAlarmByCalendarEventAndStartTime(meeting.id, meeting.startTimeMillis) != null ||
                        db.alarmDao().getAlarmByCalendarEventId(meeting.id) != null
                    if (!alreadyPromptedForThisTime && !hasAlarm) {
                        Log.d("CalendarSyncManager", "Timed meeting detected! Posting 5m/10m prompt: ${meeting.title} at ${meeting.getFormattedStartTime()}")
                        AlarmNotificationManager.postMeetingPromptNotification(context, meeting)
                        settingsRepo.markMeetingPrompted(meeting.id, meeting.startTimeMillis)
                        notificationsPostedInPass++
                    }
                }
            }
        }

        _meetingsFlow.value = meetings
        meetings
        }
    }

    /**
     * Inserts sample simulated timed meetings for interactive demonstration.
     */
    suspend fun injectSampleMeeting(title: String, delayMinutes: Int): CalendarMeeting = withContext(Dispatchers.IO) {
        val sampleId = System.currentTimeMillis()
        val start = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val end = start + (30 * 60 * 1000L)
        val meeting = CalendarMeeting(
            id = sampleId,
            title = title,
            startTimeMillis = start,
            endTimeMillis = end,
            location = "Google Meet",
            description = "Synced from Google Calendar",
            isAllDay = false,
            hasAlarmScheduled = false
        )
        synchronized(simulatedMeetings) {
            simulatedMeetings.add(0, meeting)
        }
        // Prompt for this timed meeting immediately
        AlarmNotificationManager.postMeetingPromptNotification(context, meeting)
        settingsRepo.markMeetingPrompted(meeting.id, meeting.startTimeMillis)

        syncCalendarEvents()
        meeting
    }

    /**
     * Handles when a meeting is deleted (or simulated as deleted from Google Calendar).
     * If an alarm was scheduled for this meeting, dispatches a notification asking the user
     * if the alarm should also be removed.
     */
    suspend fun onMeetingDeleted(meetingId: Long) = withContext(Dispatchers.IO) {
        synchronized(simulatedMeetings) {
            simulatedMeetings.removeAll { it.id == meetingId }
        }
        AlarmNotificationManager.cancelMeetingPromptNotification(context, meetingId)

        val linkedAlarm = db.alarmDao().getAlarmByCalendarEventId(meetingId)
        if (linkedAlarm != null && linkedAlarm.isEnabled) {
            AlarmNotificationManager.postMeetingDeletedPromptNotification(context, linkedAlarm)
            settingsRepo.markDeletionPrompted(linkedAlarm.id)
        }

        syncCalendarEvents()
    }
}
