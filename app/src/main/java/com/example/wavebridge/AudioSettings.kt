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
    }
}

object AudioSettingsStore {
    private const val PREFS = "wavebridge_audio_settings"

    fun load(context: Context): AudioSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val preset = runCatching {
            AudioPreset.valueOf(prefs.getString("preset", AudioPreset.Balanced.name) ?: AudioPreset.Balanced.name)
        }.getOrDefault(AudioPreset.Balanced)

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
            .apply()
    }
}

object ReceiverState {
    var stats by mutableStateOf(ReceiverStats())
    var settings by mutableStateOf(AudioSettings())

    fun loadSettings(context: Context) {
        settings = AudioSettingsStore.load(context)
    }

    fun updateSettings(context: Context, next: AudioSettings) {
        settings = next.copy(preset = next.preset)
        AudioSettingsStore.save(context, settings)
        if (stats.running) {
            WaveBridgeService.applySettings(context)
        }
    }
}
