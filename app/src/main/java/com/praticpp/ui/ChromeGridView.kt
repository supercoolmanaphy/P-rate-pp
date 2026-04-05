package com.praticpp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/**
 * Retro perspective grid that scrolls toward the viewer — classic Y2K / VHS intro style.
 * Grid line colour follows the accent hue; a subtle glow is layered on top.
 */
class ChromeGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var accentColor: Int = Color.CYAN
        set(value) {
            field = value
            horizonShaderDirty = true
        }

    private var horizonShaderDirty = true
    private var cachedVpY = 0f

    /** Scroll offset 0..1 — one full grid-cell depth per cycle. */
    private var scrollOffset = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            scrollOffset = (scrollOffset + SCROLL_SPEED) % 1f
            invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(frameRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Vanishing point sits at 45% height for a slight tilt
        val vpX = w * 0.5f
        val vpY = h * 0.45f

        val aR = Color.red(accentColor)
        val aG = Color.green(accentColor)
        val aB = Color.blue(accentColor)

        // Rebuild horizon shader when accent changes or size changes
        if (horizonShaderDirty || cachedVpY != vpY) {
            horizonPaint.shader = android.graphics.LinearGradient(
                0f, vpY - 4f, 0f, vpY + 4f,
                Color.argb(0, aR, aG, aB),
                Color.argb(120, aR, aG, aB),
                android.graphics.Shader.TileMode.CLAMP
            )
            cachedVpY = vpY
            horizonShaderDirty = false
        }

        // ── Horizontal grid lines ────────────────────────────────────────────
        // Lines go from 0 (vanishing point) to 1 (bottom edge), perspective-mapped.
        val hLineCount = 12
        for (i in 0..hLineCount) {
            // t: 0 = top (near vp) → 1 = bottom; add scrollOffset to animate
            val t = ((i.toFloat() + scrollOffset) / hLineCount).coerceIn(0f, 1f)
            // perspective foreshortening: use t^2 to bunch lines near horizon
            val tPow = t * t
            val y = vpY + (h - vpY) * tPow

            // Line spans full width at the bottom, tapers to a point at vp
            val alpha = (tPow * 200).toInt().coerceIn(0, 200)

            // Glow (wider, dimmer)
            glowPaint.color = Color.argb(alpha / 3, aR, aG, aB)
            canvas.drawLine(0f, y, w, y, glowPaint)

            // Core line
            linePaint.color = Color.argb(alpha, aR, aG, aB)
            canvas.drawLine(0f, y, w, y, linePaint)
        }

        // ── Vertical grid lines ──────────────────────────────────────────────
        val vLineCount = 10
        for (i in -vLineCount / 2..vLineCount / 2) {
            // Fraction from centre (0 = center, ±0.5 = edges)
            val frac = i.toFloat() / vLineCount

            // Bottom intercept spreads out; top stays at vp
            val xBottom = vpX + frac * w * 1.1f
            val alpha = (150 - (abs(frac) * 200).toInt()).coerceIn(20, 150)

            glowPaint.color = Color.argb(alpha / 3, aR, aG, aB)
            canvas.drawLine(vpX, vpY, xBottom, h, glowPaint)

            linePaint.color = Color.argb(alpha, aR, aG, aB)
            canvas.drawLine(vpX, vpY, xBottom, h, linePaint)
        }

        // ── Horizon glow ─────────────────────────────────────────────────────
        canvas.drawRect(0f, vpY - 4f, w, vpY + 4f, horizonPaint)
    }

    companion object {
        private const val FRAME_MS = 33L       // ~30 fps
        private const val SCROLL_SPEED = 0.012f
    }
}
