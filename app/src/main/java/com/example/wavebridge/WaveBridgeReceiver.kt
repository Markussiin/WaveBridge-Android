package com.example.wavebridge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

data class ReceiverStats(
    val running: Boolean = false,
    val status: String = "Stopped",
    val discoveryPort: Int = WaveBridgeProtocol.DISCOVERY_PORT,
    val audioPort: Int = WaveBridgeProtocol.AUDIO_PORT,
    val packets: Long = 0,
    val frames: Long = 0,
    val bytes: Long = 0,
    val invalidPackets: Long = 0,
    val sequenceGaps: Long = 0,
    val lastSender: String = "-",
    val lastCodec: String = "-",
    val lastFrameSamples: Int = 0,
    val error: String? = null,
)

class WaveBridgeReceiver(
    private val context: Context,
    private val onStats: (ReceiverStats) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val statsLock = Any()
    private val assembler = FrameAssembler()

    private var stats = ReceiverStats()
    private var discoverySocket: DatagramSocket? = null
    private var audioSocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var expectedSequence: Long? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        try {
            acquireMulticastLock()
            discoverySocket = bindUdp(WaveBridgeProtocol.DISCOVERY_PORT)
            audioSocket = bindUdp(WaveBridgeProtocol.AUDIO_PORT)
            audioTrack = createAudioTrack().also { it.play() }
            assembler.clear()
            expectedSequence = null

            updateStats {
                ReceiverStats(
                    running = true,
                    status = "Listening",
                    discoveryPort = WaveBridgeProtocol.DISCOVERY_PORT,
                    audioPort = WaveBridgeProtocol.AUDIO_PORT,
                )
            }

            discoveryThread = thread(name = "WaveBridgeDiscovery") { discoveryLoop() }
            audioThread = thread(name = "WaveBridgeAudio") { audioLoop() }
        } catch (ex: Exception) {
            running.set(false)
            closeResources()
            updateStats {
                copy(running = false, status = "Error", error = ex.message ?: ex.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        closeResources()
        updateStats { copy(running = false, status = "Stopped") }
    }

    fun shutdown() {
        stop()
    }

    private fun discoveryLoop() {
        val socket = discoverySocket ?: return
        val buffer = ByteArray(4096)

        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val text = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                val request = parseDiscoveryRequest(text) ?: continue

                val reply = makeDiscoveryReply(request.nonce)
                val bytes = reply.toByteArray(StandardCharsets.UTF_8)
                val response = DatagramPacket(bytes, bytes.size, packet.address, packet.port)
                socket.send(response)

                updateStats {
                    copy(
                        status = "Discovery reply sent",
                        lastSender = "${packet.address.hostAddress}:${packet.port}",
                        error = null,
                    )
                }
            } catch (ex: SocketException) {
                if (running.get()) reportError("Discovery socket error: ${ex.message}")
            } catch (ex: Exception) {
                if (running.get()) reportError("Discovery error: ${ex.message}")
            }
        }
    }

    private fun audioLoop() {
        val socket = audioSocket ?: return
        val track = audioTrack ?: return
        val buffer = ByteArray(WaveBridgeProtocol.MAX_DATAGRAM_BYTES + 512)

        while (running.get()) {
            try {
                val datagram = DatagramPacket(buffer, buffer.size)
                socket.receive(datagram)

                val packet = WaveBridgeProtocol.parseAudioPacket(datagram.data, datagram.length)
                if (packet == null || packet.header.packetType != PacketType.Audio) {
                    updateStats { copy(invalidPackets = invalidPackets + 1) }
                    continue
                }

                if (packet.header.codec != AudioCodec.PcmS16 ||
                    packet.header.sampleRate != WaveBridgeProtocol.NETWORK_SAMPLE_RATE ||
                    packet.header.channels != WaveBridgeProtocol.NETWORK_CHANNELS
                ) {
                    updateStats {
                        copy(
                            invalidPackets = invalidPackets + 1,
                            error = "Unsupported packet: ${packet.header.codec.wireName} ${packet.header.sampleRate}Hz/${packet.header.channels}ch",
                        )
                    }
                    continue
                }

                val gapDelta = sequenceGapCount(packet.header.sequence)
                val frame = assembler.offer(packet)
                if (frame != null) {
                    val written = track.write(frame, 0, frame.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        updateStats { copy(error = "AudioTrack write failed: $written") }
                    } else {
                        updateStats {
                            copy(
                                status = "Playing",
                                frames = frames + 1,
                                error = null,
                            )
                        }
                    }
                }

                updateStats {
                    copy(
                        packets = packets + 1,
                        bytes = bytes + datagram.length,
                        sequenceGaps = sequenceGaps + gapDelta,
                        lastSender = "${datagram.address.hostAddress}:${datagram.port}",
                        lastCodec = packet.header.codec.wireName,
                        lastFrameSamples = packet.header.frameSamples,
                    )
                }
            } catch (ex: SocketException) {
                if (running.get()) reportError("Audio socket error: ${ex.message}")
            } catch (ex: Exception) {
                if (running.get()) reportError("Audio error: ${ex.message}")
            }
        }
    }

    private fun sequenceGapCount(sequence: Long): Long {
        val expected = expectedSequence
        expectedSequence = sequence + 1
        return if (expected != null && sequence != expected) 1 else 0
    }

    private fun parseDiscoveryRequest(text: String): DiscoveryRequest? {
        return try {
            val json = JSONObject(text)
            if (json.optString("type") != "wavebridge.discover") return null
            if (json.optInt("version") != 1) return null
            val nonce = json.optString("nonce")
            if (nonce.isBlank()) return null
            DiscoveryRequest(nonce)
        } catch (_: Exception) {
            null
        }
    }

    private fun makeDiscoveryReply(nonce: String): String {
        return JSONObject()
            .put("type", "wavebridge.phone")
            .put("version", 1)
            .put("nonce", nonce)
            .put("deviceId", deviceId())
            .put("name", deviceName())
            .put("audioPort", WaveBridgeProtocol.AUDIO_PORT)
            .put("maxPayload", WaveBridgeProtocol.MAX_DATAGRAM_BYTES)
            .put("codecs", JSONArray().put(AudioCodec.PcmS16.wireName))
            .toString()
    }

    private fun bindUdp(port: Int): DatagramSocket {
        return DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(port))
        }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            WaveBridgeProtocol.NETWORK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, WaveBridgeProtocol.NETWORK_SAMPLE_RATE * 2 * 2 / 5)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(WaveBridgeProtocol.NETWORK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun acquireMulticastLock() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("WaveBridgeDiscovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun closeResources() {
        discoverySocket?.close()
        audioSocket?.close()
        discoverySocket = null
        audioSocket = null

        audioTrack?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        audioTrack = null

        multicastLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        multicastLock = null
    }

    private fun reportError(message: String) {
        updateStats { copy(status = "Error", error = message) }
    }

    private fun updateStats(block: ReceiverStats.() -> ReceiverStats) {
        val next = synchronized(statsLock) {
            stats = stats.block()
            stats
        }
        onStats(next)
    }

    private fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "android-${androidId ?: Build.MODEL}"
    }

    private fun deviceName(): String {
        return "WaveBridge ${Build.MANUFACTURER} ${Build.MODEL}".replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    private data class DiscoveryRequest(val nonce: String)
}

