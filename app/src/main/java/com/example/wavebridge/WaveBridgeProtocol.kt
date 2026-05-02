package com.example.wavebridge

private const val AUDIO_HEADER_SIZE = 48
private const val PROTOCOL_VERSION = 1

enum class PacketType(val wireValue: Int) {
    Audio(0),
    Start(1),
    Stop(2),
    Ping(3),
    Pong(4);

    companion object {
        fun fromWire(value: Int): PacketType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class AudioCodec(val wireValue: Int, val wireName: String) {
    PcmS16(0, "pcm"),
    Opus(1, "opus");

    companion object {
        fun fromWire(value: Int): AudioCodec? = entries.firstOrNull { it.wireValue == value }
    }
}

data class AudioPacketHeader(
    val packetType: PacketType,
    val codec: AudioCodec,
    val flags: Int,
    val streamId: Long,
    val sequence: Long,
    val sampleIndex: Long,
    val sampleRate: Int,
    val channels: Int,
    val frameSamples: Int,
    val chunkIndex: Int,
    val chunkCount: Int,
    val payloadLength: Int,
)

data class AudioPacket(
    val header: AudioPacketHeader,
    val payload: ByteArray,
)

object WaveBridgeProtocol {
    const val DISCOVERY_PORT = 37020
    const val AUDIO_PORT = 37021
    const val MAX_DATAGRAM_BYTES = 1200
    const val NETWORK_SAMPLE_RATE = 48000
    const val NETWORK_CHANNELS = 2

    fun parseAudioPacket(buffer: ByteArray, length: Int): AudioPacket? {
        if (length < AUDIO_HEADER_SIZE) return null
        if (
            buffer[0] != 'P'.code.toByte() ||
            buffer[1] != 'S'.code.toByte() ||
            buffer[2] != 'N'.code.toByte() ||
            buffer[3] != 'K'.code.toByte()
        ) {
            return null
        }

        val version = u16(buffer, 4)
        val headerSize = u16(buffer, 6)
        if (version != PROTOCOL_VERSION || headerSize != AUDIO_HEADER_SIZE || headerSize > length) {
            return null
        }

        val packetType = PacketType.fromWire(u8(buffer, 8)) ?: return null
        val codec = AudioCodec.fromWire(u8(buffer, 9)) ?: return null
        val payloadLength = u16(buffer, 44)
        if (headerSize + payloadLength > length) return null

        val header = AudioPacketHeader(
            packetType = packetType,
            codec = codec,
            flags = u16(buffer, 10),
            streamId = u32(buffer, 12),
            sequence = u64(buffer, 16),
            sampleIndex = u64(buffer, 24),
            sampleRate = u32(buffer, 32).toInt(),
            channels = u16(buffer, 36),
            frameSamples = u16(buffer, 38),
            chunkIndex = u16(buffer, 40),
            chunkCount = u16(buffer, 42),
            payloadLength = payloadLength,
        )

        if (header.chunkCount <= 0 || header.chunkIndex >= header.chunkCount) return null

        val payload = buffer.copyOfRange(headerSize, headerSize + payloadLength)
        return AudioPacket(header, payload)
    }

    fun makeControlPacket(
        type: PacketType,
        codec: AudioCodec,
        streamId: Long,
        sequence: Long,
        frameSamples: Int,
    ): ByteArray {
        require(type != PacketType.Audio) { "control packet type cannot be Audio" }
        val buffer = ByteArray(AUDIO_HEADER_SIZE)
        buffer[0] = 'P'.code.toByte()
        buffer[1] = 'S'.code.toByte()
        buffer[2] = 'N'.code.toByte()
        buffer[3] = 'K'.code.toByte()
        writeU16(buffer, 4, PROTOCOL_VERSION)
        writeU16(buffer, 6, AUDIO_HEADER_SIZE)
        buffer[8] = type.wireValue.toByte()
        buffer[9] = codec.wireValue.toByte()
        writeU16(buffer, 10, 0)
        writeU32(buffer, 12, streamId)
        writeU64(buffer, 16, sequence)
        writeU64(buffer, 24, 0)
        writeU32(buffer, 32, NETWORK_SAMPLE_RATE.toLong())
        writeU16(buffer, 36, NETWORK_CHANNELS)
        writeU16(buffer, 38, frameSamples)
        writeU16(buffer, 40, 0)
        writeU16(buffer, 42, 1)
        writeU16(buffer, 44, 0)
        writeU16(buffer, 46, 0)
        return buffer
    }

    private fun u8(buffer: ByteArray, offset: Int): Int = buffer[offset].toInt() and 0xff

    private fun u16(buffer: ByteArray, offset: Int): Int {
        return u8(buffer, offset) or (u8(buffer, offset + 1) shl 8)
    }

    private fun u32(buffer: ByteArray, offset: Int): Long {
        return u8(buffer, offset).toLong() or
            (u8(buffer, offset + 1).toLong() shl 8) or
            (u8(buffer, offset + 2).toLong() shl 16) or
            (u8(buffer, offset + 3).toLong() shl 24)
    }

    private fun u64(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 7 downTo 0) {
            value = (value shl 8) or u8(buffer, offset + index).toLong()
        }
        return value
    }

    private fun writeU16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xff).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun writeU32(buffer: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) {
            buffer[offset + index] = ((value ushr (index * 8)) and 0xff).toByte()
        }
    }

    private fun writeU64(buffer: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            buffer[offset + index] = ((value ushr (index * 8)) and 0xff).toByte()
        }
    }
}
