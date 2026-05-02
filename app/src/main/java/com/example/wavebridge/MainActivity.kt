package com.example.wavebridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wavebridge.ui.theme.WaveBridgeTheme
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReceiverState.loadSettings(applicationContext)
        requestNotificationPermissionIfNeeded()

        enableEdgeToEdge()
        setContent {
            WaveBridgeTheme {
                WaveBridgeApp(
                    stats = ReceiverState.stats,
                    settings = ReceiverState.settings,
                    onStart = { WaveBridgeService.start(applicationContext) },
                    onStop = { WaveBridgeService.stop(applicationContext) },
                    onSettings = { ReceiverState.updateSettings(applicationContext, it) },
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 37021)
    }
}

@Composable
private fun WaveBridgeApp(
    stats: ReceiverStats,
    settings: AudioSettings,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettings: (AudioSettings) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Monitor", "Quality", "Effects", "Power", "Advanced")

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(stats = stats, onStart = onStart, onStop = onStop)
                PrimaryTabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (tab) {
                        0 -> MonitorTab(stats)
                        1 -> QualityTab(settings, onSettings)
                        2 -> EffectsTab(settings, stats, onSettings)
                        3 -> PowerTab(settings, onSettings)
                        else -> AdvancedTab(settings, stats, onSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    stats: ReceiverStats,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (stats.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "W",
                color = if (stats.running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("WaveBridge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stats.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stats.running) {
            OutlinedButton(onClick = onStop) { Text("Stop") }
        } else {
            Button(onClick = onStart) { Text("Start") }
        }
    }
}

@Composable
private fun MonitorTab(stats: ReceiverStats) {
    StatusPanel(stats)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Packets", stats.packets.toString(), Modifier.weight(1f))
        MetricCard("Frames", stats.frames.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Queued", stats.queuedFrames.toString(), Modifier.weight(1f))
        MetricCard("Underruns", stats.underruns.toString(), Modifier.weight(1f))
    }
    DetailsPanel(stats)
}

@Composable
private fun QualityTab(settings: AudioSettings, onSettings: (AudioSettings) -> Unit) {
    SectionCard("Presets", "Choose the audio profile that matches the network and use case.") {
        AudioPreset.entries.filter { it != AudioPreset.Custom }.forEach { preset ->
            PresetRow(
                preset = preset,
                selected = settings.preset == preset,
                onClick = { onSettings(AudioSettings.preset(settings, preset)) },
            )
        }
    }

    SectionCard("Latency and Buffering", "Lower values feel faster; higher values survive rough Wi-Fi better.") {
        SettingSlider(
            label = "Start buffer",
            value = settings.startBufferMs,
            range = 10f..120f,
            step = 5,
            unit = "ms",
            onChange = { onSettings(settings.copy(preset = AudioPreset.Custom, startBufferMs = it)) },
        )
        SettingSlider(
            label = "Maximum latency",
            value = settings.maxLatencyMs,
            range = 50f..360f,
            step = 10,
            unit = "ms",
            onChange = { onSettings(settings.copy(preset = AudioPreset.Custom, maxLatencyMs = it.coerceAtLeast(settings.startBufferMs + 10))) },
        )
        SettingSlider(
            label = "AudioTrack buffer",
            value = settings.audioTrackBufferMs,
            range = 80f..500f,
            step = 20,
            unit = "ms",
            onChange = { onSettings(settings.copy(preset = AudioPreset.Custom, audioTrackBufferMs = it)) },
        )
    }

    SectionCard("Playback Quality", "These affect how the receiver handles real network timing.") {
        SettingSwitch("Low-latency AudioTrack", "Prefer Android's low-latency output path when available.", settings.lowLatencyTrack) {
            onSettings(settings.copy(preset = AudioPreset.Custom, lowLatencyTrack = it))
        }
        SettingSwitch("Silence fill", "Insert silence for missing frames to keep timing stable.", settings.silenceFill) {
            onSettings(settings.copy(preset = AudioPreset.Custom, silenceFill = it))
        }
        SettingSwitch("Latency trim", "Drop old buffered frames when latency grows too high.", settings.latencyTrim) {
            onSettings(settings.copy(preset = AudioPreset.Custom, latencyTrim = it))
        }
        SettingSwitch("Advertise Opus", "Allow PC Opus mode if this phone has an Android decoder.", settings.advertiseOpus) {
            onSettings(settings.copy(preset = AudioPreset.Custom, advertiseOpus = it))
        }
    }
}

@Composable
private fun EffectsTab(
    settings: AudioSettings,
    stats: ReceiverStats,
    onSettings: (AudioSettings) -> Unit,
) {
    SectionCard("Effect Presets", "Tone presets use Android's built-in audio effects and stay optional.") {
        AudioEffectPreset.entries.filter { it != AudioEffectPreset.Custom }.forEach { preset ->
            EffectPresetRow(
                preset = preset,
                selected = settings.effectPreset == preset,
                onClick = { onSettings(AudioSettings.effectPreset(settings, preset)) },
            )
        }
    }

    SectionCard("Processing", "Manual controls apply to the active output session and release when disabled.") {
        SettingSwitch("Enable effects", "Turn off for the cleanest signal and lowest processing cost.", settings.effectsEnabled) { enabled ->
            onSettings(
                if (enabled) {
                    settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true)
                } else {
                    AudioSettings.effectPreset(settings, AudioEffectPreset.Flat)
                },
            )
        }
        DetailRow("Active effects", stats.effectsStatus)
        SettingSlider(
            label = "Bass boost",
            value = settings.bassBoostStrength,
            range = 0f..1000f,
            step = 25,
            unit = "strength",
            onChange = {
                onSettings(
                    settings.copy(
                        effectPreset = AudioEffectPreset.Custom,
                        effectsEnabled = true,
                        bassBoostStrength = it,
                    ),
                )
            },
        )
        SettingDbSlider(
            label = "Loudness",
            value = settings.loudnessGainMb,
            range = 0..600,
            step = 25,
            onChange = {
                onSettings(
                    settings.copy(
                        effectPreset = AudioEffectPreset.Custom,
                        effectsEnabled = true,
                        loudnessGainMb = it,
                    ),
                )
            },
        )
        SettingSlider(
            label = "Virtualizer",
            value = settings.virtualizerStrength,
            range = 0f..1000f,
            step = 25,
            unit = "strength",
            onChange = {
                onSettings(
                    settings.copy(
                        effectPreset = AudioEffectPreset.Custom,
                        effectsEnabled = true,
                        virtualizerStrength = it,
                    ),
                )
            },
        )
    }

    SectionCard("Equalizer", "Five simple tone bands are mapped to the phone's available EQ bands.") {
        SettingSwitch("Enable equalizer", "Use the device equalizer after the stream is decoded.", settings.equalizerEnabled) {
            onSettings(
                settings.copy(
                    effectPreset = AudioEffectPreset.Custom,
                    effectsEnabled = it || settings.effectsEnabled,
                    equalizerEnabled = it,
                ),
            )
        }
        SettingDbSlider("Low", settings.eqLowGain, -600..600, 25) {
            onSettings(settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true, equalizerEnabled = true, eqLowGain = it))
        }
        SettingDbSlider("Low mid", settings.eqLowMidGain, -600..600, 25) {
            onSettings(settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true, equalizerEnabled = true, eqLowMidGain = it))
        }
        SettingDbSlider("Mid", settings.eqMidGain, -600..600, 25) {
            onSettings(settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true, equalizerEnabled = true, eqMidGain = it))
        }
        SettingDbSlider("High mid", settings.eqHighMidGain, -600..600, 25) {
            onSettings(settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true, equalizerEnabled = true, eqHighMidGain = it))
        }
        SettingDbSlider("High", settings.eqHighGain, -600..600, 25) {
            onSettings(settings.copy(effectPreset = AudioEffectPreset.Custom, effectsEnabled = true, equalizerEnabled = true, eqHighGain = it))
        }
    }
}

