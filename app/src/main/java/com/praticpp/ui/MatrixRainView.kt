package com.praticpp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Matrix-style falling character animation. Characters inherit the accent hue.
 */
class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#\$%^&*()[]アイウエオカキクケコサシスセソ"
    private val fontSize = 36f

    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSize
    }
    private val fadePaint = Paint().apply {
        color = Color.argb(28, 0, 0, 0)
    }

    private var buffer: Bitmap? = null
    private var bufCanvas: Canvas? = null

    private var columns = 0
    private var drops = IntArray(0)
    private var dropChars = Array(0) { ' ' }

    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            tick()
            invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    var accentColor: Int = Color.GREEN

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(frameRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buffer?.recycle()
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        buffer = bmp
        bufCanvas = Canvas(bmp).also { it.drawColor(Color.BLACK) }

        columns = (w / fontSize).toInt() + 1
        drops = IntArray(columns) { Random.nextInt(-30, 0) }
        dropChars = Array(columns) { charSet.random() }
    }

    private fun tick() {
        val bc = bufCanvas ?: return

        // Fade previous frame
        bc.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

        val aR = Color.red(accentColor)
        val aG = Color.green(accentColor)
        val aB = Color.blue(accentColor)

        for (i in 0 until columns) {
            val x = i * fontSize
            val yHead = drops[i] * fontSize

            if (yHead >= 0f && yHead <= height) {
                // Bright white head character
                charPaint.color = Color.argb(230, 255, 255, 255)
                bc.drawText(dropChars[i].toString(), x, yHead, charPaint)
            }

            // Draw a short glowing tail above the head
            val tailLen = 6
            for (t in 1..tailLen) {
                val yTail = (drops[i] - t) * fontSize
                if (yTail < 0f || yTail > height) continue
                val alpha = (180 - t * 25).coerceAtLeast(0)
                charPaint.color = Color.argb(alpha, aR, aG, aB)
                bc.drawText(charSet.random().toString(), x, yTail, charPaint)
            }

            // Reset drop when it exits the bottom
            if (drops[i] * fontSize > height && Random.nextFloat() > 0.975f) {
                drops[i] = Random.nextInt(-15, 0)
            }
            drops[i]++
            dropChars[i] = charSet.random()
        }
    }

    override fun onDraw(canvas: Canvas) {
        buffer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    companion object {
        private const val FRAME_MS = 80L
    }
}
