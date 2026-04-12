package com.tryaskafon.shake.system

import android.content.Context
import android.graphics.*
import android.hardware.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentCompassBinding
import kotlin.math.*

class CompassFragment : Fragment(), SensorEventListener {

    private var _b: FragmentCompassBinding? = null
    private val b get() = _b!!
    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null
    private var accelerometer: Sensor? = null

    private val gravity   = FloatArray(3)
    private val magnetic  = FloatArray(3)
    private val rotation  = FloatArray(9)
    private val orientation = FloatArray(3)

    // Данные акселерометра для уровня
    private var accelX = 0f
    private var accelY = 0f

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCompassBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (magnetometer == null) b.tvCompassDeg.text = "Магнитометр не найден"
    }

    override fun onResume() {
        super.onResume()
        magnetometer?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                accelX = event.values[0]
                accelY = event.values[1]
            }
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, magnetic, 0, 3)
        }

        if (SensorManager.getRotationMatrix(rotation, null, gravity, magnetic)) {
            SensorManager.getOrientation(rotation, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val normalized = (azimuth + 360) % 360

            b.compassView.setAzimuth(normalized)
            b.tvCompassDeg.text = "${"%.0f".format(normalized)}°  ${getDirection(normalized)}"

            // Уровень: пузырёк смещается от наклона
            b.levelView.setTilt(accelX, accelY)
            val tiltDeg = sqrt(accelX * accelX + accelY * accelY).coerceAtMost(9.8f)
            b.tvLevelDeg.text = "Наклон: ${"%.1f".format(Math.toDegrees(asin(tiltDeg / 9.8).toDouble()))}°"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getDirection(deg: Float) = when {
        deg < 22.5 || deg >= 337.5 -> "С"
        deg < 67.5  -> "СВ"
        deg < 112.5 -> "В"
        deg < 157.5 -> "ЮВ"
        deg < 202.5 -> "Ю"
        deg < 247.5 -> "ЮЗ"
        deg < 292.5 -> "З"
        else         -> "СЗ"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

/** View компаса */
class CompassView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var azimuth = 0f
    private val paintRose  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6D00"); style = Paint.Style.STROKE; strokeWidth = 4f }
    private val paintNeedle= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    private val paintText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
    private val paintBg    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E") }
    private val paintCircle= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#333355"); style = Paint.Style.STROKE; strokeWidth = 2f }

    fun setAzimuth(deg: Float) { azimuth = deg; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 20f
        canvas.drawCircle(cx, cy, r, paintBg)
        canvas.drawCircle(cx, cy, r, paintCircle)

        // Деления
        for (i in 0 until 36) {
            val angle = Math.toRadians(i * 10.0 - azimuth)
            val inner = if (i % 9 == 0) r * 0.65f else r * 0.8f
            canvas.drawLine(
                cx + sin(angle).toFloat() * inner, cy - cos(angle).toFloat() * inner,
                cx + sin(angle).toFloat() * (r - 8), cy - cos(angle).toFloat() * (r - 8),
                paintRose.apply { strokeWidth = if (i % 9 == 0) 4f else 2f }
            )
        }

        // Буквы СЮВЗ
        val dirs = listOf("С" to 0f, "В" to 90f, "Ю" to 180f, "З" to 270f)
        dirs.forEach { (label, deg) ->
            val angle = Math.toRadians((deg - azimuth).toDouble())
            canvas.drawText(label,
                cx + sin(angle).toFloat() * r * 0.55f,
                cy - cos(angle).toFloat() * r * 0.55f + paintText.textSize / 3,
                paintText)
        }

        // Стрелка
        val needleLen = r * 0.45f
        paintNeedle.color = Color.RED
        canvas.drawLine(cx, cy, cx, cy - needleLen, paintNeedle.apply { strokeWidth = 6f })
        paintNeedle.color = Color.WHITE
        canvas.drawLine(cx, cy, cx, cy + needleLen * 0.6f, paintNeedle)
        canvas.drawCircle(cx, cy, 10f, Paint().apply { color = Color.parseColor("#FF6D00"); isAntiAlias = true })
    }
}

/** View пузырькового уровня */
class LevelView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var tx = 0f; private var ty = 0f
    private val paintBg     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A2E") }
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#333355"); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val paintBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CAF50") }
    private val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6D00"); style = Paint.Style.STROKE; strokeWidth = 2f }

    fun setTilt(x: Float, y: Float) { tx = x; ty = y; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 10f
        canvas.drawCircle(cx, cy, r, paintBg)
        canvas.drawCircle(cx, cy, r, paintCircle)
        canvas.drawCircle(cx, cy, r * 0.15f, paintCenter)
        // Пузырёк смещается противоположно наклону
        val bx = cx - tx / 9.8f * r * 0.75f
        val by = cy + ty / 9.8f * r * 0.75f
        val br = r * 0.12f
        val dx = bx - cx; val dy = by - cy
        val dist = sqrt(dx * dx + dy * dy)
        val maxDist = r - br
        val finalX = if (dist > maxDist) cx + dx / dist * maxDist else bx
        val finalY = if (dist > maxDist) cy + dy / dist * maxDist else by
        paintBubble.alpha = 200
        canvas.drawCircle(finalX, finalY, br, paintBubble)
    }
}
