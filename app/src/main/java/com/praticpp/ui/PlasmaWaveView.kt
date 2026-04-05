package com.praticpp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Classic plasma blob animation rendered at reduced resolution and scaled up.
 * Colors shift using a combination of sine waves mapped through the accent hue.
 */
class PlasmaWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Low-resolution render target
    private val RES_W = 120
    private val RES_H = 68

    private var buffer: Bitmap = Bitmap.createBitmap(RES_W, RES_H, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(RES_W * RES_H)
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var time = 0f
    var accentHue: Float = 195f

    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            tick()
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

    private fun tick() {
        time += 0.045f
        for (py in 0 until RES_H) {
            for (px in 0 until RES_W) {
                val nx = px.toFloat() / RES_W
                val ny = py.toFloat() / RES_H
                val v = (sin(nx * 6f + time) +
                         sin(ny * 4f + time * 1.3f) +
                         sin((nx + ny) * 5f + time * 0.7f) +
                         sin(sqrt((nx * nx + ny * ny) * 30f) + time)) * 0.25f
                // Map v (–1..1) to hue offset
                val hue = (accentHue + v * 120f + 360f) % 360f
                val sat = 1f
                val lig = 0.3f + v * 0.2f
                pixels[py * RES_W + px] = ThemeManager.hslToArgb(hue, sat, lig.coerceIn(0.1f, 0.6f))
            }
        }
        buffer.setPixels(pixels, 0, RES_W, 0, 0, RES_W, RES_H)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(buffer, null,
            android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), scalePaint)
    }

    companion object {
        private const val FRAME_MS = 50L
    }
}