@Composable
private fun PowerTab(settings: AudioSettings, onSettings: (AudioSettings) -> Unit) {
    SectionCard("Runtime Power", "Locks are held only while the receiver is started.") {
        SettingSwitch("Wi-Fi low-latency lock", "Keeps Wi-Fi responsive for real-time audio. Uses more battery.", settings.wifiLowLatencyLock) {
            onSettings(settings.copy(preset = AudioPreset.Custom, wifiLowLatencyLock = it))
        }
        SettingSwitch("CPU wake lock", "Prevents playback from sleeping while the screen is off.", settings.cpuWakeLock) {
            onSettings(settings.copy(preset = AudioPreset.Custom, cpuWakeLock = it))
        }
        SettingSwitch("Notification stats", "Refresh foreground notification status while receiving.", settings.notificationStats) {
            onSettings(settings.copy(preset = AudioPreset.Custom, notificationStats = it))
        }
    }

    SectionCard("Recommended Modes", "Use Battery saver for long listening, Balanced for most sessions, Low latency for interactive audio.") {
        AssistChip(onClick = { onSettings(AudioSettings.preset(settings, AudioPreset.BatterySaver)) }, label = { Text("Battery saver") })
        AssistChip(onClick = { onSettings(AudioSettings.preset(settings, AudioPreset.Balanced)) }, label = { Text("Balanced") })
        AssistChip(onClick = { onSettings(AudioSettings.preset(settings, AudioPreset.LowLatency)) }, label = { Text("Low latency") })
    }
}

