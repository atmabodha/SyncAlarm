# SyncAlarm — Smart Google Calendar & Custom Alarm Clock for Android

**SyncAlarm** is a modern Android alarm clock application built with **Kotlin** and **Jetpack Compose (Material 3)** that seamlessly bridges your **Google Calendar meetings** with reliable, customizable alarms—while also functioning as a full-featured standalone alarm clock.

---

## Key Features

### 1. Automatic Google Calendar Sync & Meeting Prompts
- **Real-Time Calendar Detection**: Syncs directly with the on-device Google Calendar provider (`CalendarContract.Instances` & `CalendarContract.Events`) and listens for new or updated meetings via a live `ContentObserver`.
- **Interactive Notification Prompts (`-5m` / `-10m`)**: When a new timed meeting is added to your Google Calendar, SyncAlarm posts an interactive notification asking if you want to schedule an alarm **5 minutes before** or **10 minutes before** the meeting (or **Skip**).
- **Recurring Meeting Support**: Automatically parses calendar recurrence rules (`RRULE`, such as working days `Mon–Fri`, `Every day`, or custom day combinations) and sets up recurring alarms across all scheduled days of the week.
- **Smart All-Day & Holiday Filtering**: Automatically ignores untimed and all-day calendar entries (such as public holidays, birthdays, and out-of-office banners) so you are only notified for actual timed meetings.
- **Deleted Meeting Detection**: If a meeting with an active alarm is removed or canceled in Google Calendar, SyncAlarm detects the deletion and posts a notification asking whether you want to **Remove Alarm** or **Keep Alarm**.
- **Schedule Later from the Meetings Tab**: If you skip a notification prompt, all upcoming meetings remain accessible in the **Meetings** tab where you can schedule a `-5m` or `-10m` alarm at any time with a single tap.

### 2. Standalone Manual Alarms & Flexible Scheduling
- **Manual Alarm Creation**: Create alarms directly inside the app without needing any Google Calendar entry.
- **Three Scheduling Modes**:
  - **Repeat Days**: Choose any combination of days of the week (e.g., Mondays only, Mondays & Thursdays, Weekdays `Mon–Fri`, or Everyday). No days are pre-selected by default when creating a new manual alarm.
  - **Specific Future Date**: Pick an exact calendar date in the future for one-off events, travel, or appointments.
  - **One-Time (`Once`)**: Rings at the next occurrence of the selected time today or tomorrow.

### 3. Auto Re-Alarm (Fail-Safe Re-Ringing) & Snooze Controls
- **Configurable Auto Re-Alarm**: If an alarm rings and no action is taken before the auto-silence timeout, SyncAlarm automatically silences and schedules a re-alarm after a configurable gap.
  - **Max Re-Ring Count**: Choose how many times the alarm will re-ring (`1`, `2`, `3`, or `5` times) if left unattended.
  - **Gap Between Re-Rings**: Configure the interval between automatic re-rings (`2`, `3`, `5`, or `10` minutes).
  - **Auto-Silence Timeout**: Set how long an unattended alarm rings (`30`, `60`, or `120` seconds) before pausing for the next re-ring.
- **Customizable Snooze**: Configure default and per-alarm snooze intervals (`5`, `10`, or `15` minutes).

### 4. System Ringtones, Synthesized Tones & Custom MP3 Import
- **Full Phone Ringtone & Alarm List**: Queries Android's `RingtoneManager` to list all default **Alarm**, **Ringtone**, and **Notification** sounds available on your phone.
- **10 Built-In Melodies**: Includes 10 synthesized digital melodies (*Gentle Digital Chime*, *Zen Marimba*, *Energize Beat*, *Classic Bell*, *Sunrise Harp*, *Cosmic Pulse*, *Crystal Cascade*, *Retro Arcade*, *Deep Gong*, and *Morning Birds*).
- **Custom MP3 / Audio Import (`+ Custom MP3`)**: Import any custom `.mp3` or audio file from your device using the system document picker (`ActivityResultContracts.OpenDocument`). Selected files are copied into private app storage so they remain reliably playable even when the device is locked or rebooted.
- **Live Audio & Volume Preview**: Tap any sound in the list or use the volume slider in **Settings** or the **Add/Edit Alarm** dialog to preview the exact tone and volume level immediately.

### 5. Upcoming Alarm Management & Lock-Screen Experience
- **Hero Next-Alarm Card**: Displays the next upcoming alarm at the top of the **Alarms** screen with a quick **Dismiss Next** action.
- **Persistent Upcoming Notification**: Posts an upcoming alarm notification ahead of the trigger time with a direct **Dismiss** button so you can skip an alarm if you are already awake.
- **Full-Screen Lock-Screen Alert**: Uses a foreground `AlarmAudioService` and full-screen activity (`AlarmRingingActivity`) with wake-lock support, vibration patterns, and large **Snooze** and **Dismiss** controls.
- **Boot & Time-Change Resilience**: Automatically restores and reschedules all enabled alarms after a device reboot (`BOOT_COMPLETED`) or timezone/clock adjustment.

---

## Technical Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (`MaterialTheme`, adaptive layouts, dark midnight/indigo theme)
- **Architecture**: MVVM (`AlarmViewModel`, `AlarmRepository`, `SettingsRepository`, `CalendarSyncManager`)
- **Local Persistence**: Room Database (`AppDatabase`, `AlarmDao`, `AlarmEntity`) + `SharedPreferences` for user preferences and custom tone metadata
- **System Integrations**:
  - `AlarmManager.setAlarmClock()` for exact, Doze-proof alarm scheduling
  - `CalendarContract.Instances` & `CalendarContract.Events` with `ContentObserver` for Google Calendar synchronization
  - `RingtoneManager` & `MediaPlayer` + custom PCM WAV synthesis (`AlarmSoundSynthesizer`) for alarm audio
  - Foreground Service (`AlarmAudioService` with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) for reliable alarm playback

---

## Permissions Used

| Permission | Purpose |
| :--- | :--- |
| `READ_CALENDAR` | Reads upcoming timed meetings from the on-device Google Calendar provider to offer `-5m` / `-10m` alarm prompts. |
| `POST_NOTIFICATIONS` | Displays interactive meeting alarm prompts, upcoming alarm notifications, and full-screen ringing alerts. |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Guarantees alarms trigger at the exact scheduled minute via `AlarmManager`. |
| `USE_FULL_SCREEN_INTENT` | Wakes the screen and presents `AlarmRingingActivity` when an alarm triggers while the device is locked. |
| `FOREGROUND_SERVICE` / `WAKE_LOCK` / `VIBRATE` | Keeps alarm audio and haptics running reliably until snoozed or dismissed. |
| `RECEIVE_BOOT_COMPLETED` | Automatically reschedules all active alarms when the phone restarts. |

---

## Building & Running

1. Open the project in **Android Studio** or **Google AI Studio Build**.
2. Sync Gradle and run the `app` configuration on an Android device or emulator (API 24+).
3. Grant **Calendar** and **Notifications** permissions when prompted on first launch to enable automatic Google Calendar meeting detection.
