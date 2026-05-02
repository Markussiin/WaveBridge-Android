package com.example.wavebridge

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import java.io.ByteArrayOutputStream

class OpusAudioDecoder {
    private var codec: MediaCodec? = null

    fun start() {
        if (codec != null) return
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            WaveBridgeProtocol.NETWORK_SAMPLE_RATE,
            WaveBridgeProtocol.NETWORK_CHANNELS,
        )
        decoder.configure(format, null, null, 0)
        decoder.start()
        codec = decoder
    }

    fun stop() {
        codec?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        codec = null
    }

    fun decode(payload: ByteArray, frameSamples: Int, sampleIndex: Long): ByteArray? {
        val decoder = codec ?: return null
        val inputIndex = decoder.dequeueInputBuffer(1_000)
        if (inputIndex >= 0) {
            val input = decoder.getInputBuffer(inputIndex) ?: return null
            input.clear()
            input.put(payload)
            val presentationUs = sampleIndex * 1_000_000L / WaveBridgeProtocol.NETWORK_SAMPLE_RATE
            decoder.queueInputBuffer(inputIndex, 0, payload.size, presentationUs, 0)
        }

        val output = ByteArrayOutputStream(frameSamples * WaveBridgeProtocol.NETWORK_CHANNELS * 2)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(info, 0)
            when {
                outputIndex >= 0 -> {
                    val buffer = decoder.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(bytes)
                        output.write(bytes)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> break
            }
        }

        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    companion object {
        fun isAvailable(): Boolean {
            return runCatching {
                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                list.codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_OPUS, ignoreCase = true) }
                }
            }.getOrDefault(false)
        }
    }
}
