package com.example.wavebridge

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AudioPreset(
    val title: String,
    val summary: String,
) {
    LowLatency("Low latency", "Small buffer for gaming and calls"),
    Balanced("Balanced", "Best default for stable Wi-Fi"),
    Studio("Studio", "Maximum stability and fewer dropouts"),
    BatterySaver("Battery saver", "Lower power, more relaxed buffering"),
    Custom("Custom", "Manual receiver tuning"),
}

enum class AudioEffectPreset(
    val title: String,
    val summary: String,
) {
    Flat("Flat", "No tonal changes"),
    BassBoost("Bass boost", "Strong low-end lift"),
    Warm("Warm", "Fuller bass with softer highs"),
    Bright("Bright", "Clearer treble and presence"),
    Voice("Voice", "Focused mids for speech"),
    Movie("Movie", "Wide, lively playback"),
    Custom("Custom", "Manual effect tuning"),
}

data class AudioSettings(
    val preset: AudioPreset = AudioPreset.Balanced,
    val startBufferMs: Int = 40,
    val maxLatencyMs: Int = 140,
    val audioTrackBufferMs: Int = 200,
    val lowLatencyTrack: Boolean = true,
    val silenceFill: Boolean = true,
    val latencyTrim: Boolean = true,
    val routeRecovery: Boolean = true,
    val advertiseOpus: Boolean = false,
    val wifiLowLatencyLock: Boolean = true,
    val cpuWakeLock: Boolean = true,
    val notificationStats: Boolean = true,
    val effectsEnabled: Boolean = false,
    val effectPreset: AudioEffectPreset = AudioEffectPreset.Flat,
    val bassBoostStrength: Int = 0,
    val loudnessGainMb: Int = 0,
    val virtualizerStrength: Int = 0,
    val equalizerEnabled: Boolean = false,
    val eqLowGain: Int = 0,
    val eqLowMidGain: Int = 0,
    val eqMidGain: Int = 0,
    val eqHighMidGain: Int = 0,
    val eqHighGain: Int = 0,
) {
    companion object {
        fun preset(preset: AudioPreset): AudioSettings {
            return when (preset) {
                AudioPreset.LowLatency -> AudioSettings(
                    preset = preset,
                    startBufferMs = 20,
                    maxLatencyMs = 70,
                    audioTrackBufferMs = 120,
                    lowLatencyTrack = true,
                    silenceFill = true,
                    latencyTrim = true,
                    routeRecovery = true,
                    advertiseOpus = false,
                    wifiLowLatencyLock = true,
                    cpuWakeLock = true,
                    notificationStats = true,
                )
                AudioPreset.Balanced -> AudioSettings(
                    preset = preset,
                    startBufferMs = 40,
                    maxLatencyMs = 140,
                    audioTrackBufferMs = 200,
                    lowLatencyTrack = true,
                    silenceFill = true,
                    latencyTrim = true,
                    routeRecovery = true,
                    advertiseOpus = false,
                    wifiLowLatencyLock = true,
                    cpuWakeLock = true,
                    notificationStats = true,
                )
                AudioPreset.Studio -> AudioSettings(
                    preset = preset,
                    startBufferMs = 80,
                    maxLatencyMs = 260,
                    audioTrackBufferMs = 360,
                    lowLatencyTrack = false,
                    silenceFill = true,
                    latencyTrim = true,
                    routeRecovery = true,
                    advertiseOpus = false,
                    wifiLowLatencyLock = true,
                    cpuWakeLock = true,
                    notificationStats = true,
                )
                AudioPreset.BatterySaver -> AudioSettings(
                    preset = preset,
                    startBufferMs = 70,
                    maxLatencyMs = 220,
                    audioTrackBufferMs = 280,
                    lowLatencyTrack = false,
                    silenceFill = true,
                    latencyTrim = true,
                    routeRecovery = true,
                    advertiseOpus = false,
                    wifiLowLatencyLock = false,
                    cpuWakeLock = true,
                    notificationStats = false,
                )
                AudioPreset.Custom -> AudioSettings(preset = preset)
            }
        }

        fun preset(current: AudioSettings, preset: AudioPreset): AudioSettings {
            return preset(preset).copy(
                effectsEnabled = current.effectsEnabled,
                effectPreset = current.effectPreset,
                bassBoostStrength = current.bassBoostStrength,
                loudnessGainMb = current.loudnessGainMb,
                virtualizerStrength = current.virtualizerStrength,
                equalizerEnabled = current.equalizerEnabled,
                eqLowGain = current.eqLowGain,
                eqLowMidGain = current.eqLowMidGain,
                eqMidGain = current.eqMidGain,
                eqHighMidGain = current.eqHighMidGain,
                eqHighGain = current.eqHighGain,
            )
        }

        fun effectPreset(current: AudioSettings, preset: AudioEffectPreset): AudioSettings {
            return when (preset) {
                AudioEffectPreset.Flat -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = false,
                    bassBoostStrength = 0,
                    loudnessGainMb = 0,
                    virtualizerStrength = 0,
                    equalizerEnabled = false,
                    eqLowGain = 0,
                    eqLowMidGain = 0,
                    eqMidGain = 0,
                    eqHighMidGain = 0,
                    eqHighGain = 0,
                )
                AudioEffectPreset.BassBoost -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                    bassBoostStrength = 700,
                    loudnessGainMb = 100,
                    virtualizerStrength = 0,
                    equalizerEnabled = true,
                    eqLowGain = 500,
                    eqLowMidGain = 250,
                    eqMidGain = 0,
                    eqHighMidGain = -100,
                    eqHighGain = -150,
                )
                AudioEffectPreset.Warm -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                    bassBoostStrength = 350,
                    loudnessGainMb = 50,
                    virtualizerStrength = 0,
                    equalizerEnabled = true,
                    eqLowGain = 250,
                    eqLowMidGain = 180,
                    eqMidGain = 0,
                    eqHighMidGain = -80,
                    eqHighGain = -120,
                )
                AudioEffectPreset.Bright -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                    bassBoostStrength = 0,
                    loudnessGainMb = 80,
                    virtualizerStrength = 0,
                    equalizerEnabled = true,
                    eqLowGain = -150,
                    eqLowMidGain = -60,
                    eqMidGain = 0,
                    eqHighMidGain = 200,
                    eqHighGain = 380,
                )
                AudioEffectPreset.Voice -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                    bassBoostStrength = 0,
                    loudnessGainMb = 120,
                    virtualizerStrength = 0,
                    equalizerEnabled = true,
                    eqLowGain = -250,
                    eqLowMidGain = -100,
                    eqMidGain = 250,
                    eqHighMidGain = 320,
                    eqHighGain = 120,
                )
                AudioEffectPreset.Movie -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                    bassBoostStrength = 500,
                    loudnessGainMb = 180,
                    virtualizerStrength = 350,
                    equalizerEnabled = true,
                    eqLowGain = 250,
                    eqLowMidGain = 100,
                    eqMidGain = 0,
                    eqHighMidGain = 150,
                    eqHighGain = 180,
                )
                AudioEffectPreset.Custom -> current.copy(
                    effectPreset = preset,
                    effectsEnabled = true,
                )
            }
        }
    }
}