@Composable
private fun AdvancedTab(
    settings: AudioSettings,
    stats: ReceiverStats,
    onSettings: (AudioSettings) -> Unit,
) {
    SectionCard("Receiver Behavior", "Advanced options for device and route handling.") {
        SettingSwitch("Route recovery", "Rebuild playback when headphones or Bluetooth route changes.", settings.routeRecovery) {
            onSettings(settings.copy(preset = AudioPreset.Custom, routeRecovery = it))
        }
        DetailRow("Active preset", stats.presetName)
        DetailRow("Configured latency", "${stats.configuredLatencyMs} ms")
        DetailRow("AudioTrack buffer", "${stats.audioTrackBufferMs} ms")
        DetailRow("Power mode", stats.powerMode)
    }

    SectionCard("Diagnostics", "Counters help tune a profile for your Wi-Fi.") {
        DetailRow("Sender", stats.lastSender)
        DetailRow("Codec", stats.lastCodec)
        DetailRow("Route", stats.audioRoute)
        DetailRow("Effects", stats.effectsStatus)
        DetailRow("Bytes", stats.bytes.toString())
        DetailRow("Invalid packets", stats.invalidPackets.toString())
        DetailRow("Sequence gaps", stats.sequenceGaps.toString())
        DetailRow("Silence fills", stats.silenceFrames.toString())
        DetailRow("Drift corrections", stats.driftCorrections.toString())
    }
}

@Composable
private fun StatusPanel(stats: ReceiverStats) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (stats.running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (stats.running) "Receiver active" else "Receiver stopped", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Preset ${stats.presetName} | latency ${stats.configuredLatencyMs} ms", style = MaterialTheme.typography.bodyMedium)
            Text("Discovery ${stats.discoveryPort} | Audio ${stats.audioPort}", style = MaterialTheme.typography.bodyMedium)
            if (stats.error != null) {
                Text(stats.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DetailsPanel(stats: ReceiverStats) {
    SectionCard("Session", "Current stream and output state.") {
        DetailRow("Sender", stats.lastSender)
        DetailRow("Codec", stats.lastCodec)
        DetailRow("Frame samples", stats.lastFrameSamples.toString())
        DetailRow("Audio route", stats.audioRoute)
        DetailRow("Power", stats.powerMode)
        DetailRow("Effects", stats.effectsStatus)
        DetailRow("Silence fills", stats.silenceFrames.toString())
        DetailRow("Drift corrections", stats.driftCorrections.toString())
        DetailRow("Sequence gaps", stats.sequenceGaps.toString())
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PresetRow(preset: AudioPreset, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(preset.title, fontWeight = FontWeight.SemiBold)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EffectPresetRow(preset: AudioEffectPreset, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(preset.title, fontWeight = FontWeight.SemiBold)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingDbSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(formatDb(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val stepped = (it / step).roundToInt() * step
                onChange(stepped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

private fun formatDb(value: Int): String {
    val prefix = when {
        value > 0 -> "+"
        value < 0 -> "-"
        else -> ""
    }
    val absolute = abs(value)
    return "$prefix${absolute / 100}.${(absolute % 100) / 10} dB"
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    step: Int,
    unit: String,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("$value $unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val stepped = (it / step).roundToInt() * step
                onChange(stepped.coerceIn(range.start.roundToInt(), range.endInclusive.roundToInt()))
            },
            valueRange = range,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun WaveBridgeAppPreview() {
    WaveBridgeTheme {
        WaveBridgeApp(
            stats = ReceiverStats(
                running = true,
                status = "Playing",
                packets = 2400,
                frames = 2400,
                bytes = 2_304_000,
                queuedFrames = 8,
                lastSender = "192.168.1.24:51244",
                lastCodec = "pcm",
                lastFrameSamples = 240,
                audioRoute = "USB-C headphones",
                powerMode = "multicast+wifi-low-latency+wake",
                effectsStatus = "bass+eq",
            ),
            settings = AudioSettings.preset(AudioPreset.Balanced),
            onStart = {},
            onStop = {},
            onSettings = {},
        )
    }
}
