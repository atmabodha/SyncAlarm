package com.example.alarm

import android.content.Context
import android.database.Cursor
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class AlarmSoundOption(
    val title: String,
    val uri: String,
    val category: String // "Custom MP3", "Phone Ringtones", "Built-in Tones"
)

object AlarmSoundSynthesizer {

    const val TONE_GENTLE_CHIME = "Gentle Digital Chime"
    const val TONE_ZEN_MARIMBA = "Zen Marimba"
    const val TONE_ENERGIZE_BEAT = "Energize Beat"
    const val TONE_CLASSIC_BELL = "Classic Bell"
    const val TONE_SUNRISE_HARP = "Sunrise Harp"
    const val TONE_COSMIC_PULSE = "Cosmic Pulse"
    const val TONE_RETRO_ARCADE = "Retro 8-Bit Arcade"
    const val TONE_CRYSTAL_PIANO = "Crystal Piano"
    const val TONE_BRASS_HORN = "Morning Brass Fanfare"
    const val TONE_PEACEFUL_FLUTE = "Peaceful Bamboo Flute"

    val AVAILABLE_TONES = listOf(
        TONE_GENTLE_CHIME,
        TONE_ZEN_MARIMBA,
        TONE_ENERGIZE_BEAT,
        TONE_CLASSIC_BELL,
        TONE_SUNRISE_HARP,
        TONE_COSMIC_PULSE,
        TONE_RETRO_ARCADE,
        TONE_CRYSTAL_PIANO,
        TONE_BRASS_HORN,
        TONE_PEACEFUL_FLUTE
    )

    private const val SAMPLE_RATE = 44100

    fun isBuiltInSynthesizedUri(uri: String?): Boolean {
        return uri.isNullOrBlank() || uri.startsWith("synthesized://")
    }

    fun getSynthesizedUri(title: String): String = "synthesized://$title"

