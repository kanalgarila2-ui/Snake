package com.tryaskafon.shake.science

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
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import kotlin.math.*

// ── Хаб науки ────────────────────────────────────────────────────────────────
class ScienceFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!
    private val tabs = listOf(
        "🛰 МКС"        to { IssTrackerFragment() as Fragment },
        "🪐 Вес"        to { PlanetWeightFragment() as Fragment },
        "🌕 Луна"       to { MoonPhaseFragment() as Fragment },
        "📡 Сейсмо"     to { SeismographFragment() as Fragment }
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

// ── МКС трекер ───────────────────────────────────────────────────────────────
class IssTrackerFragment : Fragment() {
    private var _b: FragmentIssTrackerBinding? = null
    private val b get() = _b!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() { fetchIss(); handler.postDelayed(this, 5000L) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentIssTrackerBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnRefreshIss.setOnClickListener { fetchIss() }
        b.tvIssHint.text = "🛰 МКС летит со скоростью ~7.7 км/с, делая оборот за ~92 минуты"
        fetchIss()
    }
    override fun onResume() { super.onResume(); handler.post(refreshTick) }
    override fun onPause()  { super.onPause();  handler.removeCallbacks(refreshTick) }

    private fun fetchIss() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    JSONObject(URL("http://api.open-notify.org/iss-now.json").readText())
                }
                val pos = json.getJSONObject("iss_position")
                val lat = pos.getString("latitude").toDouble()
                val lon = pos.getString("longitude").toDouble()
                val timestamp = json.getLong("timestamp")

                b.tvIssPosition.text = buildString {
                    appendLine("🛰 МКС сейчас:")
                    appendLine("📍 Широта:  ${"%.4f".format(lat)}°")
                    appendLine("📍 Долгота: ${"%.4f".format(lon)}°")
                    appendLine()
                    appendLine(getOceanOrContinent(lat, lon))
                    appendLine()
                    appendLine("⏱ Обновлено: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(timestamp * 1000))}")
                }
                // Простое ASCII представление позиции
                drawIssOnMap(lat, lon)
            } catch (e: Exception) {
                b.tvIssPosition.text = "❌ ${e.message}"
            }
        }
    }

    private fun getOceanOrContinent(lat: Double, lon: Double): String {
        return when {
            lat in -60.0..70.0 && lon in -90.0..(-30.0) -> "🌊 Над Атлантическим океаном"
            lat in -60.0..70.0 && lon in 30.0..150.0    -> "🌊 Над Индийским/Тихим океаном"
            lat in 35.0..70.0  && lon in (-10.0)..60.0  -> "🌍 Над Европой/Россией"
            lat in -35.0..35.0 && lon in (-20.0)..50.0  -> "🌍 Над Африкой"
            lat in -55.0..12.0 && lon in (-80.0)..(-35.0) -> "🌎 Над Южной Америкой"
            lat in 15.0..70.0  && lon in (-170.0)..(-60.0) -> "🌎 Над Северной Америкой"
            lat in 10.0..55.0  && lon in 60.0..150.0    -> "🌏 Над Азией"
            lat < -60.0                                  -> "🧊 Над Антарктидой"
            else -> "🌊 Над океаном"
        }
    }

    private fun drawIssOnMap(lat: Double, lon: Double) {
        // 40x20 ASCII карта
        val W = 40; val H = 20
        val map = Array(H) { CharArray(W) { '·' } }
        // Основные контуры континентов (приблизительно)
        val x = ((lon + 180) / 360 * W).toInt().coerceIn(0, W-1)
        val y = ((90 - lat) / 180 * H).toInt().coerceIn(0, H-1)
        map[y][x] = '🛰'
        b.tvIssMap.text = map.joinToString("\n") { String(it) } + "\n(·= океан, 🛰=МКС)"
    }

    override fun onDestroyView() { handler.removeCallbacks(refreshTick); super.onDestroyView(); _b = null }
}

// ── Вес на планетах ──────────────────────────────────────────────────────────
class PlanetWeightFragment : Fragment() {
    private var _b: FragmentPlanetWeightBinding? = null
    private val b get() = _b!!

    private val planets = listOf(
        "☿ Меркурий" to 0.378, "♀ Венера"   to 0.907, "🌍 Земля"    to 1.000,
        "♂ Марс"     to 0.377, "♃ Юпитер"   to 2.364, "♄ Сатурн"   to 0.916,
        "♅ Уран"     to 0.889, "♆ Нептун"   to 1.120, "🌙 Луна"     to 0.166,
        "☀ Солнце"   to 27.90, "⬛ Плутон"   to 0.063
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPlanetWeightBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnCalcWeight.setOnClickListener { calculate() }
        b.tvWeightHint.text = "Введи вес на Земле → узнай свой вес на всех планетах"
    }

