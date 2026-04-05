package com.praticpp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.praticpp.databinding.ActivityMainBinding
import com.praticpp.databinding.ActivityPlaylistBinding
import kotlin.math.abs

object ThemeManager {

    private const val PREFS_NAME = "praticpp_theme"
    private const val KEY_HUE = "accent_hue"

    /** Default hue: neon cyan (~195°) */
    const val DEFAULT_HUE = 195f

    var currentHue: Float = DEFAULT_HUE
        private set

    // ── Persistence ──────────────────────────────────────────────────────────

    fun load(context: Context) {
        currentHue = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_HUE, DEFAULT_HUE)
    }

    fun save(context: Context, hue: Float) {
        currentHue = hue.coerceIn(0f, 359.9f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_HUE, currentHue).apply()
    }

    // ── Color calculations ────────────────────────────────────────────────────

    /** Primary neon accent: full saturation, 50% lightness */
    fun accentColor(): Int = hslToArgb(currentHue, 1f, 0.5f)

    /** Dimmed accent for unselected borders and dim states */
    fun accentDimColor(): Int = hslToArgb(currentHue, 1f, 0.21f)

    /** Secondary accent: hue shifted +105° (mirrors default cyan→magenta offset) */
    fun secondaryColor(): Int = hslToArgb((currentHue + 105f) % 360f, 1f, 0.5f)

    /** Convert HSL (h 0–360, s 0–1, l 0–1) to an ARGB color integer. */
    fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs(h / 60f % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f  -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else     -> Triple(c, 0f, x)
        }
        return Color.argb(
            255,
            ((r + m) * 255).toInt().coerceIn(0, 255),
            ((g + m) * 255).toInt().coerceIn(0, 255),
            ((b + m) * 255).toInt().coerceIn(0, 255)
        )
    }

    // ── Apply to activity bindings ────────────────────────────────────────────

    fun applyToMainBinding(binding: ActivityMainBinding) {
        val accent = accentColor()
        val secondary = secondaryColor()

        // App title + version
        binding.tvAppTitle.applyAccentGlow(accent, 12f)
        binding.tvAppVersion.setTextColor(secondary)
        binding.tvAppVersion.setShadowLayer(4f, 0f, 0f, secondary)

        // Palette button
        ImageViewCompat.setImageTintList(binding.btnColorPicker, ColorStateList.valueOf(accent))

        // Section headers
        binding.tvSectionRecent.applyAccentGlow(accent, 6f)
        binding.tvSectionPlaylists.applyAccentGlow(accent, 6f)

        // Create-playlist button
        binding.btnCreatePlaylist.setTextColor(accent)
        binding.btnCreatePlaylist.setShadowLayer(6f, 0f, 0f, accent)
        binding.btnCreatePlaylist.background = buildNeonButtonBg(binding.btnCreatePlaylist.context)

        // Loading progress
        binding.progressLoadingRecentSongs.indeterminateTintList = ColorStateList.valueOf(accent)

        // Now-playing bar background (neon border)
        binding.nowPlayingBar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#08080F"))
            setStroke(dpToPx(binding.nowPlayingBar.context, 1), accent)
        }

        // Now-playing title
        binding.tvNowPlayingTitle.applyAccentGlow(accent, 8f)

        // Control buttons
        ImageViewCompat.setImageTintList(binding.btnPrev, ColorStateList.valueOf(accent))
        ImageViewCompat.setImageTintList(binding.btnNext, ColorStateList.valueOf(accent))
        binding.btnPlayPause.background = buildPlayButtonBg(binding.btnPlayPause.context, accent)

        // Seek bar
        binding.seekBar.progressTintList = ColorStateList.valueOf(accent)
        binding.seekBar.thumbTintList = ColorStateList.valueOf(secondary)
    }

    fun applyToPlaylistBinding(binding: ActivityPlaylistBinding) {
        val accent = accentColor()
        val secondary = secondaryColor()

        // Toolbar
        binding.toolbar.setTitleTextColor(accent)
        binding.toolbar.navigationIconTintList = ColorStateList.valueOf(accent)

        // Playlist name — secondary colour
        binding.tvPlaylistName.setTextColor(secondary)
        binding.tvPlaylistName.setShadowLayer(8f, 0f, 0f, secondary)

        // Section headers
        binding.tvSectionSongs.applyAccentGlow(accent, 6f)
        binding.tvSectionAllSongs.applyAccentGlow(accent, 6f)

        // Add-songs button
        binding.btnAddSongs.setTextColor(accent)
        binding.btnAddSongs.setShadowLayer(6f, 0f, 0f, accent)
        binding.btnAddSongs.background = buildNeonButtonBg(binding.btnAddSongs.context)
    }

    // ── Adapter item helpers ──────────────────────────────────────────────────

    fun applyToSongItem(title: TextView, addBtn: ImageView?) {
        val accent = accentColor()
        title.applyAccentGlow(accent, 4f)
        addBtn?.let {
            ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(accent))
        }
    }

    fun applyToPlaylistItem(name: TextView, renameBtn: ImageButton, deleteBtn: ImageButton) {
        val accent = accentColor()
        name.applyAccentGlow(accent, 4f)
        ImageViewCompat.setImageTintList(renameBtn, ColorStateList.valueOf(accent))
        ImageViewCompat.setImageTintList(deleteBtn, ColorStateList.valueOf(accent))
    }

    fun applyToPlaylistSongItem(title: TextView, removeBtn: ImageButton) {
        val accent = accentColor()
        title.applyAccentGlow(accent, 4f)
        ImageViewCompat.setImageTintList(removeBtn, ColorStateList.valueOf(accent))
    }

    // ── Drawable builders ─────────────────────────────────────────────────────

    /** Builds a neon-outlined button background matching the current accent hue. */
    fun buildNeonButtonBg(context: Context): StateListDrawable {
        val accent = accentColor()
        val accentDim = accentDimColor()
        val r = dpToPx(context, 4).toFloat()
        val sw = dpToPx(context, 1)
        val pressedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.argb(255, 10, 42, 48))
            cornerRadius = r
            setStroke(sw, accent)
        }
        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.argb(255, 5, 5, 16))
            cornerRadius = r
            setStroke(sw, accentDim)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
            addState(intArrayOf(), normalBg)
        }
    }

    private fun buildPlayButtonBg(context: Context, accent: Int): StateListDrawable {
        val sw = dpToPx(context, 2)
        val accentDim = Color.argb(
            255,
            (Color.red(accent) * 0.63f).toInt(),
            (Color.green(accent) * 0.63f).toInt(),
            (Color.blue(accent) * 0.63f).toInt()
        )
        val pressedBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.argb(255, 64, 64, 80), Color.argb(255, 16, 16, 24))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(sw, accent)
        }
        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.argb(255, 192, 192, 208),
                Color.argb(255, 96, 96, 112),
                Color.argb(255, 32, 32, 48)
            )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(sw, accentDim)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
            addState(intArrayOf(), normalBg)
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun TextView.applyAccentGlow(color: Int, shadowRadius: Float) {
        setTextColor(color)
        setShadowLayer(shadowRadius, 0f, 0f, color)
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
