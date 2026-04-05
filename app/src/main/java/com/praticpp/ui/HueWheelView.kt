package com.praticpp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A circular hue-wheel that lets the user drag to pick an accent hue (0–360°).
 * The outer ring shows the full hue spectrum; the inner circle previews the selected color.
 * A white ring indicator follows the touch position around the wheel.
 */
class HueWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Currently selected hue in degrees [0, 360). */
    var selectedHue: Float = ThemeManager.DEFAULT_HUE
        private set

    /** Callback invoked whenever the hue changes due to a touch event. */
    var onHueChanged: ((Float) -> Unit)? = null

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F0F1A")
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val selectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 255, 255)
    }

    private var cx = 0f
    private var cy = 0f
    private var outerR = 0f
    private var innerR = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        outerR = min(w, h) / 2f * 0.92f
        innerR = outerR * 0.58f
        rebuildShader()
    }

    private fun rebuildShader() {
        // 361 stops so the gradient wraps seamlessly
        val colors = IntArray(361) { i -> ThemeManager.hslToArgb(i.toFloat() % 360f, 1f, 0.5f) }
        val positions = FloatArray(361) { i -> i / 360f }
        wheelPaint.shader = SweepGradient(cx, cy, colors, positions)
        wheelPaint.style = Paint.Style.STROKE
        wheelPaint.strokeWidth = outerR - innerR
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Hue ring
        canvas.drawCircle(cx, cy, (outerR + innerR) / 2f, wheelPaint)

        // Inner dark background
        canvas.drawCircle(cx, cy, innerR - 1f, bgPaint)

        // Selected-color preview circle
        previewPaint.color = ThemeManager.hslToArgb(selectedHue, 1f, 0.5f)
        canvas.drawCircle(cx, cy, innerR * 0.60f, previewPaint)

        // Selector indicator on the ring
        val angleRad = Math.toRadians((selectedHue - 90.0))
        val selectorR = (outerR + innerR) / 2f
        val sx = cx + selectorR * cos(angleRad).toFloat()
        val sy = cy + selectorR * sin(angleRad).toFloat()
        val indicatorR = (outerR - innerR) / 2f + 2f
        canvas.drawCircle(sx, sy, indicatorR, selectorFillPaint)
        canvas.drawCircle(sx, sy, indicatorR, selectorRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= outerR + 24f) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f
                    if (angle >= 360f) angle -= 360f
                    selectedHue = angle
                    onHueChanged?.invoke(selectedHue)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the view square
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    /** Programmatically set the displayed hue without firing [onHueChanged]. */
    fun setHue(hue: Float) {
        selectedHue = hue.coerceIn(0f, 359.9f)
        invalidate()
    }
}
