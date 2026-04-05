package com.praticpp.ui

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Wraps Android's built-in audio effects (Equalizer, BassBoost, Virtualizer, LoudnessEnhancer)
 * and attaches them to the current ExoPlayer audio session.
 */
class EqualizerManager(audioSessionId: Int) {

    val equalizer: Equalizer? = tryCreate { Equalizer(PRIORITY, audioSessionId) }
    val bassBoost: BassBoost? = tryCreate { BassBoost(PRIORITY, audioSessionId) }
    val virtualizer: Virtualizer? = tryCreate { Virtualizer(PRIORITY, audioSessionId) }
    val loudnessEnhancer: LoudnessEnhancer? = tryCreate { LoudnessEnhancer(audioSessionId) }

    val numberOfBands: Short get() = equalizer?.numberOfBands ?: 0.toShort()
    val bandLevelRange: ShortArray get() = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)

    fun setEnabled(enabled: Boolean) {
        equalizer?.setEnabled(enabled)
        bassBoost?.setEnabled(enabled)
        virtualizer?.setEnabled(enabled)
        loudnessEnhancer?.setEnabled(enabled)
    }

    fun setBandLevel(band: Short, levelMb: Short) {
        equalizer?.setBandLevel(band, levelMb)
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
    }

    fun setLoudnessGain(gainMb: Int) {
        loudnessEnhancer?.setTargetGain(gainMb)
    }

    /** Returns center frequency for each band in Hz (converts from mHz). */
    fun getCenterFreqHz(band: Short): String {
        val mHz = equalizer?.getCenterFreq(band) ?: 0
        val hz = mHz / 1000
        return if (hz >= 1000) "${hz / 1000}.${(hz % 1000) / 100}k" else "${hz}Hz"
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
    }

    companion object {
        private const val PRIORITY = 0
        private const val TAG = "EqualizerManager"

        private inline fun <T> tryCreate(block: () -> T): T? =
            try { block() } catch (e: Exception) {
                Log.w(TAG, "Audio effect unavailable: ${e.message}")
                null
            }
    }
}
