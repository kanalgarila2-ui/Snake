package com.tryaskafon.shake.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.*
import kotlin.math.abs
import kotlin.math.sqrt

// ── Хаб здоровья ────────────────────────────────────────────────────────────
class HealthFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!
    private val tabs = listOf(
        "👣 Шаги"       to { PedometerFragment() as Fragment },
        "🫁 Дыхание"    to { BreathingFragment() as Fragment },
        "📐 Осанка"     to { PostureFragment() as Fragment },
        "🔥 Калории"    to { CalorieFragment() as Fragment }
    )
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTabsBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(pos: Int) = tabs[pos].second()
        }
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos -> tab.text = tabs[pos].first }.attach()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Шагомер ──────────────────────────────────────────────────────────────────
class PedometerFragment : Fragment(), SensorEventListener {
    private var _b: FragmentPedometerBinding? = null
    private val b get() = _b!!
    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var stepSensor: Sensor? = null
    private var steps = 0
    private var running = false
    // Для fallback акселерометра
    private var lastMagnitude = 0f
    private val STEP_THRESHOLD = 10.5f
    private val STEP_DELAY_MS  = 300L
    private var lastStepTime   = 0L

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPedometerBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Пробуем аппаратный шагомер, fallback на акселерометр
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accel      = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        b.btnPedometerToggle.setOnClickListener {
            if (running) stopPedometer() else startPedometer()
        }
        b.btnPedometerReset.setOnClickListener { steps = 0; updateStepUI() }
        b.tvPedometerHint.text = if (stepSensor != null)
            "✅ Аппаратный шагомер найден"
        else
            "⚠️ Нет аппаратного шагомера, используется акселерометр (менее точно)"
    }

    private fun startPedometer() {
        running = true; b.btnPedometerToggle.text = "⏹ Стоп"
        if (stepSensor != null)
            sensorManager.registerListener(this, stepSensor!!, SensorManager.SENSOR_DELAY_FASTEST)
        else
            sensorManager.registerListener(this, accel!!, SensorManager.SENSOR_DELAY_GAME)
    }
    private fun stopPedometer() {
        running = false; b.btnPedometerToggle.text = "▶ Старт"
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                steps++; updateStepUI()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Алгоритм подсчёта шагов по акселерометру (пороговый метод)
                val mag = sqrt(event.values[0].pow2() + event.values[1].pow2() + event.values[2].pow2())
                val now = System.currentTimeMillis()
                if (abs(mag - lastMagnitude) > STEP_THRESHOLD && now - lastStepTime > STEP_DELAY_MS) {
                    steps++; lastStepTime = now; updateStepUI()
                }
                lastMagnitude = mag
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateStepUI() {
        b.tvStepCount.text = steps.toString()
        val distKm = steps * 0.00075 // ~0.75м на шаг
        val calories = (steps * 0.04).toInt() // ~0.04 ккал на шаг
        b.tvStepStats.text = buildString {
            appendLine("📏 Дистанция: ${"%.2f".format(distKm)} км")
            appendLine("🔥 Калории: ~$calories ккал")
            appendLine("🎯 Цель 10000: ${if (steps >= 10000) "✅ Достигнута!" else "${10000 - steps} до цели"}")
        }
        b.progressSteps.max = 10000
        b.progressSteps.progress = steps.coerceAtMost(10000)
    }

    override fun onPause()  { stopPedometer(); super.onPause() }
    override fun onDestroyView() { stopPedometer(); super.onDestroyView(); _b = null }
    private fun Float.pow2() = this * this
}

// ── Дыхательная гимнастика ────────────────────────────────────────────────────
class BreathingFragment : Fragment() {
    private var _b: FragmentBreathingBinding? = null
    private val b get() = _b!!
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var phase = 0 // 0=вдох, 1=задержка, 2=выдох, 3=пауза
    private var cycleCount = 0

    // Техника 4-7-8
    private val phaseDurations = listOf(4000L, 7000L, 8000L, 1000L)
    private val phaseNames = listOf("Вдох... 🫁", "Задержи... ⏸", "Выдох... 💨", "Пауза...")
    private val phaseColors = listOf("#4CAF50", "#FF9800", "#2196F3", "#9E9E9E")

