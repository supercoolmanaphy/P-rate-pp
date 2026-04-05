package com.praticpp.ui

/**
 * Built-in EQ presets. Band levels are in millibels (mB).
 * Five bands correspond to Android Equalizer defaults: ~60Hz, ~230Hz, ~910Hz, ~3.6kHz, ~14kHz.
 */
enum class EqPreset(val displayName: String, val bandLevelsMb: IntArray) {
    FLAT("FLAT",          intArrayOf(    0,    0,    0,    0,    0)),
    BASS_BOOST("BASS BOOST", intArrayOf( 800,  600,  200, -100, -200)),
    VOCAL_CLARITY("VOCAL",   intArrayOf(-200,  0,    400,  600,  300)),
    CLUB("CLUB",          intArrayOf(  200,  600,  800,  600,  200)),
    LO_FI("LO-FI",        intArrayOf(  500,  400, -200, -500, -700));

    companion object {
        fun fromName(name: String): EqPreset? = entries.firstOrNull { it.name == name }
    }
}