private class FrameAssembler {
    private data class Key(
        val streamId: Long,
        val sampleIndex: Long,
        val codec: AudioCodec,
        val frameSamples: Int,
    )

    private class PendingFrame(
        val chunks: Array<ByteArray?>,
        val createdAt: Long,
    )

    private val pending = LinkedHashMap<Key, PendingFrame>()

    fun clear() {
        pending.clear()
    }

    fun offer(packet: AudioPacket): ByteArray? {
        val header = packet.header
        if (header.chunkCount == 1) return packet.payload

        val key = Key(header.streamId, header.sampleIndex, header.codec, header.frameSamples)
        val frame = pending.getOrPut(key) {
            PendingFrame(arrayOfNulls(header.chunkCount), System.currentTimeMillis())
        }

        if (header.chunkIndex >= frame.chunks.size) {
            pending.remove(key)
            return null
        }

        frame.chunks[header.chunkIndex] = packet.payload
        pruneOldFrames()

        if (frame.chunks.any { it == null }) return null
        pending.remove(key)

        val totalSize = frame.chunks.sumOf { it?.size ?: 0 }
        val merged = ByteArray(totalSize)
        var offset = 0
        for (chunk in frame.chunks) {
            val bytes = chunk ?: return null
            bytes.copyInto(merged, offset)
            offset += bytes.size
        }
        return merged
    }

    private fun pruneOldFrames() {
        val cutoff = System.currentTimeMillis() - 2000
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.createdAt < cutoff || pending.size > 64) {
                iterator.remove()
            }
        }
    }
}
