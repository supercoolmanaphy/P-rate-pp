package com.praticpp.ui

import android.content.Context

/**
 * Persists all EQ settings per-user in SharedPreferences.
 */
class EqRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun getBandLevel(band: Int): Short =
        prefs.getInt("${KEY_BAND_PREFIX}$band", 0).toShort()

    fun setBandLevel(band: Int, levelMb: Short) {
        prefs.edit().putInt("${KEY_BAND_PREFIX}$band", levelMb.toInt()).apply()
    }

    fun setBandLevels(levels: IntArray) {
        val edit = prefs.edit()
        levels.forEachIndexed { i, v -> edit.putInt("${KEY_BAND_PREFIX}$i", v) }
        edit.apply()
    }

    var bassBoostStrength: Short
        get() = prefs.getInt(KEY_BASS_BOOST, 0).toShort()
        set(value) = prefs.edit().putInt(KEY_BASS_BOOST, value.toInt()).apply()

    var virtualizerStrength: Short
        get() = prefs.getInt(KEY_VIRTUALIZER, 0).toShort()
        set(value) = prefs.edit().putInt(KEY_VIRTUALIZER, value.toInt()).apply()

    var loudnessGainMb: Int
        get() = prefs.getInt(KEY_LOUDNESS, 0)
        set(value) = prefs.edit().putInt(KEY_LOUDNESS, value).apply()

    var currentPreset: String
        get() = prefs.getString(KEY_PRESET, EqPreset.FLAT.name) ?: EqPreset.FLAT.name
        set(value) = prefs.edit().putString(KEY_PRESET, value).apply()

    fun applyPreset(preset: EqPreset) {
        setBandLevels(preset.bandLevelsMb)
        currentPreset = preset.name
    }

    companion object {
        private const val PREFS_NAME = "praticpp_eq"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_BAND_PREFIX = "eq_band_"
        private const val KEY_BASS_BOOST = "eq_bass_boost"
        private const val KEY_VIRTUALIZER = "eq_virtualizer"
        private const val KEY_LOUDNESS = "eq_loudness"
        private const val KEY_PRESET = "eq_preset"
    }
}
