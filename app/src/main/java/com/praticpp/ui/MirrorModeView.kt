package com.praticpp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs

/**
 * Full-screen chrome/metallic mirror surface that reacts to device tilt in real time.
 *
 * A radial gradient simulates a reflective metallic sphere; a specular highlight (glare)
 * tracks the device tilt via TYPE_ROTATION_VECTOR. A subtle scanline overlay adds depth.
 * The accent hue tints the chrome (e.g. red → rose-gold, blue → steel-blue).
 *
 * No extra manifest permission is needed — the gyroscope sensor is available without one.
 */
class MirrorModeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    /** Accent hue (0–360) used to tint the chrome surface. */
    var accentHue: Float = 195f
        set(value) {
            field = value
            shaderDirty = true
            invalidate()
        }

    // ── Sensor ───────────────────────────────────────────────────────────────

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Smoothed, normalised tilt: -1..1 per axis. */
    private var tiltX = 0f   // negative = tilted left,  positive = tilted right
    private var tiltY = 0f   // negative = tilted forward, positive = tilted back

    // ── Paints ───────────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryGlarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(14, 0, 0, 0)
    }
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // Cached shader state
    private var shaderDirty = true
    private var lastW = 0f
    private var lastH = 0f
    private var lastTiltX = Float.NaN
    private var lastTiltY = Float.NaN

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSensor()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }

    /** Call from Activity.onResume() to re-register after a pause. */
    fun resumeSensor() = registerSensor()

    /** Call from Activity.onPause() to save battery while in background. */
    fun pauseSensor() = sensorManager.unregisterListener(this)

    private fun registerSensor() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // ── SensorEventListener ───────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotMatrix, orientation)

        // orientation[1] = pitch (rad): negative when screen faces floor (tilt forward)
        // orientation[2] = roll  (rad): negative when right side is down (tilt left)
        //
        // Mirror physics:
        //   tilt left  (roll-) → glare moves right → rawX positive
        //   tilt forward (pitch-) → glare moves down  → rawY negative → invert below
        val rawX = -orientation[2]   // roll negated: tilt-left → positive
        val rawY = orientation[1]    // pitch as-is:  tilt-forward → negative

        // Low-pass filter (ALPHA ≈ 0.15 gives smooth motion without lag)
        tiltX += SENSOR_ALPHA * (rawX - tiltX)
        tiltY += SENSOR_ALPHA * (rawY - tiltY)

        invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val maxAngle = (PI / 4).toFloat()
        val normX = (tiltX / maxAngle).coerceIn(-1f, 1f)
        val normY = (tiltY / maxAngle).coerceIn(-1f, 1f)

        // Rebuild gradient shaders only when size, hue, or tilt changes meaningfully
        val sizeChanged = (w != lastW || h != lastH)
        val tiltChanged = (abs(normX - lastTiltX) > TILT_CHANGE_THRESHOLD || abs(normY - lastTiltY) > TILT_CHANGE_THRESHOLD)
        if (shaderDirty || sizeChanged || tiltChanged) {
            buildShaders(w, h, normX, normY)
            lastW = w; lastH = h; lastTiltX = normX; lastTiltY = normY
            shaderDirty = false
        }

        // ── Chrome base ──────────────────────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── Secondary wide glow (brushed-metal depth ring) ───────────────────
        canvas.drawRect(0f, 0f, w, h, secondaryGlarePaint)

        // ── Primary specular highlight ───────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, glarePaint)

        // ── Subtle horizontal scanlines ──────────────────────────────────────
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanlinePaint)
            y += SCANLINE_SPACING
        }

        // ── Brushed-chrome vertical micro-lines ──────────────────────────────
        // Faint vertical lines spaced BRUSH_LINE_SPACING px apart, slightly offset by tilt
        val lineOffset = normX * BRUSH_LINE_OFFSET_MULTIPLIER
        brushPaint.color = Color.argb(8, 255, 255, 255)
        var x = (lineOffset % BRUSH_LINE_SPACING + BRUSH_LINE_SPACING) % BRUSH_LINE_SPACING
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, brushPaint)
            x += BRUSH_LINE_SPACING
        }
    }

    private fun buildShaders(w: Float, h: Float, normX: Float, normY: Float) {
        // Glare center: tilting left (normX+) moves glare right; forward (normY-) moves down
        val glareCx = w * (0.42f + normX * 0.28f)
        val glareCy = h * (0.38f - normY * 0.22f)

        // ── Chrome colours derived from accent hue ───────────────────────────
        val hue = accentHue
        // Very light, barely-tinted highlight
        val highlight  = hslToArgb(hue, 0.10f, 0.94f)
        // Mid-tone silver with moderate hue saturation
        val chromeMid  = hslToArgb(hue, 0.22f, 0.58f)
        // Dark metallic edge
        val chromeDark = hslToArgb(hue, 0.16f, 0.11f)

        // ── Chrome base radial gradient ──────────────────────────────────────
        bgPaint.shader = RadialGradient(
            glareCx, glareCy,
            maxOf(w, h) * 1.05f,
            intArrayOf(highlight, chromeMid, chromeDark),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )

        // ── Secondary depth ring (wider, dimmer) ─────────────────────────────
        val glareR2 = Color.red(highlight)
        val glareG2 = Color.green(highlight)
        val glareB2 = Color.blue(highlight)
        secondaryGlarePaint.shader = RadialGradient(
            glareCx, glareCy,
            maxOf(w, h) * 0.55f,
            intArrayOf(
                Color.argb(90, glareR2, glareG2, glareB2),
                Color.argb(0,  glareR2, glareG2, glareB2)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // ── Tight specular highlight ─────────────────────────────────────────
        val glareRadius = w * 0.20f
        glarePaint.shader = RadialGradient(
            glareCx, glareCy,
            glareRadius,
            intArrayOf(
                Color.argb(210, 255, 255, 255),
                Color.argb(90,  255, 255, 255),
                Color.argb(0,   255, 255, 255)
            ),
            floatArrayOf(0f, 0.28f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
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

    companion object {
        /** Low-pass filter smoothing factor. 0 = frozen, 1 = raw (no smoothing). */
        private const val SENSOR_ALPHA = 0.15f
        /** Minimum normalised tilt change that triggers a shader rebuild. */
        private const val TILT_CHANGE_THRESHOLD = 0.005f
        /** Vertical distance (px) between scanlines. */
        private const val SCANLINE_SPACING = 3f
        /** Multiplier mapping tilt to brushed-chrome line offset in pixels. */
        private const val BRUSH_LINE_OFFSET_MULTIPLIER = 6f
        /** Horizontal spacing (px) between brushed-chrome vertical micro-lines. */
        private const val BRUSH_LINE_SPACING = 4f
    }
}
