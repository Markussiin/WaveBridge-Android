@file:Suppress("DEPRECATION")

package com.example.wavebridge

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlin.math.roundToInt

class AudioEffectsController {
    private var audioSessionId: Int = -1
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null

    fun attach(sessionId: Int, settings: AudioSettings): String {
        if (sessionId <= 0) {
            release()
            return "off"
        }
        if (audioSessionId != sessionId) {
            releaseEffects()
            audioSessionId = sessionId
        }
        return apply(settings)
    }

    fun apply(settings: AudioSettings): String {
        if (!settings.effectsEnabled) {
            release()
            return "off"
        }

        val active = mutableListOf<String>()
        applyBassBoost(settings, active)
        applyEqualizer(settings, active)
        applyLoudness(settings, active)
        applyVirtualizer(settings, active)

        if (active.isEmpty()) {
            release()
            return "off"
        }
        return active.joinToString("+")
    }

    fun release() {
        releaseEffects()
        audioSessionId = -1
    }

    private fun applyBassBoost(settings: AudioSettings, active: MutableList<String>) {
        val strength = settings.bassBoostStrength.coerceIn(0, 1000)
        if (strength == 0) {
            releaseBassBoost()
            return
        }

        val effect = bassBoost ?: runCatching {
            BassBoost(0, audioSessionId).also { bassBoost = it }
        }.getOrNull() ?: return

        val applied = runCatching {
            effect.enabled = true
            if (effect.strengthSupported) {
                effect.setStrength(strength.toShort())
            }
        }.isSuccess

        if (applied) active += "bass"
    }

    private fun applyEqualizer(settings: AudioSettings, active: MutableList<String>) {
        if (!settings.equalizerEnabled) {
            releaseEqualizer()
            return
        }

        val effect = equalizer ?: runCatching {
            Equalizer(0, audioSessionId).also { equalizer = it }
        }.getOrNull() ?: return

        val applied = runCatching {
            val bandCount = effect.numberOfBands.toInt().coerceAtLeast(0)
            if (bandCount == 0) return@runCatching

            val range = effect.bandLevelRange
            val minGain = range[0].toInt()
            val maxGain = range[1].toInt()
            val gains = intArrayOf(
                settings.eqLowGain,
                settings.eqLowMidGain,
                settings.eqMidGain,
                settings.eqHighMidGain,
                settings.eqHighGain,
            )

            for (band in 0 until bandCount) {
                val gainIndex = if (bandCount == 1) {
                    gains.lastIndex / 2
                } else {
                    (band.toFloat() * gains.lastIndex / (bandCount - 1).toFloat()).roundToInt()
                }.coerceIn(0, gains.lastIndex)

                val gain = gains[gainIndex].coerceIn(minGain, maxGain).toShort()
                effect.setBandLevel(band.toShort(), gain)
            }

            effect.enabled = true
        }.isSuccess

        if (applied) active += "eq"
    }

    private fun applyLoudness(settings: AudioSettings, active: MutableList<String>) {
        val gain = settings.loudnessGainMb.coerceIn(0, 1200)
        if (gain == 0) {
            releaseLoudness()
            return
        }

        val effect = loudnessEnhancer ?: runCatching {
            LoudnessEnhancer(audioSessionId).also { loudnessEnhancer = it }
        }.getOrNull() ?: return

        val applied = runCatching {
            effect.setTargetGain(gain)
            effect.enabled = true
        }.isSuccess

        if (applied) active += "loudness"
    }

    private fun applyVirtualizer(settings: AudioSettings, active: MutableList<String>) {
        val strength = settings.virtualizerStrength.coerceIn(0, 1000)
        if (strength == 0) {
            releaseVirtualizer()
            return
        }

        val effect = virtualizer ?: runCatching {
            Virtualizer(0, audioSessionId).also { virtualizer = it }
        }.getOrNull() ?: return

        val applied = runCatching {
            effect.enabled = true
            if (effect.strengthSupported) {
                effect.setStrength(strength.toShort())
            }
        }.isSuccess

        if (applied) active += "virtualizer"
    }

    private fun releaseEffects() {
        releaseBassBoost()
        releaseEqualizer()
        releaseLoudness()
        releaseVirtualizer()
    }

    private fun releaseBassBoost() {
        bassBoost?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        bassBoost = null
    }

    private fun releaseEqualizer() {
        equalizer?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        equalizer = null
    }

    private fun releaseLoudness() {
        loudnessEnhancer?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        loudnessEnhancer = null
    }

    private fun releaseVirtualizer() {
        virtualizer?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        virtualizer = null
    }
}
