package com.example.wavebridge

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class AudioEffectsController {
    private var processingEnabled = false
    private var bassAmount = 0f
    private var bassLeft = 0f
    private var bassRight = 0f
    private var bassAlpha = 0f
    private var loudnessGain = 1f
    private var stereoWidth = 1f
    private var leftEq = emptyList<Biquad>()
    private var rightEq = emptyList<Biquad>()

    fun attach(@Suppress("UNUSED_PARAMETER") sessionId: Int, settings: AudioSettings): String {
        return apply(settings)
    }

    fun apply(settings: AudioSettings): String {
        release()
        if (!settings.effectsEnabled) return "off"

        bassAmount = settings.bassBoostStrength.coerceIn(0, 1000) / 1000f * 1.65f
        bassAlpha = onePoleAlpha(155f, WaveBridgeProtocol.NETWORK_SAMPLE_RATE.toFloat())
        loudnessGain = 10f.pow(settings.loudnessGainMb.coerceIn(0, 600) / 2000f)
        stereoWidth = 1f + settings.virtualizerStrength.coerceIn(0, 1000) / 1000f * 0.65f

        val eqGains = if (settings.equalizerEnabled) {
            listOf(
                EqBand(75f, 0.85f, settings.eqLowGain),
                EqBand(240f, 0.95f, settings.eqLowMidGain),
                EqBand(1000f, 1.0f, settings.eqMidGain),
                EqBand(3600f, 1.0f, settings.eqHighMidGain),
                EqBand(9500f, 0.85f, settings.eqHighGain),
            ).filter { it.gainMb != 0 }
        } else {
            emptyList()
        }

        leftEq = eqGains.map { Biquad.peaking(it.frequencyHz, it.q, it.gainMb / 100f, WaveBridgeProtocol.NETWORK_SAMPLE_RATE.toFloat()) }
        rightEq = eqGains.map { Biquad.peaking(it.frequencyHz, it.q, it.gainMb / 100f, WaveBridgeProtocol.NETWORK_SAMPLE_RATE.toFloat()) }

        processingEnabled = bassAmount > 0f ||
            leftEq.isNotEmpty() ||
            loudnessGain > 1.0001f ||
            stereoWidth > 1.0001f

        return settings.requestedEffectsStatus()
    }

    fun processPcm16InPlace(data: ByteArray) {
        if (!processingEnabled) return

        var offset = 0
        while (offset + 3 < data.size) {
            var left = readPcm16(data, offset) / 32768f
            var right = readPcm16(data, offset + 2) / 32768f

            for (filter in leftEq) left = filter.process(left)
            for (filter in rightEq) right = filter.process(right)

            if (bassAmount > 0f) {
                bassLeft += bassAlpha * (left - bassLeft)
                bassRight += bassAlpha * (right - bassRight)
                left += bassLeft * bassAmount
                right += bassRight * bassAmount
            }

            if (stereoWidth > 1.0001f) {
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f * stereoWidth
                left = mid + side
                right = mid - side
            }

            if (loudnessGain > 1.0001f) {
                left *= loudnessGain
                right *= loudnessGain
            }

            writePcm16(data, offset, left)
            writePcm16(data, offset + 2, right)
            offset += 4
        }
    }

    fun release() {
        processingEnabled = false
        bassAmount = 0f
        bassLeft = 0f
        bassRight = 0f
        bassAlpha = 0f
        loudnessGain = 1f
        stereoWidth = 1f
        leftEq = emptyList()
        rightEq = emptyList()
    }

    private fun readPcm16(data: ByteArray, offset: Int): Int {
        val value = (data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)
        return value.toShort().toInt()
    }

    private fun writePcm16(data: ByteArray, offset: Int, value: Float) {
        val sample = (value.coerceIn(-1f, 1f) * 32767f)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        data[offset] = (sample and 0xff).toByte()
        data[offset + 1] = ((sample shr 8) and 0xff).toByte()
    }

    private fun onePoleAlpha(cutoffHz: Float, sampleRate: Float): Float {
        return (1.0 - exp((-2.0 * PI * cutoffHz / sampleRate).toDouble())).toFloat()
    }

    private data class EqBand(
        val frequencyHz: Float,
        val q: Float,
        val gainMb: Int,
    )

    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var z1 = 0f
        private var z2 = 0f

        fun process(input: Float): Float {
            val output = input * b0 + z1
            z1 = input * b1 + z2 - a1 * output
            z2 = input * b2 - a2 * output
            return output
        }

        companion object {
            fun peaking(frequencyHz: Float, q: Float, gainDb: Float, sampleRate: Float): Biquad {
                val omega = 2.0 * PI * frequencyHz / sampleRate
                val alpha = sin(omega) / (2.0 * q)
                val cosOmega = cos(omega)
                val amplitude = 10.0.pow(gainDb / 40.0)

                val b0 = 1.0 + alpha * amplitude
                val b1 = -2.0 * cosOmega
                val b2 = 1.0 - alpha * amplitude
                val a0 = 1.0 + alpha / amplitude
                val a1 = -2.0 * cosOmega
                val a2 = 1.0 - alpha / amplitude

                return Biquad(
                    b0 = (b0 / a0).toFloat(),
                    b1 = (b1 / a0).toFloat(),
                    b2 = (b2 / a0).toFloat(),
                    a1 = (a1 / a0).toFloat(),
                    a2 = (a2 / a0).toFloat(),
                )
            }
        }
    }
}