fun AudioSettings.requestedEffectsStatus(): String {
    if (!effectsEnabled) return "off"

    val parts = mutableListOf<String>()
    if (bassBoostStrength > 0) parts += "bass"
    if (equalizerEnabled && listOf(eqLowGain, eqLowMidGain, eqMidGain, eqHighMidGain, eqHighGain).any { it != 0 }) {
        parts += "eq"
    }
    if (loudnessGainMb > 0) parts += "loudness"
    if (virtualizerStrength > 0) parts += "wide"

    return if (parts.isEmpty()) "flat" else "software:${parts.joinToString("+")}"
}

object AudioSettingsStore {
    private const val PREFS = "wavebridge_audio_settings"

    fun load(context: Context): AudioSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val preset = runCatching {
            AudioPreset.valueOf(prefs.getString("preset", AudioPreset.Balanced.name) ?: AudioPreset.Balanced.name)
        }.getOrDefault(AudioPreset.Balanced)
        val effectPreset = runCatching {
            AudioEffectPreset.valueOf(
                prefs.getString("effectPreset", AudioEffectPreset.Flat.name) ?: AudioEffectPreset.Flat.name,
            )
        }.getOrDefault(AudioEffectPreset.Flat)

        return AudioSettings(
            preset = preset,
            startBufferMs = prefs.getInt("startBufferMs", AudioSettings.preset(preset).startBufferMs),
            maxLatencyMs = prefs.getInt("maxLatencyMs", AudioSettings.preset(preset).maxLatencyMs),
            audioTrackBufferMs = prefs.getInt("audioTrackBufferMs", AudioSettings.preset(preset).audioTrackBufferMs),
            lowLatencyTrack = prefs.getBoolean("lowLatencyTrack", AudioSettings.preset(preset).lowLatencyTrack),
            silenceFill = prefs.getBoolean("silenceFill", AudioSettings.preset(preset).silenceFill),
            latencyTrim = prefs.getBoolean("latencyTrim", AudioSettings.preset(preset).latencyTrim),
            routeRecovery = prefs.getBoolean("routeRecovery", AudioSettings.preset(preset).routeRecovery),
            advertiseOpus = prefs.getBoolean("advertiseOpus", AudioSettings.preset(preset).advertiseOpus),
            wifiLowLatencyLock = prefs.getBoolean("wifiLowLatencyLock", AudioSettings.preset(preset).wifiLowLatencyLock),
            cpuWakeLock = prefs.getBoolean("cpuWakeLock", AudioSettings.preset(preset).cpuWakeLock),
            notificationStats = prefs.getBoolean("notificationStats", AudioSettings.preset(preset).notificationStats),
            effectsEnabled = prefs.getBoolean("effectsEnabled", false),
            effectPreset = effectPreset,
            bassBoostStrength = prefs.getInt("bassBoostStrength", 0),
            loudnessGainMb = prefs.getInt("loudnessGainMb", 0),
            virtualizerStrength = prefs.getInt("virtualizerStrength", 0),
            equalizerEnabled = prefs.getBoolean("equalizerEnabled", false),
            eqLowGain = prefs.getInt("eqLowGain", 0),
            eqLowMidGain = prefs.getInt("eqLowMidGain", 0),
            eqMidGain = prefs.getInt("eqMidGain", 0),
            eqHighMidGain = prefs.getInt("eqHighMidGain", 0),
            eqHighGain = prefs.getInt("eqHighGain", 0),
        )
    }

    fun save(context: Context, settings: AudioSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("preset", settings.preset.name)
            .putInt("startBufferMs", settings.startBufferMs)
            .putInt("maxLatencyMs", settings.maxLatencyMs)
            .putInt("audioTrackBufferMs", settings.audioTrackBufferMs)
            .putBoolean("lowLatencyTrack", settings.lowLatencyTrack)
            .putBoolean("silenceFill", settings.silenceFill)
            .putBoolean("latencyTrim", settings.latencyTrim)
            .putBoolean("routeRecovery", settings.routeRecovery)
            .putBoolean("advertiseOpus", settings.advertiseOpus)
            .putBoolean("wifiLowLatencyLock", settings.wifiLowLatencyLock)
            .putBoolean("cpuWakeLock", settings.cpuWakeLock)
            .putBoolean("notificationStats", settings.notificationStats)
            .putBoolean("effectsEnabled", settings.effectsEnabled)
            .putString("effectPreset", settings.effectPreset.name)
            .putInt("bassBoostStrength", settings.bassBoostStrength)
            .putInt("loudnessGainMb", settings.loudnessGainMb)
            .putInt("virtualizerStrength", settings.virtualizerStrength)
            .putBoolean("equalizerEnabled", settings.equalizerEnabled)
            .putInt("eqLowGain", settings.eqLowGain)
            .putInt("eqLowMidGain", settings.eqLowMidGain)
            .putInt("eqMidGain", settings.eqMidGain)
            .putInt("eqHighMidGain", settings.eqHighMidGain)
            .putInt("eqHighGain", settings.eqHighGain)
            .apply()
    }
}

object ReceiverState {
    var stats by mutableStateOf(ReceiverStats())
    var settings by mutableStateOf(AudioSettings())

    fun loadSettings(context: Context) {
        settings = AudioSettingsStore.load(context)
        if (!stats.running) {
            stats = stats.copy(
                presetName = settings.preset.title,
                configuredLatencyMs = settings.maxLatencyMs,
                audioTrackBufferMs = settings.audioTrackBufferMs,
                effectsStatus = settings.requestedEffectsStatus(),
            )
        }
    }

    fun updateSettings(context: Context, next: AudioSettings) {
        settings = next.copy(preset = next.preset)
        AudioSettingsStore.save(context, settings)
        if (stats.running) {
            WaveBridgeService.applySettings(context)
        } else {
            stats = stats.copy(
                presetName = settings.preset.title,
                configuredLatencyMs = settings.maxLatencyMs,
                audioTrackBufferMs = settings.audioTrackBufferMs,
                effectsStatus = settings.requestedEffectsStatus(),
            )
        }
    }
}
