package com.praticpp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Warp-speed starfield — stars stream from the centre outward to simulate light-speed travel.
 * The brightest stars are tinted with the current accent colour.
 */
class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Star(
        var x: Float,
        var y: Float,
        var z: Float,          // depth: 0 (far) → 1 (close)
        val speed: Float,
        val accentTinted: Boolean
    )

    private val starCount = 160
    private val stars = mutableListOf<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint().apply { color = Color.BLACK }

    var accentColor: Int = Color.CYAN

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        repeat(starCount) { stars.add(randomStar(w, h, scattered = true)) }
    }

    private fun randomStar(w: Int, h: Int, scattered: Boolean = false): Star {
        val cx = w / 2f
        val cy = h / 2f
        return if (scattered) {
            Star(
                x = Random.nextFloat() * w - cx,
                y = Random.nextFloat() * h - cy,
                z = Random.nextFloat(),
                speed = 0.004f + Random.nextFloat() * 0.008f,
                accentTinted = Random.nextFloat() < 0.25f
            )
        } else {
            Star(
                x = (Random.nextFloat() - 0.5f) * 0.1f,
                y = (Random.nextFloat() - 0.5f) * 0.1f,
                z = 0f,
                speed = 0.004f + Random.nextFloat() * 0.008f,
                accentTinted = Random.nextFloat() < 0.25f
            )
        }
    }

    private fun tick() {
        val w = width.takeIf { it > 0 } ?: return
        val h = height.takeIf { it > 0 } ?: return
        val cx = w / 2f
        val cy = h / 2f
        val diagonal = hypot(cx, cy)

        val toRemove = mutableListOf<Star>()
        for (star in stars) {
            star.z += star.speed
            val screenX = star.x / (1f - star.z) + cx
            val screenY = star.y / (1f - star.z) + cy
            if (star.z >= 1f || screenX < 0 || screenX > w || screenY < 0 || screenY > h) {
                toRemove.add(star)
            }
        }
        stars.removeAll(toRemove)
        repeat(toRemove.size) { stars.add(randomStar(w, h)) }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.takeIf { it > 0 } ?: return
        val h = height.takeIf { it > 0 } ?: return
        val cx = w / 2f
        val cy = h / 2f

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val aR = Color.red(accentColor)
        val aG = Color.green(accentColor)
        val aB = Color.blue(accentColor)

        for (star in stars) {
            val perspective = 1f - star.z
            val screenX = star.x / perspective + cx
            val screenY = star.y / perspective + cy

            val brightness = star.z  // 0 = invisible, 1 = full brightness
            val radius = (brightness * 3.5f).coerceIn(0.3f, 3.5f)
            val alpha = (brightness * 220).toInt().coerceIn(0, 220)

            if (star.accentTinted) {
                paint.color = Color.argb(alpha, aR, aG, aB)
            } else {
                val wh = (brightness * 255).toInt().coerceIn(80, 255)
                paint.color = Color.argb(alpha, wh, wh, wh)
            }
            canvas.drawCircle(screenX, screenY, radius, paint)
        }
    }

    companion object {
        private const val FRAME_MS = 16L
    }
}