    private val nextPhase = object : Runnable {
        override fun run() {
            if (!running) return
            phase = (phase + 1) % 4
            if (phase == 0) cycleCount++
            showPhase()
            handler.postDelayed(this, phaseDurations[phase])
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentBreathingBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnBreathToggle.setOnClickListener {
            if (running) { running = false; handler.removeCallbacks(nextPhase); b.btnBreathToggle.text = "▶ Начать"; b.tvBreathPhase.text = "Готов" }
            else { running = true; phase = 0; cycleCount = 0; b.btnBreathToggle.text = "⏹ Стоп"; showPhase(); handler.postDelayed(nextPhase, phaseDurations[0]) }
        }
        b.tvBreathHint.text = "Техника 4-7-8: вдох 4с → задержка 7с → выдох 8с\nСнижает стресс, помогает уснуть"
    }

    private fun showPhase() {
        b.tvBreathPhase.text = phaseNames[phase]
        b.tvBreathCycles.text = "Циклов: $cycleCount"
        val color = android.graphics.Color.parseColor(phaseColors[phase])
        b.breathCircle.animate()
            .scaleX(if (phase == 0) 1.5f else if (phase == 2) 0.8f else 1.2f)
            .scaleY(if (phase == 0) 1.5f else if (phase == 2) 0.8f else 1.2f)
            .setDuration(phaseDurations[phase] - 100)
            .start()
        b.breathCircle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
        b.progressBreath.max = (phaseDurations[phase] / 1000).toInt()
        // Анимация прогресс-бара
        var elapsed = 0
        val tick = object : Runnable {
            override fun run() {
                if (!running || elapsed >= b.progressBreath.max) return
                b.progressBreath.progress = elapsed++
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(tick)
    }

    override fun onPause() { running = false; handler.removeCallbacksAndMessages(null); super.onPause() }
    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}

// ── Проверка осанки ───────────────────────────────────────────────────────────
class PostureFragment : Fragment(), SensorEventListener {
    private var _b: FragmentPostureBinding? = null
    private val b get() = _b!!
    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var monitoring = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPostureBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        b.btnPostureToggle.setOnClickListener {
            monitoring = !monitoring
            if (monitoring) { b.btnPostureToggle.text = "⏹ Стоп"; sensorManager.registerListener(this, accel!!, SensorManager.SENSOR_DELAY_UI) }
            else { b.btnPostureToggle.text = "▶ Мониторить"; sensorManager.unregisterListener(this) }
        }
        b.tvPostureHint.text = "📱 Держи телефон вертикально перед собой\nПриложение оценит отклонение от прямой осанки"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        // Угол наклона от вертикали
        val tiltAngle = Math.toDegrees(Math.atan2(x.toDouble(), sqrt(y * y + z * z).toDouble()))
        val forwardTilt = Math.toDegrees(Math.atan2((-y).toDouble(), z.toDouble()))

        val postureScore = (100 - (Math.abs(tiltAngle) + Math.abs(forwardTilt + 10)) * 1.5).coerceIn(0.0, 100.0).toInt()

        b.tvPostureAngle.text = "Боковой наклон: ${"%.1f".format(tiltAngle)}°\nНаклон вперёд: ${"%.1f".format(forwardTilt)}°"
        b.progressPosture.progress = postureScore
        b.tvPostureScore.text = when {
            postureScore >= 85 -> "✅ Отличная осанка!"
            postureScore >= 65 -> "😐 Неплохо, но есть куда расти"
            postureScore >= 40 -> "😬 Выпрями спину!"
            else               -> "😱 Срочно выпрямись!"
        }
        val color = when {
            postureScore >= 85 -> "#4CAF50"
            postureScore >= 65 -> "#FFC107"
            postureScore >= 40 -> "#FF9800"
            else               -> "#F44336"
        }
        b.progressPosture.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onPause() { monitoring = false; sensorManager.unregisterListener(this); super.onPause() }
    override fun onDestroyView() { sensorManager.unregisterListener(this); super.onDestroyView(); _b = null }
}

// ── Калькулятор калорий ───────────────────────────────────────────────────────
class CalorieFragment : Fragment() {
    private var _b: FragmentCalorieBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCalorieBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnCalcCalories.setOnClickListener { calculate() }
        b.tvCalorieHint.text = "Введи данные — рассчитаем дневную норму калорий"
    }

    private fun calculate() {
        val weight = b.etCalWeight.text.toString().toDoubleOrNull() ?: return
        val height = b.etCalHeight.text.toString().toDoubleOrNull() ?: return
        val age    = b.etCalAge.text.toString().toDoubleOrNull()    ?: return
        val isMale = b.rgGender.checkedRadioButtonId == b.rbMale.id

        // Формула Миффлина-Сан Жеора
        val bmr = if (isMale)
            10 * weight + 6.25 * height - 5 * age + 5
        else
            10 * weight + 6.25 * height - 5 * age - 161

        // Коэффициент активности
        val activityMultiplier = when (b.spinnerActivity.selectedItemPosition) {
            0 -> 1.2   // Сидячий
            1 -> 1.375 // Лёгкая активность
            2 -> 1.55  // Умеренная
            3 -> 1.725 // Высокая
            4 -> 1.9   // Очень высокая
            else -> 1.2
        }

        val tdee = bmr * activityMultiplier
        val bmi  = weight / ((height / 100) * (height / 100))

        b.tvCalorieResult.text = buildString {
            appendLine("📊 Результаты:")
            appendLine()
            appendLine("BMR (базовый обмен): ${"%.0f".format(bmr)} ккал/день")
            appendLine("TDEE (с учётом активности): ${"%.0f".format(tdee)} ккал/день")
            appendLine()
            appendLine("Для похудения (-0.5 кг/нед): ${"%.0f".format(tdee - 500)} ккал")
            appendLine("Для набора (+0.5 кг/нед):    ${"%.0f".format(tdee + 500)} ккал")
            appendLine()
            appendLine("BMI: ${"%.1f".format(bmi)} — ${bmiCategory(bmi)}")
        }
    }

    private fun bmiCategory(bmi: Double) = when {
        bmi < 18.5 -> "Недовес"
        bmi < 25.0 -> "Норма ✅"
        bmi < 30.0 -> "Избыточный вес"
        else       -> "Ожирение"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
