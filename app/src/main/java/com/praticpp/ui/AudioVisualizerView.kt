package com.praticpp.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The three visualizer display modes. */
enum class VisualizerMode {
    /** Smooth bezier-curved waveform line. */
    WAVEFORM,

    /** Vertical frequency-spectrum bars. */
    BAR,

    /** Circular waveform drawn around a center point. */
    CIRCULAR;

    fun next(): VisualizerMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Y2K-style audio visualizer that renders waveform/FFT data captured by
 * [android.media.audiofx.Visualizer] on a Canvas.
 *
 * Feed audio data via [updateWaveform] / [updateFft], then set [accentColor]
 * and [mode] to control appearance.
 *
 * A software layer is required for the [BlurMaskFilter]-based glow effect.
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** The accent color used for both the sharp line and the glow halo. */
    var accentColor: Int = Color.CYAN
        set(value) {
            field = value
            rebuildPaints()
            invalidate()
        }

    /** Current visualizer display mode; triggers a redraw on change. */
    var mode: VisualizerMode = VisualizerMode.WAVEFORM
        set(value) {
            field = value
            invalidate()
        }

    private var waveformData: ByteArray? = null
    private var fftData: ByteArray? = null

    // ── Paints ────────────────────────────────────────────────────────────────

    private val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val barSharpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // BlurMaskFilter only works with a software layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        rebuildPaints()
    }

    private fun rebuildPaints() {
        val r = Color.red(accentColor)
        val g = Color.green(accentColor)
        val b = Color.blue(accentColor)

        sharpPaint.color = accentColor
        glowPaint.color = Color.argb(90, r, g, b)
        glowPaint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)

        barSharpPaint.color = accentColor
        barGlowPaint.color = Color.argb(70, r, g, b)
        barGlowPaint.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    // ── Data ingestion ────────────────────────────────────────────────────────

    /** Call from the Visualizer waveform callback (may be called off the main thread). */
    fun updateWaveform(data: ByteArray) {
        waveformData = data.copyOf()
        postInvalidateOnAnimation()
    }

    /** Call from the Visualizer FFT callback (may be called off the main thread). */
    fun updateFft(data: ByteArray) {
        fftData = data.copyOf()
        postInvalidateOnAnimation()
    }

    /** Reset buffers and redraw an idle state. */
    fun clearData() {
        waveformData = null
        fftData = null
        invalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        when (mode) {
            VisualizerMode.WAVEFORM  -> drawWaveform(canvas)
            VisualizerMode.BAR       -> drawBars(canvas)
            VisualizerMode.CIRCULAR  -> drawCircular(canvas)
        }
    }

    /** Smooth bezier-curved waveform line. Falls back to a flat idle line. */
    private fun drawWaveform(canvas: Canvas) {
        val data = waveformData
        val w = width.toFloat()
        val cy = height / 2f

        val path = Path()

        if (data == null || data.size < 2) {
            // Idle: flat center line
            path.moveTo(0f, cy)
            path.lineTo(w, cy)
        } else {
            val amp = cy * 0.85f
            // Build list of (x, y) sample points
            val pts = Array(data.size) { i ->
                val x = i.toFloat() / (data.size - 1) * w
                val norm = ((data[i].toInt() and 0xFF) - 128) / 128f
                floatArrayOf(x, cy - norm * amp)
            }
            path.moveTo(pts[0][0], pts[0][1])
            for (i in 1 until pts.size - 1) {
                // Standard smooth-bezier: use each sample as the control point and the
                // midpoint to the next sample as the curve endpoint (C1 continuity).
                val mx = (pts[i][0] + pts[i + 1][0]) / 2f
                val my = (pts[i][1] + pts[i + 1][1]) / 2f
                path.quadTo(pts[i][0], pts[i][1], mx, my)
            }
            path.lineTo(pts.last()[0], pts.last()[1])
        }

        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, sharpPaint)
    }

    /**
     * Frequency-spectrum bar chart using FFT magnitude data.
     * Bars are drawn from the bottom of the view upward.
     */
    private fun drawBars(canvas: Canvas) {
        val data = fftData
        val h = height.toFloat()
        val w = width.toFloat()
        val barCount = BAR_COUNT

        // Derive magnitudes from interleaved complex FFT pairs.
        // data[0] is the DC component; data[1] is the Nyquist component (real only).
        // Subsequent pairs are complex: re = data[2i], im = data[2i+1].
        val magnitudes: FloatArray = if (data != null && data.size >= 4) {
            val bins = (data.size / 2).coerceAtMost(barCount)
            FloatArray(bins) { i ->
                val re = (data[i * 2].toInt() and 0xFF) - 128f
                val im = if (i * 2 + 1 < data.size) (data[i * 2 + 1].toInt() and 0xFF) - 128f else 0f
                sqrt(re * re + im * im) / 128f
            }
        } else {
            FloatArray(barCount) { 0f }
        }

        val slots = min(barCount, magnitudes.size)
        val slotW = w / slots

        for (i in 0 until slots) {
            val barH = (magnitudes[i] * h * 0.9f).coerceIn(2f, h * 0.9f)
            val left  = i * slotW + slotW * 0.1f
            val right = (i + 1) * slotW - slotW * 0.1f
            val top   = h - barH
            canvas.drawRect(left, top, right, h, barGlowPaint)
            canvas.drawRect(left, top, right, h, barSharpPaint)
        }
    }

    /**
     * Circular waveform: waveform amplitude is mapped to radial distance
     * around the center, producing a pulsing circle.
     */
    private fun drawCircular(canvas: Canvas) {
        val data = waveformData
        val cx = width / 2f
        val cy = height / 2f
        val baseR = min(cx, cy) * 0.45f
        val ampScale = min(cx, cy) * 0.40f

        val path = Path()

        if (data == null || data.size < 2) {
            // Idle: plain circle
            path.addCircle(cx, cy, baseR, Path.Direction.CW)
        } else {
            val n = data.size
            val pts = Array(n) { i ->
                val angle = (2f * PI.toFloat() * i / n) - (PI.toFloat() / 2f)
                val norm = ((data[i].toInt() and 0xFF) - 128) / 128f
                val r = baseR + norm * ampScale
                floatArrayOf(cx + r * cos(angle), cy + r * sin(angle))
            }
            path.moveTo(pts[0][0], pts[0][1])
            for (i in 1 until pts.size - 1) {
                // Same smooth-bezier-through-points pattern as drawWaveform().
                val mx = (pts[i][0] + pts[i + 1][0]) / 2f
                val my = (pts[i][1] + pts[i + 1][1]) / 2f
                path.quadTo(pts[i][0], pts[i][1], mx, my)
            }
            path.close()
        }

        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, sharpPaint)
    }

    private companion object {
        private const val BAR_COUNT = 32
    }
}
