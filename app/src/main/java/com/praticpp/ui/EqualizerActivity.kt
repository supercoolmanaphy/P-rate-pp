package com.praticpp.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.praticpp.R
import com.praticpp.databinding.ActivityEqualizerBinding
import com.praticpp.player.MusicPlayerManager

/**
 * Y2K-styled hardware-mixer Equalizer screen.
 *
 * Wraps Android's built-in android.media.audiofx package:
 *  - Equalizer        → per-band frequency control (5 bands)
 *  - BassBoost        → dedicated bass intensity
 *  - Virtualizer      → surround/spatial effect
 *  - LoudnessEnhancer → volume boost
 *
 * All settings are saved per-user in SharedPreferences via [EqRepository].
 */
class EqualizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEqualizerBinding
    private lateinit var eqRepo: EqRepository
    private lateinit var eqManager: EqualizerManager

    /** Vertical SeekBar references, one per frequency band. */
    private val bandSeekBars = mutableListOf<SeekBar>()
    /** Level display labels per band. */
    private val bandValueLabels = mutableListOf<TextView>()
    /** LED meter bars per band. */
    private val ledMeterViews = mutableListOf<View>()
    /** Preset buttons, indexed to match EqPreset.entries. */
    private val presetButtons = mutableListOf<Button>()

    private var currentPreset: EqPreset = EqPreset.FLAT

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eqRepo = EqRepository(this)

        val sessionId = MusicPlayerManager.getInstance(this).player.audioSessionId
        eqManager = EqualizerManager(sessionId)

        ThemeManager.load(this)

        binding.btnBack.setOnClickListener { finish() }

        setupEnableSwitch()
        setupPresetButtons()
        setupBandSliders()
        setupEffectSliders()
        loadSavedSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        eqManager.release()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupEnableSwitch() {
        binding.swEqEnable.isChecked = eqRepo.isEnabled
        eqManager.setEnabled(eqRepo.isEnabled)
        setControlsAlpha(eqRepo.isEnabled)

        binding.swEqEnable.setOnCheckedChangeListener { _, isChecked ->
            eqRepo.isEnabled = isChecked
            eqManager.setEnabled(isChecked)
            setControlsAlpha(isChecked)
        }
    }

    private fun setControlsAlpha(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.45f
        binding.llBands.alpha = alpha
        binding.llPresets.alpha = alpha
        binding.sbBassBoost.alpha = alpha
        binding.sbVirtualizer.alpha = alpha
        binding.sbLoudness.alpha = alpha
    }

    private fun setupPresetButtons() {
        EqPreset.entries.forEach { preset ->
            val btn = Button(this).apply {
                text = preset.displayName
                setTextColor(resources.getColor(R.color.neon_cyan, theme))
                textSize = 9f
                isAllCaps = true
                letterSpacing = 0.08f
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundResource(R.drawable.bg_preset_btn)
                val hPad = resources.getDimensionPixelSize(R.dimen.eq_preset_pad_h)
                val vPad = resources.getDimensionPixelSize(R.dimen.eq_preset_pad_v)
                setPadding(hPad, vPad, hPad, vPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.eq_preset_gap)
                }
                setOnClickListener { applyPreset(preset) }
            }
            presetButtons.add(btn)
            binding.llPresets.addView(btn)
        }
    }

    private fun setupBandSliders() {
        val numBands = eqManager.numberOfBands.toInt()
        val levelRange = eqManager.bandLevelRange      // [minMb, maxMb]
        val minMb = levelRange[0].toInt()
        val maxMb = levelRange[1].toInt()
        val rangeMb = maxMb - minMb

        repeat(numBands) { band ->
            val bandColumn = buildBandColumn(band, minMb, maxMb, rangeMb)
            binding.llBands.addView(bandColumn)
        }
    }

    /**
     * Builds one vertical fader column for [band]:
     *  - dB label on top
     *  - Vertical SeekBar (rotated 270°)
     *  - LED indicator bar
     *  - Frequency label on bottom
     */
    private fun buildBandColumn(band: Int, minMb: Int, maxMb: Int, rangeMb: Int): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(4.dp, 0, 4.dp, 0)
        }

        // dB value label (top)
        val tvDb = TextView(this).apply {
            text = "+0.0"
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 9f
            setTextColor(resources.getColor(R.color.neon_cyan, theme))
            setShadowLayer(4f, 0f, 0f, resources.getColor(R.color.neon_cyan, theme))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp }
        }
        bandValueLabels.add(tvDb)
        column.addView(tvDb)

        // Vertical SeekBar via rotation trick
        val sliderHeight = 140.dp   // apparent height of the fader track
        val sliderThumb  = 48.dp    // apparent width of the fader widget

        val sliderFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(sliderThumb, sliderHeight)
        }

        val seekBar = SeekBar(this).apply {
            max = rangeMb
            progress = -minMb                // 0 dB sits at centre of range
            progressDrawable = resources.getDrawable(R.drawable.bg_eq_fader, theme)
            thumb = resources.getDrawable(R.drawable.bg_eq_fader_thumb, theme)
            splitTrack = false
            rotation = 270f
            // After 270° rotation, the widget width maps to visual height and vice-versa.
            layoutParams = FrameLayout.LayoutParams(sliderHeight, sliderThumb).apply {
                gravity = Gravity.CENTER
            }
            setPadding(12.dp, 0, 12.dp, 0)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val levelMb = (progress + minMb).toShort()
                    tvDb.text = formatDb(levelMb.toInt())
                    updateLedMeter(band, levelMb.toInt(), minMb, maxMb)
                    eqRepo.setBandLevel(band, levelMb)
                    eqManager.setBandLevel(band.toShort(), levelMb)
                    if (fromUser) {
                        eqRepo.currentPreset = ""
                        highlightPreset(null)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        bandSeekBars.add(seekBar)
        sliderFrame.addView(seekBar)
        column.addView(sliderFrame)

        // LED meter bar
        val ledBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24.dp, 4.dp).apply {
                topMargin = 4.dp
                bottomMargin = 4.dp
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(resources.getColor(R.color.neon_cyan_dim, theme))
        }
        ledMeterViews.add(ledBar)
        column.addView(ledBar)

        // Frequency label (bottom)
        val tvFreq = TextView(this).apply {
            text = eqManager.getCenterFreqHz(band.toShort())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 8f
            setTextColor(resources.getColor(R.color.text_secondary, theme))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        column.addView(tvFreq)

        return column
    }

    private fun setupEffectSliders() {
        binding.sbBassBoost.setOnSeekBarChangeListener(effectListener(
            label = binding.tvBassBoostVal,
            onChanged = { v ->
                eqRepo.bassBoostStrength = v.toShort()
                eqManager.setBassBoostStrength(v.toShort())
            }
        ))

        binding.sbVirtualizer.setOnSeekBarChangeListener(effectListener(
            label = binding.tvVirtualizerVal,
            onChanged = { v ->
                eqRepo.virtualizerStrength = v.toShort()
                eqManager.setVirtualizerStrength(v.toShort())
            }
        ))

        binding.sbLoudness.setOnSeekBarChangeListener(effectListener(
            label = binding.tvLoudnessVal,
            onChanged = { v ->
                eqRepo.loudnessGainMb = v
                eqManager.setLoudnessGain(v)
            }
        ))
    }

    private fun effectListener(
        label: TextView,
        onChanged: (Int) -> Unit
    ) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            label.text = "%03d".format(progress)
            onChanged(progress)
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    // ── Load / apply settings ─────────────────────────────────────────────────

    private fun loadSavedSettings() {
        val levelRange = eqManager.bandLevelRange
        val minMb = levelRange[0].toInt()
        val maxMb = levelRange[1].toInt()

        bandSeekBars.forEachIndexed { band, sb ->
            val levelMb = eqRepo.getBandLevel(band).toInt().coerceIn(minMb, maxMb)
            val progress = (levelMb - minMb).coerceIn(0, sb.max)
            sb.progress = progress
            bandValueLabels[band].text = formatDb(levelMb)
            updateLedMeter(band, levelMb, minMb, maxMb)
            eqManager.setBandLevel(band.toShort(), levelMb.toShort())
        }

        val bass = eqRepo.bassBoostStrength.toInt()
        binding.sbBassBoost.progress = bass
        binding.tvBassBoostVal.text = "%03d".format(bass)
        eqManager.setBassBoostStrength(bass.toShort())

        val virt = eqRepo.virtualizerStrength.toInt()
        binding.sbVirtualizer.progress = virt
        binding.tvVirtualizerVal.text = "%03d".format(virt)
        eqManager.setVirtualizerStrength(virt.toShort())

        val loud = eqRepo.loudnessGainMb
        binding.sbLoudness.progress = loud
        binding.tvLoudnessVal.text = "%03d".format(loud)
        eqManager.setLoudnessGain(loud)

        currentPreset = EqPreset.fromName(eqRepo.currentPreset) ?: EqPreset.FLAT
        highlightPreset(currentPreset)
    }

    private fun applyPreset(preset: EqPreset) {
        currentPreset = preset
        eqRepo.applyPreset(preset)

        val levelRange = eqManager.bandLevelRange
        val minMb = levelRange[0].toInt()
        val maxMb = levelRange[1].toInt()

        preset.bandLevelsMb.forEachIndexed { band, levelMb ->
            if (band < bandSeekBars.size) {
                val clamped = levelMb.coerceIn(minMb, maxMb)
                bandSeekBars[band].progress = clamped - minMb
                bandValueLabels[band].text = formatDb(clamped)
                updateLedMeter(band, clamped, minMb, maxMb)
                eqManager.setBandLevel(band.toShort(), clamped.toShort())
            }
        }
        highlightPreset(preset)
    }

    private fun highlightPreset(active: EqPreset?) {
        EqPreset.entries.forEachIndexed { i, preset ->
            val btn = presetButtons.getOrNull(i) ?: return@forEachIndexed
            val isActive = (preset == active)
            btn.isSelected = isActive
            btn.setTextColor(
                if (isActive) resources.getColor(R.color.neon_cyan, theme)
                else resources.getColor(R.color.text_secondary, theme)
            )
            btn.setShadowLayer(
                if (isActive) 6f else 0f, 0f, 0f,
                resources.getColor(R.color.neon_cyan, theme)
            )
        }
    }

    // ── LED meter ─────────────────────────────────────────────────────────────

    private fun updateLedMeter(band: Int, levelMb: Int, minMb: Int, maxMb: Int) {
        val bar = ledMeterViews.getOrNull(band) ?: return
        val color = when {
            levelMb > 600  -> resources.getColor(R.color.neon_magenta, theme)
            levelMb > 0    -> resources.getColor(R.color.neon_cyan, theme)
            levelMb < -600 -> resources.getColor(R.color.neon_green_dim, theme)
            else           -> resources.getColor(R.color.neon_cyan_dim, theme)
        }
        bar.setBackgroundColor(color)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun formatDb(levelMb: Int): String {
        val db = levelMb / 100f
        return if (db >= 0) "+%.1f".format(db) else "%.1f".format(db)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
