package com.example.wavebridge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.TreeMap
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
    val underruns: Long = 0,
    val silenceFrames: Long = 0,
    val driftCorrections: Long = 0,
    val queuedFrames: Int = 0,
    val lastSender: String = "-",
    val lastCodec: String = "-",
    val lastFrameSamples: Int = 0,
    val audioRoute: String = "-",
    val powerMode: String = "Idle",
    val effectsStatus: String = "off",
    val presetName: String = AudioPreset.Balanced.title,
    val configuredLatencyMs: Int = 140,
    val audioTrackBufferMs: Int = 200,
    val error: String? = null,
)

class WaveBridgeReceiver(
    private val context: Context,
    private val onStats: (ReceiverStats) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val statsLock = Any()
    private val trackLock = Any()
    private val assembler = FrameAssembler()
    private val jitterBuffer = PcmJitterBuffer()
    private val effectsController = AudioEffectsController()

    private var stats = ReceiverStats()
    private var discoverySocket: DatagramSocket? = null
    private var audioSocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null
    private var audioThread: Thread? = null
    private var playbackThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var expectedSequence: Long? = null
    private var opusDecoder: OpusAudioDecoder? = null
    private var settings = ReceiverState.settings
    private var routeCallbackRegistered = false

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            rebuildAudioTrack("Audio route changed")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            rebuildAudioTrack("Audio route changed")
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        try {
            acquirePowerLocks()
            discoverySocket = bindUdp(WaveBridgeProtocol.DISCOVERY_PORT)
            audioSocket = bindUdp(WaveBridgeProtocol.AUDIO_PORT)
            val effectsStatus = synchronized(trackLock) {
                audioTrack = createAudioTrack().also { it.play() }
                applyAudioEffectsLocked()
            }
            if (settings.advertiseOpus && OpusAudioDecoder.isAvailable()) {
                opusDecoder = runCatching { OpusAudioDecoder().also { it.start() } }.getOrNull()
            }
            if (settings.routeRecovery) {
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
                routeCallbackRegistered = true
            }
            assembler.clear()
            jitterBuffer.reset()
            jitterBuffer.applySettings(settings)
            expectedSequence = null

            updateStats {
                ReceiverStats(
                    running = true,
                    status = "Waiting for PC",
                    discoveryPort = WaveBridgeProtocol.DISCOVERY_PORT,
                    audioPort = WaveBridgeProtocol.AUDIO_PORT,
                    audioRoute = currentRouteName(),
                    powerMode = activePowerMode(),
                    effectsStatus = effectsStatus,
                    presetName = settings.preset.title,
                    configuredLatencyMs = settings.maxLatencyMs,
                    audioTrackBufferMs = settings.audioTrackBufferMs,
                )
            }

            discoveryThread = thread(name = "WaveBridgeDiscovery") { discoveryLoop() }
            audioThread = thread(name = "WaveBridgeAudioRx") { audioLoop() }
            playbackThread = thread(name = "WaveBridgePlayback") { playbackLoop() }
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
        updateStats { copy(running = false, status = "Stopped", powerMode = "Idle", effectsStatus = "off", queuedFrames = 0) }
    }

    fun shutdown() {
        stop()
    }

    fun applySettings(next: AudioSettings) {
        val previous = settings
        settings = next
        jitterBuffer.applySettings(next)
        if (!running.get()) {
            updateStats {
                copy(
                    presetName = next.preset.title,
                    configuredLatencyMs = next.maxLatencyMs,
                    audioTrackBufferMs = next.audioTrackBufferMs,
                    powerMode = "Idle",
                    effectsStatus = if (next.effectsEnabled) "pending" else "off",
                )
            }
            return
        }

        if (powerLocksNeedRebuild(previous, next)) {
            rebuildPowerLocks()
        }

        val effectsStatus = if (audioTrackNeedsRebuild(previous, next)) {
            rebuildAudioTrack("Settings applied")
        } else {
            applyAudioEffects()
        }

        if (next.advertiseOpus && opusDecoder == null && OpusAudioDecoder.isAvailable()) {
            opusDecoder = runCatching { OpusAudioDecoder().also { it.start() } }.getOrNull()
        } else if (!next.advertiseOpus) {
            opusDecoder?.stop()
            opusDecoder = null
        }
        updateRouteCallbackRegistration()
        updateStats {
            copy(
                presetName = next.preset.title,
                configuredLatencyMs = next.maxLatencyMs,
                audioTrackBufferMs = next.audioTrackBufferMs,
                powerMode = activePowerMode(),
                effectsStatus = effectsStatus,
            )
        }
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
                socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))

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
        val buffer = ByteArray(WaveBridgeProtocol.MAX_DATAGRAM_BYTES + 512)

        while (running.get()) {
            try {
                val datagram = DatagramPacket(buffer, buffer.size)
                socket.receive(datagram)

                val packet = WaveBridgeProtocol.parseAudioPacket(datagram.data, datagram.length)
                if (packet == null) {
                    updateStats { copy(invalidPackets = invalidPackets + 1) }
                    continue
                }

                val sender = "${datagram.address.hostAddress}:${datagram.port}"
                when (packet.header.packetType) {
                    PacketType.Start -> handleStart(packet, sender)
                    PacketType.Stop -> handleStop(sender)
                    PacketType.Ping -> handlePing(socket, datagram, packet, sender)
                    PacketType.Pong -> Unit
                    PacketType.Audio -> handleAudioPacket(packet, datagram.length, sender)
                }
            } catch (ex: SocketException) {
                if (running.get()) reportError("Audio socket error: ${ex.message}")
            } catch (ex: Exception) {
                if (running.get()) reportError("Audio error: ${ex.message}")
            }
        }
    }

    private fun playbackLoop() {
        while (running.get()) {
            val result = jitterBuffer.nextFrame()
            when (result) {
                is PlaybackResult.Waiting -> jitterBuffer.waitForFrames(100)
                is PlaybackResult.Frame -> {
                    writePcm(result.data)
                    updateStats {
                        copy(
                            status = "Playing",
                            frames = frames + 1,
                            queuedFrames = jitterBuffer.queuedFrames,
                            error = null,
                        )
                    }
                }
                is PlaybackResult.Silence -> {
                    writePcm(result.data)
                    updateStats {
                        copy(
                            status = "Recovering",
                            frames = frames + 1,
                            underruns = underruns + 1,
                            silenceFrames = silenceFrames + 1,
                            queuedFrames = jitterBuffer.queuedFrames,
                        )
                    }
                }
                is PlaybackResult.DriftDrop -> {
                    updateStats {
                        copy(
                            status = "Latency trim",
                            driftCorrections = driftCorrections + 1,
                            queuedFrames = jitterBuffer.queuedFrames,
                        )
                    }
                }
            }
        }
    }

    private fun handleStart(packet: AudioPacket, sender: String) {
        assembler.clear()
        jitterBuffer.reset()
        jitterBuffer.applySettings(settings)
        expectedSequence = null
        updateStats {
            copy(
                status = "Connected",
                lastSender = sender,
                lastCodec = packet.header.codec.wireName,
                lastFrameSamples = packet.header.frameSamples,
                queuedFrames = 0,
                presetName = settings.preset.title,
                configuredLatencyMs = settings.maxLatencyMs,
                audioTrackBufferMs = settings.audioTrackBufferMs,
                error = null,
            )
        }
    }

    private fun handleStop(sender: String) {
        assembler.clear()
        jitterBuffer.reset()
        jitterBuffer.applySettings(settings)
        updateStats {
            copy(
                status = "Waiting for PC",
                lastSender = sender,
                queuedFrames = 0,
                error = null,
            )
        }
    }

    private fun handlePing(socket: DatagramSocket, datagram: DatagramPacket, packet: AudioPacket, sender: String) {
        val pong = WaveBridgeProtocol.makeControlPacket(
            PacketType.Pong,
            packet.header.codec,
            packet.header.streamId,
            packet.header.sequence,
            packet.header.frameSamples,
        )
        socket.send(DatagramPacket(pong, pong.size, datagram.address, datagram.port))
        updateStats {
            copy(status = if (frames > 0) "Playing" else "Connected", lastSender = sender, error = null)
        }
    }

    private fun handleAudioPacket(packet: AudioPacket, datagramLength: Int, sender: String) {
        if (packet.header.sampleRate != WaveBridgeProtocol.NETWORK_SAMPLE_RATE ||
            packet.header.channels != WaveBridgeProtocol.NETWORK_CHANNELS
        ) {
            updateStats {
                copy(
                    invalidPackets = invalidPackets + 1,
                    error = "Unsupported format: ${packet.header.sampleRate}Hz/${packet.header.channels}ch",
                )
            }
            return
        }

        val gapDelta = sequenceGapCount(packet.header.sequence)
        val assembled = assembler.offer(packet) ?: run {
            updatePacketStats(packet, datagramLength, sender, gapDelta)
            return
        }

        val pcmFrame = when (packet.header.codec) {
            AudioCodec.PcmS16 -> assembled
            AudioCodec.Opus -> decodeOpus(packet, assembled) ?: run {
                updateStats { copy(invalidPackets = invalidPackets + 1, error = "Opus decoder unavailable or waiting") }
                updatePacketStats(packet, datagramLength, sender, gapDelta)
                return
            }
        }

        jitterBuffer.offer(
            PcmFrame(
                sampleIndex = packet.header.sampleIndex,
                frameSamples = packet.header.frameSamples,
                data = pcmFrame,
            ),
        )
        updatePacketStats(packet, datagramLength, sender, gapDelta)
    }

    private fun updatePacketStats(packet: AudioPacket, datagramLength: Int, sender: String, gapDelta: Long) {
        updateStats {
            copy(
                packets = packets + 1,
                bytes = bytes + datagramLength,
                sequenceGaps = sequenceGaps + gapDelta,
                queuedFrames = jitterBuffer.queuedFrames,
                lastSender = sender,
                lastCodec = packet.header.codec.wireName,
                lastFrameSamples = packet.header.frameSamples,
            )
        }
    }

    private fun decodeOpus(packet: AudioPacket, payload: ByteArray): ByteArray? {
        return opusDecoder?.decode(payload, packet.header.frameSamples, packet.header.sampleIndex)
    }

    private fun sequenceGapCount(sequence: Long): Long {
        val expected = expectedSequence
        expectedSequence = sequence + 1
        return if (expected != null && sequence != expected) 1 else 0
    }

    private fun writePcm(data: ByteArray) {
        synchronized(trackLock) {
            effectsController.processPcm16InPlace(data)
            val written = audioTrack?.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING) ?: AudioTrack.ERROR_INVALID_OPERATION
            if (written < 0) {
                reportError("AudioTrack write failed: $written")
            }
        }
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
        val codecs = JSONArray().put(AudioCodec.PcmS16.wireName)
        if (settings.advertiseOpus && opusDecoder != null) {
            codecs.put(AudioCodec.Opus.wireName)
        }

        return JSONObject()
            .put("type", "wavebridge.phone")
            .put("version", 1)
            .put("nonce", nonce)
            .put("deviceId", deviceId())
            .put("name", deviceName())
            .put("audioPort", WaveBridgeProtocol.AUDIO_PORT)
            .put("maxPayload", WaveBridgeProtocol.MAX_DATAGRAM_BYTES)
            .put("codecs", codecs)
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
        val requestedBuffer = WaveBridgeProtocol.NETWORK_SAMPLE_RATE *
            WaveBridgeProtocol.NETWORK_CHANNELS *
            2 *
            settings.audioTrackBufferMs / 1000
        val bufferSize = max(minBuffer, requestedBuffer)

        val builder = AudioTrack.Builder()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(
                if (settings.lowLatencyTrack) {
                    AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                } else {
                    AudioTrack.PERFORMANCE_MODE_NONE
                },
            )
        }

        return builder.build()
    }

    private fun rebuildAudioTrack(reason: String): String {
        val effectsStatus = synchronized(trackLock) {
            effectsController.release()
            audioTrack?.let {
                runCatching { it.pause() }
                runCatching { it.flush() }
                runCatching { it.release() }
            }
            audioTrack = createAudioTrack().also { it.play() }
            applyAudioEffectsLocked()
        }
        updateStats { copy(status = reason, audioRoute = currentRouteName(), effectsStatus = effectsStatus) }
        return effectsStatus
    }

    private fun applyAudioEffects(): String = synchronized(trackLock) {
        applyAudioEffectsLocked()
    }

    private fun applyAudioEffectsLocked(): String {
        val sessionId = audioTrack?.audioSessionId ?: return "off"
        return effectsController.attach(sessionId, settings)
    }

    private fun audioTrackNeedsRebuild(previous: AudioSettings, next: AudioSettings): Boolean {
        return previous.audioTrackBufferMs != next.audioTrackBufferMs ||
            previous.lowLatencyTrack != next.lowLatencyTrack
    }

    private fun powerLocksNeedRebuild(previous: AudioSettings, next: AudioSettings): Boolean {
        return previous.wifiLowLatencyLock != next.wifiLowLatencyLock ||
            previous.cpuWakeLock != next.cpuWakeLock
    }

    private fun rebuildPowerLocks() {
        releasePowerLocks()
        acquirePowerLocks()
    }

    private fun updateRouteCallbackRegistration() {
        if (settings.routeRecovery && !routeCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
            routeCallbackRegistered = true
        } else if (!settings.routeRecovery && routeCallbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
            routeCallbackRegistered = false
        }
    }

    private fun acquirePowerLocks() {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("WaveBridgeDiscovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        if (settings.wifiLowLatencyLock) {
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(wifiMode, "WaveBridgeAudio")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (settings.cpuWakeLock) {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveBridge:AudioReceiver")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun activePowerMode(): String {
        val parts = mutableListOf<String>()
        if (multicastLock?.isHeld == true) parts += "multicast"
        if (wifiLock?.isHeld == true) parts += "wifi-low-latency"
        if (wakeLock?.isHeld == true) parts += "wake"
        return if (parts.isEmpty()) "Idle" else parts.joinToString("+")
    }

    private fun closeResources() {
        discoverySocket?.close()
        audioSocket?.close()
        discoverySocket = null
        audioSocket = null

        if (routeCallbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
            routeCallbackRegistered = false
        }
        opusDecoder?.stop()
        opusDecoder = null

        synchronized(trackLock) {
            effectsController.release()
            audioTrack?.let {
                runCatching { it.pause() }
                runCatching { it.flush() }
                runCatching { it.release() }
            }
            audioTrack = null
        }

        releasePowerLocks()

        assembler.clear()
        jitterBuffer.reset()
    }

    private fun releasePowerLocks() {
        multicastLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wifiLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        multicastLock = null
        wifiLock = null
        wakeLock = null
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

    private fun currentRouteName(): String {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: devices.firstOrNull()
        return preferred?.productName?.toString() ?: "default"
    }

    private data class DiscoveryRequest(val nonce: String)
}

private data class PcmFrame(
    val sampleIndex: Long,
    val frameSamples: Int,
    val data: ByteArray,
)

private sealed class PlaybackResult {
    data object Waiting : PlaybackResult()
    data class Frame(val data: ByteArray) : PlaybackResult()
    data class Silence(val data: ByteArray) : PlaybackResult()
    data object DriftDrop : PlaybackResult()
}

private class PcmJitterBuffer {
    private val lock = Object()
    private val frames = TreeMap<Long, PcmFrame>()
    private var expectedSampleIndex: Long? = null
    private var frameSamples = 240
    private var startFrames = 8
    private var highWaterFrames = 24
    private var maxFrames = 64
    private var silenceFill = true
    private var latencyTrim = true

    val queuedFrames: Int
        get() = synchronized(lock) { frames.size }

    fun reset() {
        synchronized(lock) {
            frames.clear()
            expectedSampleIndex = null
            frameSamples = 240
            lock.notifyAll()
        }
    }

    fun applySettings(settings: AudioSettings) {
        synchronized(lock) {
            val frameMs = (frameSamples * 1000 / WaveBridgeProtocol.NETWORK_SAMPLE_RATE).coerceAtLeast(1)
            startFrames = (settings.startBufferMs / frameMs).coerceIn(1, 64)
            highWaterFrames = (settings.maxLatencyMs / frameMs).coerceIn(startFrames + 1, 160)
            maxFrames = (highWaterFrames * 2).coerceAtLeast(highWaterFrames + 1)
            silenceFill = settings.silenceFill
            latencyTrim = settings.latencyTrim
        }
    }

    fun offer(frame: PcmFrame) {
        synchronized(lock) {
            frameSamples = frame.frameSamples.coerceAtLeast(1)
            frames[frame.sampleIndex] = frame
            while (frames.size > maxFrames) {
                frames.pollFirstEntry()
            }
            lock.notifyAll()
        }
    }

    fun waitForFrames(timeoutMs: Long) {
        synchronized(lock) {
            if (frames.size < startFrames) {
                lock.wait(timeoutMs)
            }
        }
    }

    fun nextFrame(): PlaybackResult {
        synchronized(lock) {
            if (expectedSampleIndex == null) {
                if (frames.size < startFrames) {
                    return PlaybackResult.Waiting
                }
                expectedSampleIndex = frames.firstKey()
            }

            val expected = expectedSampleIndex ?: return PlaybackResult.Waiting
            if (latencyTrim && frames.size > highWaterFrames) {
                frames.remove(expected)
                expectedSampleIndex = expected + frameSamples
                return PlaybackResult.DriftDrop
            }

            val exact = frames.remove(expected)
            if (exact != null) {
                expectedSampleIndex = expected + exact.frameSamples
                return PlaybackResult.Frame(exact.data)
            }

            val firstKey = frames.firstKeyOrNull() ?: return PlaybackResult.Waiting
            return if (firstKey > expected) {
                expectedSampleIndex = expected + frameSamples
                if (silenceFill) {
                    PlaybackResult.Silence(ByteArray(frameSamples * WaveBridgeProtocol.NETWORK_CHANNELS * 2))
                } else {
                    PlaybackResult.Waiting
                }
            } else {
                while (frames.isNotEmpty() && frames.firstKey() < expected) {
                    frames.pollFirstEntry()
                }
                PlaybackResult.Waiting
            }
        }
    }

    private fun TreeMap<Long, PcmFrame>.firstKeyOrNull(): Long? = if (isEmpty()) null else firstKey()
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