    private fun calculate() {
        val earthWeight = b.etEarthWeight.text.toString().toDoubleOrNull()
        if (earthWeight == null || earthWeight <= 0) {
            b.tvPlanetWeights.text = "Введи корректный вес"; return
        }
        b.tvPlanetWeights.text = planets.joinToString("\n") { (name, gravity) ->
            val w = earthWeight * gravity
            "$name: ${"%.1f".format(w)} кг"
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Фазы луны ────────────────────────────────────────────────────────────────
class MoonPhaseFragment : Fragment() {
    private var _b: FragmentMoonPhaseBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMoonPhaseBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showMoonPhase()
        b.btnMoonCalendar.setOnClickListener { showCalendar() }
    }

    private fun showMoonPhase() {
        val cal = Calendar.getInstance()
        val phase = getMoonPhase(cal)
        val (emoji, name, desc) = getMoonInfo(phase)
        b.tvMoonEmoji.text = emoji
        b.tvMoonName.text = name
        b.tvMoonDesc.text = desc
        b.tvMoonPhaseNum.text = "Фаза: ${"%.0f".format(phase * 100)}%"
        b.progressMoonPhase.progress = (phase * 100).toInt()
    }

    /**
     * Вычисляем фазу луны по алгоритму Конвея.
     * Возвращает 0.0 (новолуние) до 1.0 (полнолуние и обратно).
     */
    private fun getMoonPhase(cal: Calendar): Double {
        val year  = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day   = cal.get(Calendar.DAY_OF_MONTH)
        var r = year % 100
        r %= 19; if (r > 9) r -= 19
        r = ((r * 11) % 30) + month + day
        if (month < 3) r += 2
        r -= if (year < 2000) 4 else 8.1.toInt()
        r = ((r + 90) % 30)
        return r / 29.5
    }

    private fun getMoonInfo(phase: Double): Triple<String, String, String> {
        return when {
            phase < 0.03 -> Triple("🌑", "Новолуние", "Луна не видна. Начало лунного цикла.")
            phase < 0.22 -> Triple("🌒", "Молодая луна", "Растущий серп на западе после заката.")
            phase < 0.28 -> Triple("🌓", "Первая четверть", "Луна освещена наполовину, растёт.")
            phase < 0.47 -> Triple("🌔", "Прибывающая луна", "Видна большая часть диска, растёт.")
            phase < 0.53 -> Triple("🌕", "Полнолуние", "Луна полностью освещена. Самое яркое время.")
            phase < 0.72 -> Triple("🌖", "Убывающая луна", "Диск уменьшается, видна после полуночи.")
            phase < 0.78 -> Triple("🌗", "Последняя четверть", "Полуосвещённая, убывает.")
            phase < 0.97 -> Triple("🌘", "Старая луна", "Узкий серп на востоке перед рассветом.")
            else          -> Triple("🌑", "Новолуние", "Цикл завершается.")
        }
    }

    private fun showCalendar() {
        val sb = StringBuilder("📅 Ближайшие фазы:\n\n")
        val cal = Calendar.getInstance()
        repeat(30) { day ->
            val c = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, day) }
            val phase = getMoonPhase(c)
            val (emoji, name, _) = getMoonInfo(phase)
            if (phase < 0.05 || (phase > 0.48 && phase < 0.52) ||
                (phase > 0.23 && phase < 0.27) || (phase > 0.73 && phase < 0.77)) {
                val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale("ru"))
                sb.appendLine("${sdf.format(c.time)}  $emoji $name")
            }
        }
        b.tvMoonCalendar.text = sb.toString()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Сейсмограф ──────────────────────────────────────────────────────────────
class SeismographFragment : Fragment(), SensorEventListener {
    private var _b: FragmentSeismographBinding? = null
    private val b get() = _b!!
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var recording = false
    private val readings = mutableListOf<Float>()
    private val MAX_READINGS = 100

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSeismographBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        b.btnSeismoToggle.setOnClickListener {
            if (recording) { stopRecording() } else { startRecording() }
        }
        b.btnSeismoClear.setOnClickListener { readings.clear(); drawSeismograph() }
        b.tvSeismoHint.text = "📡 Записывает ускорение как сейсмограф. Положи телефон на стол и топни."
    }

    private fun startRecording() {
        recording = true
        b.btnSeismoToggle.text = "⏹ Стоп"
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    private fun stopRecording() {
        recording = false
        b.btnSeismoToggle.text = "⏺ Запись"
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val mag = sqrt(event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2))
        readings.add(mag)
        if (readings.size > MAX_READINGS) readings.removeAt(0)
        drawSeismograph()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun drawSeismograph() {
        if (readings.isEmpty()) return
        val max = readings.max().coerceAtLeast(10f)
        val height = 20
        val sb = StringBuilder()
        // Вертикальный ASCII график
        for (row in height downTo 0) {
            val threshold = row.toFloat() / height * max
            sb.append("|")
            readings.forEach { v ->
                sb.append(if (v >= threshold) "█" else " ")
            }
            sb.appendLine()
        }
        sb.append("+").append("-".repeat(readings.size))
        val lastMag = readings.lastOrNull() ?: 0f
        val richter = (log10(lastMag.toDouble().coerceAtLeast(0.001)) + 2).coerceAtLeast(0.0)
        b.tvSeismoGraph.text = sb.toString()
        b.tvSeismoStats.text = "Последнее: ${"%.2f".format(lastMag)} м/с²  |  ~${"%.1f".format(richter)} по Рихтеру (оценочно)"
    }

    override fun onPause()  { stopRecording(); super.onPause() }
    override fun onDestroyView() { stopRecording(); super.onDestroyView(); _b = null }
}

private fun Float.pow(exp: Int): Float = this.toDouble().pow(exp).toFloat()