    /**
     * Loads all sound options:
     * 1. User's custom added MP3/audio files
     * 2. All system alarm, ringtone, and notification tones from Android's RingtoneManager
     * 3. 10 built-in synthesized alarm melodies
     */
    fun getAllSoundOptions(
        context: Context,
        customTones: List<AlarmSoundOption>
    ): List<AlarmSoundOption> {
        val result = mutableListOf<AlarmSoundOption>()

        // 1. Custom MP3s added by user
        result.addAll(customTones)

        // 2. Query default phone ringtones & alarm sounds from RingtoneManager
        val seenUris = mutableSetOf<String>()
        val types = listOf(
            RingtoneManager.TYPE_ALARM to "Phone Alarm",
            RingtoneManager.TYPE_RINGTONE to "Phone Ringtone",
            RingtoneManager.TYPE_NOTIFICATION to "Phone Notification"
        )

        for ((type, categoryLabel) in types) {
            try {
                val manager = RingtoneManager(context)
                manager.setType(type)
                val cursor: Cursor? = manager.cursor
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val pos = cursor.position
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "System Tone"
                        val ringtoneUri = manager.getRingtoneUri(pos)?.toString() ?: continue
                        if (seenUris.add(ringtoneUri)) {
                            result.add(
                                AlarmSoundOption(
                                    title = title,
                                    uri = ringtoneUri,
                                    category = categoryLabel
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AlarmSoundSynthesizer", "Could not query RingtoneManager for type $type: ${e.message}")
            }
        }

        // 3. Built-in synthesized alarm tones
        for (toneName in AVAILABLE_TONES) {
            result.add(
                AlarmSoundOption(
                    title = toneName,
                    uri = getSynthesizedUri(toneName),
                    category = "Built-in Tone"
                )
            )
        }

        return result
    }

    /**
     * Imports a user-selected custom MP3 / audio URI into internal storage so it works reliably
     * even when the phone is locked or rebooted.
     */
    fun importCustomAudioFile(context: Context, sourceUri: Uri): AlarmSoundOption? {
        try {
            var displayName = "Custom Audio.mp3"
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }

            val cleanTitle = displayName.substringBeforeLast(".").ifBlank { displayName }
            val customDir = File(context.filesDir, "custom_tones").apply { mkdirs() }
            val safeFileName = "custom_${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val destFile = File(customDir, safeFileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0L) {
                return AlarmSoundOption(
                    title = cleanTitle,
                    uri = destFile.absolutePath,
                    category = "Custom MP3"
                )
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundSynthesizer", "Error importing custom MP3 file", e)
        }
        return null
    }

    /**
     * Configures a MediaPlayer's data source for custom MP3 files, system RingtoneManager URIs,
     * or built-in synthesized WAV melodies.
     */
    fun setMediaPlayerDataSource(
        context: Context,
        mediaPlayer: MediaPlayer,
        toneTitle: String?,
        toneUri: String?
    ) {
        if (!isBuiltInSynthesizedUri(toneUri)) {
            val nonNullUri = toneUri!!
            try {
                if (nonNullUri.startsWith("/")) {
                    val file = File(nonNullUri)
                    if (file.exists()) {
                        mediaPlayer.setDataSource(file.absolutePath)
                        return
                    }
                } else {
                    mediaPlayer.setDataSource(context, Uri.parse(nonNullUri))
                    return
                }
            } catch (e: Exception) {
                Log.w("AlarmSoundSynthesizer", "Failed to load custom/system URI $nonNullUri, falling back to built-in tone", e)
            }
        }

        val resolvedTitle = when {
            !toneUri.isNullOrBlank() && toneUri.startsWith("synthesized://") ->
                toneUri.removePrefix("synthesized://")
            !toneTitle.isNullOrBlank() -> toneTitle
            else -> TONE_GENTLE_CHIME
        }
        val wavFile = getOrCreateToneFile(context, resolvedTitle)
        mediaPlayer.setDataSource(wavFile.absolutePath)
    }

    /**
     * Returns a playable WAV file for the given built-in tone title. Generates it if not yet cached.
     */
    fun getOrCreateToneFile(context: Context, toneTitle: String): File {
        val safeName = toneTitle.lowercase().replace(Regex("[^a-z0-9]"), "_")
        val file = File(context.filesDir, "alarm_tone_${safeName}_v2.wav")
        if (file.exists() && file.length() > 1000) {
            return file
        }

        val pcmSamples = when (toneTitle) {
            TONE_ZEN_MARIMBA -> generateZenMarimba()
            TONE_ENERGIZE_BEAT -> generateEnergizeBeat()
            TONE_CLASSIC_BELL -> generateClassicBell()
            TONE_SUNRISE_HARP -> generateSunriseHarp()
            TONE_COSMIC_PULSE -> generateCosmicPulse()
            TONE_RETRO_ARCADE -> generateRetroArcade()
            TONE_CRYSTAL_PIANO -> generateCrystalPiano()
            TONE_BRASS_HORN -> generateBrassHorn()
            TONE_PEACEFUL_FLUTE -> generatePeacefulFlute()
            else -> generateGentleChime()
        }

        writeWavFile(file, pcmSamples)
        return file
    }

    private fun generateGentleChime(): ShortArray {
        val durationSec = 2.6
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val notes = listOf(
            Triple(0.0, 523.25, 1.2),
            Triple(0.30, 659.25, 1.2),
            Triple(0.60, 783.99, 1.2),
            Triple(0.90, 987.77, 1.2),
            Triple(1.20, 1046.50, 1.35)
        )
        for ((startSec, freq, noteDur) in notes) {
            addHarmonicNote(samples, startSec, freq, noteDur, 3.2, doubleArrayOf(1.0, 0.35, 0.15))
        }
        return normalizeToPcm(samples)
    }

    private fun generateZenMarimba(): ShortArray {
        val durationSec = 2.6
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val notes = listOf(
            Triple(0.0, 293.66, 0.45),
            Triple(0.25, 369.99, 0.45),
            Triple(0.50, 440.00, 0.45),
            Triple(0.75, 493.88, 0.55),
            Triple(1.10, 440.00, 0.45),
            Triple(1.35, 369.99, 0.45),
            Triple(1.60, 587.33, 0.95)
        )
        for ((startSec, freq, noteDur) in notes) {
            addHarmonicNote(samples, startSec, freq, noteDur, 7.5, doubleArrayOf(1.0, 0.0, 0.0, 0.5))
        }
        return normalizeToPcm(samples)
    }

    private fun generateEnergizeBeat(): ShortArray {
        val durationSec = 2.4
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val step = 0.18
        val freqs = listOf(
            440.0, 554.37, 659.25, 880.0,
            659.25, 880.0, 1108.73, 880.0,
            440.0, 554.37, 659.25, 880.0
        )
        freqs.forEachIndexed { index, freq ->
            addHarmonicNote(samples, index * step, freq, step * 0.9, 6.0, doubleArrayOf(1.0, 0.5, 0.33, 0.25))
        }
        return normalizeToPcm(samples)
    }

    private fun generateClassicBell(): ShortArray {
        val durationSec = 2.4
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val bursts = listOf(0.0 to 0.85, 1.15 to 2.0)
        for ((burstStart, burstEnd) in bursts) {
            val startIdx = (burstStart * SAMPLE_RATE).toInt()
            val endIdx = (burstEnd * SAMPLE_RATE).toInt()
            for (idx in startIdx until endIdx) {
                if (idx >= totalSamples) break
                val t = (idx - startIdx).toDouble() / SAMPLE_RATE
                val strikeIndex = (t * 22.0).toInt()
                val localT = t - (strikeIndex / 22.0)
                val baseFreq = if (strikeIndex % 2 == 0) 1320.0 else 1760.0
                val strikeEnv = exp(-28.0 * localT)
                val wave = sin(2.0 * PI * baseFreq * t) +
                        0.6 * sin(2.0 * PI * (baseFreq * 1.414) * t) +
                        0.35 * sin(2.0 * PI * (baseFreq * 2.73) * t)
                samples[idx] += wave * strikeEnv * 0.45
            }
        }
        return normalizeToPcm(samples)
    }

    private fun generateSunriseHarp(): ShortArray {
        val durationSec = 2.8
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val freqs = listOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25, 783.99, 1046.50)
        freqs.forEachIndexed { idx, freq ->
            addHarmonicNote(samples, idx * 0.16, freq, 1.2, 2.8, doubleArrayOf(1.0, 0.4, 0.2, 0.1))
        }
        return normalizeToPcm(samples)
    }

    private fun generateCosmicPulse(): ShortArray {
        val durationSec = 2.5
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val notes = listOf(
            Triple(0.0, 220.0, 0.7),
            Triple(0.35, 329.63, 0.7),
            Triple(0.70, 493.88, 0.7),
            Triple(1.05, 659.25, 1.3)
        )
        for ((startSec, freq, dur) in notes) {
            val startIdx = (startSec * SAMPLE_RATE).toInt()
            val len = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = startIdx + i
                if (idx >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                val vibrato = 1.0 + 0.012 * sin(2.0 * PI * 6.0 * t)
                val env = (t / 0.08).coerceAtMost(1.0) * exp(-2.2 * t)
                samples[idx] += (sin(2.0 * PI * freq * vibrato * t) + 0.5 * sin(2.0 * PI * freq * 1.5 * t)) * env * 0.4
            }
        }
        return normalizeToPcm(samples)
    }

    private fun generateRetroArcade(): ShortArray {
        val durationSec = 2.2
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val notes = listOf(
            523.25 to 0.12, 659.25 to 0.12, 783.99 to 0.12, 1046.50 to 0.24,
            783.99 to 0.12, 1046.50 to 0.45,
            587.33 to 0.12, 739.99 to 0.12, 880.00 to 0.12, 1174.66 to 0.45
        )
        var cursorSec = 0.0
        for ((freq, dur) in notes) {
            val startIdx = (cursorSec * SAMPLE_RATE).toInt()
            val len = (dur * 0.85 * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = startIdx + i
                if (idx >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                val square = if (sin(2.0 * PI * freq * t) >= 0.0) 0.6 else -0.6
                samples[idx] += square * exp(-2.0 * t)
            }
            cursorSec += dur
        }
        return normalizeToPcm(samples)
    }

    private fun generateCrystalPiano(): ShortArray {
        val durationSec = 2.6
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val chords = listOf(
            0.0 to listOf(349.23, 440.00, 523.25, 659.25),   // Fmaj7
            0.65 to listOf(392.00, 493.88, 587.33, 783.99),  // G6
            1.30 to listOf(261.63, 392.00, 523.25, 659.25, 1046.50) // Cmaj
        )
        for ((startSec, freqs) in chords) {
            for (f in freqs) {
                addHarmonicNote(samples, startSec, f, 1.1, 4.0, doubleArrayOf(1.0, 0.45, 0.2, 0.08))
            }
        }
        return normalizeToPcm(samples)
    }

    private fun generateBrassHorn(): ShortArray {
        val durationSec = 2.4
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val fanfare = listOf(
            Triple(0.0, 392.00, 0.22),
            Triple(0.24, 392.00, 0.22),
            Triple(0.48, 392.00, 0.22),
            Triple(0.72, 523.25, 0.55),
            Triple(1.30, 392.00, 0.25),
            Triple(1.58, 523.25, 0.75)
        )
        for ((startSec, freq, dur) in fanfare) {
            addHarmonicNote(samples, startSec, freq, dur, 2.5, doubleArrayOf(0.8, 1.0, 0.85, 0.6, 0.4, 0.25))
        }
        return normalizeToPcm(samples)
    }

    private fun generatePeacefulFlute(): ShortArray {
        val durationSec = 2.6
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = DoubleArray(totalSamples)
        val notes = listOf(
            Triple(0.0, 587.33, 0.55),
            Triple(0.55, 659.25, 0.45),
            Triple(1.00, 783.99, 0.60),
            Triple(1.60, 880.00, 0.95)
        )
        for ((startSec, freq, dur) in notes) {
            val startIdx = (startSec * SAMPLE_RATE).toInt()
            val len = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = startIdx + i
                if (idx >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                val attack = (t / 0.09).coerceAtMost(1.0)
                val release = ((dur - t) / 0.12).coerceIn(0.0, 1.0)
                val vibrato = 1.0 + 0.008 * sin(2.0 * PI * 5.2 * t)
                val wave = sin(2.0 * PI * freq * vibrato * t) + 0.18 * sin(2.0 * PI * freq * 2.0 * t)
                samples[idx] += wave * attack * release * 0.55
            }
        }
        return normalizeToPcm(samples)
    }

    private fun addHarmonicNote(
        samples: DoubleArray,
        startSec: Double,
        freq: Double,
        noteDur: Double,
        decayRate: Double,
        harmonics: DoubleArray
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt()
        val len = (noteDur * SAMPLE_RATE).toInt()
        for (i in 0 until len) {
            val idx = startIdx + i
            if (idx >= samples.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.012).coerceAtMost(1.0)
            val env = attack * exp(-decayRate * t)
            var wave = 0.0
            for (h in harmonics.indices) {
                val amp = harmonics[h]
                if (amp > 0.0) {
                    wave += amp * sin(2.0 * PI * (freq * (h + 1)) * t)
                }
            }
            samples[idx] += wave * env * 0.35
        }
    }

    private fun normalizeToPcm(samples: DoubleArray): ShortArray {
        val maxAbs = samples.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.01) ?: 1.0
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val normalized = (samples[i] / maxAbs) * 0.90
            out[i] = (normalized * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun writeWavFile(file: File, pcmData: ShortArray) {
        val byteDataSize = pcmData.size * 2
        val totalDataLen = byteDataSize + 36

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(byteDataSize)
            fos.write(header.array())

            val pcmBuffer = ByteBuffer.allocate(byteDataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                pcmBuffer.putShort(sample)
            }
            fos.write(pcmBuffer.array())
        }
    }
}
