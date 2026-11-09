# SyncAlarm: Google Calendar-Synced & Advanced Manual Alarm System

A full-featured Android alarm application combining smart Google Calendar meeting detection with advanced manual alarm scheduling. Includes interactive 5-minute / 10-minute lead-time notifications for newly added calendar meetings, recurring day-of-week scheduling (e.g., every Monday & Thursday), future specific date alarms, persistent "Next Alarm" notifications with one-tap dismissal, auto-re-alarm rules (maximum ring count and gap between rings if no action is taken), customizable snooze intervals, volume sliders, and ringtone selection.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Advanced Scheduling & Re-Alarm Rules**:
> - **Recurring Days & Specific Future Dates**: Alarms can be set to:
>   1. **Day of Week Recurrence**: Choose specific recurring days (e.g., Mondays & Thursdays, Weekdays, Weekends, or Everyday).
>   2. **Specific Future Date**: Use a Material 3 Date Picker to schedule an alarm for an exact date in the future (e.g., Nov 25, 2026).
>   3. **One-Time Next Occurrence**: Schedule for the nearest upcoming time today/tomorrow.
> - **Auto-Dismiss & Re-Alarm Engine**: If an alarm rings and the user takes no action (e.g. sleeps through or doesn't touch the phone), the alarm auto-silences after a set ringing period (e.g., 1 minute) and automatically re-alarms:
>   - **Max Re-Alarm Count**: Configurable in Settings (e.g. 1, 2, 3, or 5 re-rings).
>   - **Re-Alarm Gap**: Configurable interval between re-rings (e.g. 3, 5, or 10 minutes).
>   - **Snooze Configuration**: Independent user-initiated snooze duration (e.g., 5, 10, or 15 minutes) and max snooze allowance.
> - **Google Calendar Meeting Prompts**: Any meeting added to Google Calendar triggers an actionable notification with direct buttons: **"5 Mins Before"**, **"10 Mins Before"**, and **"Ignore"**.
> - **Upcoming Alarm Quick Dismiss**: Active alarms present an ongoing system notification before the ring time with a direct **"Dismiss"** action button.

- **Confirmed Decision 1**: Support both recurring day-of-week combinations (e.g. Mon + Thu) and specific future calendar date selection.
- **Confirmed Decision 2**: Provide granular Re-Alarm settings (max re-ring count, auto-silence timeout, gap between re-rings, and custom snooze duration).
- **Confirmed Decision 3**: New Google Calendar meetings prompt an actionable notification with 5m / 10m alarm options, and upcoming alarms show a persistent dismiss notification.

---

## 1. Overview & Core Concept

- **What It Does**: SyncAlarm serves as your complete alarm command center:
  1. **Google Calendar Automation**: Seamlessly scans for calendar events and alerts you with a 1-tap notification to schedule alarms 5m or 10m before meetings.
  2. **Comprehensive Manual Alarm Clock**: Supports multi-day recurrence (e.g., Mon & Thu), one-off alarms, and future date alarms (e.g., next month's flight).
  3. **Fail-Safe Re-Alarm Protection**: Re-rings automatically if you don't dismiss or snooze the alarm, with configurable limits and intervals.
  4. **Proactive Dismissal**: Shows an "Upcoming Alarm" notification ahead of time so you can dismiss alarms before they ring.
- **Target Audience / Persona**: Professionals, shift workers, and students who have busy schedules with both recurring routines and calendar meetings.
- **Key Value**: Eliminates the fear of sleeping through alarms through intelligent re-alarm logic and ensures you are never late to calendar meetings.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Main Alarm Hub & Hero Card**:
   - **Hero Card**: Displays the next active alarm (manual or calendar-based) with a real-time countdown, title, badge, and **"Dismiss Next"** button.
   - **Filter / Tabs**: Quick view toggle between **"All Alarms"**, **"Recurring"**, and **"Calendar Synced"**.
   - **Floating Action Button (`+`)**: Launches the Create Alarm flow.
   - **Top App Bar**: Calendar sync trigger (`Sync Now`), Calendar Meeting browser, and Settings gear icon.

2. **Manual Alarm Creation & Scheduling Flow**:
   - **Time Selection**: Material 3 Time Picker (Hour & Minute wheels).
   - **Schedule Mode Toggle**:
     - *Mode A: Repeat Days*: Interactive day-of-week chips `[M] [T] [W] [T] [F] [S] [S]` with quick presets (`Weekdays`, `Weekends`, `Everyday`, or custom days like Mon + Thu).
     - *Mode B: Specific Future Date*: Integrated DatePicker dialog to select any calendar date in the future.
     - *Mode C: One-Time Alarm*: Automatically schedules for the next occurrence today or tomorrow.
   - **Customization Options**:
     - **Label**: Alarm name (e.g., "Team Standup", "Morning Gym").
     - **Ringtone**: Tone selector with audio preview.
     - **Volume**: Interactive volume slider (0% to 100%) with test tone.
     - **Vibration**: On / Off toggle with haptic feedback.
     - **Re-Alarm / Snooze Override**: Option to use app defaults or specify custom re-alarm rules for this specific alarm.

3. **Meeting Alarm Suggestion Notification Flow**:
   - Detects newly added Google Calendar meeting.
   - Posts a high-priority heads-up notification:
     - **Title**: *"New Meeting: Strategy Review (3:30 PM)"*
     - **Body**: *"Set an alarm before this meeting starts?"*
     - **Action 1**: `5 Mins Before (3:25 PM)`
     - **Action 2**: `10 Mins Before (3:20 PM)`
     - **Action 3**: `Skip`
   - Tapping an action button schedules the exact alarm without opening the app.

4. **Upcoming Alarm Notification with Quick Dismiss**:
   - 30–60 minutes before any scheduled alarm, an ongoing notification appears:
     - *"Upcoming Alarm: Strategy Review at 3:25 PM"*
     - **Action**: `[ Dismiss ]`
   - Tapping "Dismiss" silences that specific upcoming ring without disabling future repeats.

5. **Alarm Ringing & Fail-Safe Re-Alarm Flow**:
   - When the scheduled time arrives:
     - Full-screen lock-screen alert (`AlarmRingingActivity`) with high-priority audio.
     - Displays buttons: **"Snooze"** (with configured snooze duration) and **"Dismiss"**.
     - **If no action is taken**: Audio silences after 1 minute, and the app schedules an automatic **Re-Alarm** after the configured gap (e.g., 5 minutes), repeating up to the configured maximum ring count (e.g., 3 times).

6. **Comprehensive Settings Screen**:
   - **Re-Alarm Rules**:
     - *Max Re-Alarm Rings*: Dropdown / picker (1, 2, 3, 4, 5 rings, or No Auto-Re-alarm).
     - *Re-Alarm Gap*: Gap between automatic re-rings (2m, 3m, 5m, 10m).
     - *Auto-Silence Timeout*: How long each ring lasts before pausing (30s, 1m, 2m).
   - **Snooze Settings**:
     - *Snooze Interval*: 5 min, 10 min, 15 min.
     - *Max Snoozes*: 3, 5, or Unlimited.
   - **Calendar Defaults**:
     - *Default Lead Time*: 5 min or 10 min.
     - *Auto-Prompt*: Toggle on/off.
   - **Default Audio & Ringtones**:
     - Master alarm volume level and default ringtone sound.

### Visual Identity & Theme

- **Aesthetic Direction**: **Midnight Precision** — deep slate dark theme with crisp illuminated accents.
- **Color Palette**:
  - `Background`: Deep Midnight Slate (`#0B0F19`)
  - `Card Surface`: Elevated Dark Slate (`#161E2E`)
  - `Primary / Accent`: Electric Violet (`#7C3AED`) & Indigo (`#6366F1`)
  - `Manual Alarm Badge`: Cyan Blue (`#06B6D4`)
  - `Calendar Alarm Badge`: Emerald Mint (`#10B981`)
  - `Warning / Ringing`: Crimson Red (`#F43F5E`)
  - `Text`: Crisp White (`#FFFFFF`) and Slate Gray (`#94A3B8`)
- **Typography & Components**:
  - Tabular digital numerals for clocks and countdowns.
  - Generous 56dp+ touch targets for snooze/dismiss.
  - Material 3 TimePicker, DatePicker, and ModalBottomSheets.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Flexible Scheduling (Days of Week + Specific Date)**:
  - *Chosen Approach*: Support both a bitmask representation of recurring days (Mon–Sun) and an optional `specificDateMillis` field.
  - *Why*: Gives complete scheduling flexibility: fixed weekly routines (e.g. Mondays & Thursdays) or one-off future appointments (e.g. Dec 15th doctor visit).
  - *Alternatives Considered*: Only 24-hour next alarms (too limited for modern alarm apps).

- **Decision 2: Automated Re-Alarm Engine vs OS Timeout**:
  - *Chosen Approach*: Track `currentRingCount` and schedule a linked follow-up alarm via `AlarmManager` if an alarm times out without dismiss/snooze.
  - *Why*: Standard Android ringtones will either stop after 60s or loop indefinitely until battery dies. Our engine safely pauses, waits for the configured gap (e.g. 5m), and rings again up to the max count.

- **Decision 3: Exact Alarm Execution via `AlarmManager.setAlarmClock`**:
  - *Chosen Approach*: `AlarmManager.setAlarmClock()` with `AlarmClockInfo`.
  - *Why*: Guaranteed execution even in Doze mode, and displays the system alarm clock icon.

- **Decision 4: On-Device & Cloud Calendar Sync**:
  - *Chosen Approach*: Android `CalendarContract` integration + Google Calendar API.
  - *Why*: Instant detection when Google Calendar app syncs on the phone without manual refreshing.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        User & System Triggers                          │
│   ┌───────────────────────────┐     ┌──────────────────────────────┐   │
│   │ Manual "+ Add Alarm" UI   │     │ Google Calendar App / Events │   │
│   │ - Days of Week (M/Th/...) │     │ - Newly added meetings       │   │
│   │ - Specific Future Date    │     └──────────────┬───────────────┘   │
│   └─────────────┬─────────────┘                    │                   │
└─────────────────┼──────────────────────────────────┼───────────────────┘
                  │                                  │ New Event Detected
                  │                                  ▼
                  │                   ┌──────────────────────────────┐
                  │                   │ Meeting Prompt Notification  │
                  │                   │ [ 5m Before ] [ 10m Before ] │
                  │                   └──────────────┬───────────────┘
                  │ User Saves Manual                │ User Selects Lead Time
                  └───────────────────┬──────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         SyncAlarm Core Engine                          │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ AlarmScheduler (AlarmManager.setAlarmClock)                    │   │
│   │ - Next trigger timestamp calculation (recurring or date)       │   │
│   │ - Room Database persistence (AlarmEntity)                      │   │
│   │ - Posts "Upcoming Alarm" notification with [ Dismiss ] action  │   │
│   └────────────────────────────────┬───────────────────────────────┘   │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │ Alarm Fires
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Ringing & Re-Alarm Engine                       │
│   ┌────────────────────────────┐      ┌────────────────────────────┐   │
│   │ AlarmReceiver & Service    │ ───> │ AlarmRingingActivity       │   │
│   │ - WakeLock + MediaPlayer   │      │ - Full-screen UI           │   │
│   │ - Auto-Silence Timer (1m)  │      │ - [ Snooze ] [ Dismiss ]   │   │
│   └─────────────┬──────────────┘      └─────────────┬──────────────┘   │
│                 │                                   │                  │
│                 │ If No Action Taken                │ User Action      │
│                 ▼                                   ▼                  │
│   ┌────────────────────────────┐      ┌────────────────────────────┐   │
│   │ Check maxReAlarmRings      │      │ Snooze: +snoozeMinutes     │   │
│   │ If < max: reschedule after │      │ Dismiss: advance recurring │   │
│   │ reAlarmGapMinutes          │      │ or disable one-time alarm  │   │
│   └────────────────────────────┘      └────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

### Proposed Technical Stack

| Component | Technology | Purpose |
|---|---|---|
| **UI Framework** | Jetpack Compose (Material 3) | Modern declarative UI, TimePicker, DatePicker, BottomSheets |
| **Language & Concurrency** | Kotlin 2.2 + Coroutines & StateFlow | Reactive UI state, asynchronous calendar sync |
| **Local Persistence** | AndroidX Room (Room KTX & KSP) | Persistent storage for manual alarms, calendar alarms, settings |
| **Scheduling** | Android `AlarmManager` + `BroadcastReceiver` | Exact Doze-resistant scheduling, re-alarm loop & reboot restore |
| **Calendar Sync** | `android.provider.CalendarContract` + Google API | Real-time meeting detection and calendar reading |
| **Audio & Haptics** | Android `MediaPlayer` + `Vibrator` | Alarm audio stream playback, custom volume, ringtone selector |
| **Notifications** | AndroidX Core `NotificationManagerCompat` | 5m/10m interactive prompts, upcoming alarm dismiss notification |

### Room Entity Schema & Preferences

- **`AlarmEntity`**:
  - `id: Long` (Primary key, auto-generated)
  - `isManual: Boolean` (True if user-created manual alarm, False if calendar-synced)
  - `calendarEventId: Long?` (Google Calendar event ID if calendar alarm)
  - `title: String` (Alarm label or meeting title)
  - `hour: Int`, `minute: Int` (Time of day)
  - `triggerTimeMillis: Long` (Exact epoch timestamp for next alarm trigger)
  - `scheduleType: String` (`ONE_TIME`, `RECURRING_DAYS`, `SPECIFIC_DATE`, `CALENDAR_EVENT`)
  - `repeatDays: Int` (Bitmask for recurring days: 1=Mon, 2=Tue, 4=Wed, 8=Thu, 16=Fri, 32=Sat, 64=Sun)
  - `specificDateMillis: Long?` (Date timestamp if scheduled for specific future date)
  - `meetingStartTime: Long?` (Original meeting start timestamp if calendar alarm)
  - `offsetMinutes: Int` (0 for manual, 5 or 10 for calendar alarms)
  - `isEnabled: Boolean` (Active toggle state)
  - `isDismissed: Boolean` (Next occurrence dismissed flag)
  - `volume: Float` (0.0f to 1.0f volume level)
  - `toneUri: String?` (Ringtone URI or sound identifier)
  - `toneTitle: String` (Display name of the sound)
  - `vibrate: Boolean` (Vibration enabled)
  - `snoozeDurationMinutes: Int` (Snooze interval, e.g. 5m, 10m)
  - `maxReAlarmRings: Int` (Max auto-re-ring count if ignored)
  - `reAlarmGapMinutes: Int` (Minutes between automatic re-rings)
  - `currentRingCount: Int` (Tracks how many times it has re-alarmed)

- **`AppPreferences`**:
  - `defaultMaxReAlarmRings: Int` (Default 3 times)
  - `defaultReAlarmGapMinutes: Int` (Default 5 minutes)
  - `defaultAutoSilenceSeconds: Int` (Default 60 seconds)
  - `defaultSnoozeMinutes: Int` (Default 10 minutes)
  - `defaultLeadTimeMinutes: Int` (5 or 10 minutes for calendar meetings)
  - `defaultVolume: Float` (0.85f)
  - `defaultToneTitle: String` ("Chime Alert")

### Implementation Roadmap
1. Configure permissions in `AndroidManifest.xml` (`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `READ_CALENDAR`, `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`).
2. Implement Room Database (`AlarmDatabase`, `AlarmDao`, `AlarmEntity`, `SettingsDataStore`/`SettingsRepository`).
3. Build `AlarmScheduler` with `AlarmManager.setAlarmClock`, `AlarmReceiver`, `DismissReceiver`, `SnoozeReceiver`, and `BootReceiver` handling re-alarm loops.
4. Build `CalendarSyncManager` for meeting detection and interactive 5m/10m prompt notifications.
5. Build Compose UI:
   - Dashboard with "Next Alarm" countdown card and quick dismiss.
   - Comprehensive Manual Alarm creation dialog with TimePicker, DatePicker (future dates), Repeat Days chips (Mondays/Thursdays/etc.), volume slider, and tone chooser.
   - Calendar Meetings view with manual alarm toggle and lead-time selectors.
   - Settings Screen for Max Re-Alarm rings, Re-Alarm gap, Snooze duration, and audio options.
   - Full-screen `AlarmRingingActivity` with snooze and dismiss controls.
6. Verify and compile the Android applet.
