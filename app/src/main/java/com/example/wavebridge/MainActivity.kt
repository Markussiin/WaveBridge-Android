package com.example.wavebridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wavebridge.ui.theme.WaveBridgeTheme

class MainActivity : ComponentActivity() {
    private lateinit var receiver: WaveBridgeReceiver
    private var stats by mutableStateOf(ReceiverStats())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = WaveBridgeReceiver(applicationContext) { next ->
            runOnUiThread { stats = next }
        }

        enableEdgeToEdge()
        setContent {
            WaveBridgeTheme {
                ReceiverScreen(
                    stats = stats,
                    onStart = receiver::start,
                    onStop = receiver::stop,
                )
            }
        }
    }

    override fun onDestroy() {
        receiver.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun ReceiverScreen(
    stats: ReceiverStats,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(stats = stats, onStart = onStart, onStop = onStop)
                StatusCard(stats)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Packets", stats.packets.toString(), Modifier.weight(1f))
                    StatTile("Frames", stats.frames.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Bytes", stats.bytes.toString(), Modifier.weight(1f))
                    StatTile("Invalid", stats.invalidPackets.toString(), Modifier.weight(1f))
                }

                DetailCard(stats)
            }
        }
    }
}

@Composable
private fun Header(
    stats: ReceiverStats,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "WaveBridge",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stats.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (stats.running) {
            OutlinedButton(onClick = onStop) {
                Text("Stop")
            }
        } else {
            Button(onClick = onStart) {
                Text("Start")
            }
        }
    }
}

@Composable
private fun StatusCard(stats: ReceiverStats) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (stats.running) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (stats.running) "Receiver active" else "Receiver stopped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Discovery ${stats.discoveryPort} | Audio ${stats.audioPort}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (stats.error != null) {
                Text(
                    text = stats.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DetailCard(stats: ReceiverStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailRow("Sender", stats.lastSender)
            DetailRow("Codec", stats.lastCodec)
            DetailRow("Frame samples", stats.lastFrameSamples.toString())
            DetailRow("Sequence gaps", stats.sequenceGaps.toString())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceiverScreenPreview() {
    WaveBridgeTheme {
        ReceiverScreen(
            stats = ReceiverStats(
                running = true,
                status = "Playing",
                packets = 2400,
                frames = 2400,
                bytes = 2_304_000,
                lastSender = "192.168.1.24:51244",
                lastCodec = "pcm",
                lastFrameSamples = 240,
            ),
            onStart = {},
            onStop = {},
        )
    }
}
