package com.tryaskafon.shake.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentMorseBinding
import kotlin.math.sqrt

/**
 * MorseFragment — ввод азбуки Морзе встряхиванием.
 * Короткая тряска → точка (·)
 * Длинная тряска  → тире (−)
 * Пауза 1.5 сек   → конец буквы (декодируем)
 * Пауза 3 сек     → пробел
 *
 * Реализует собственный SensorEventListener независимо от сервиса —
 * чтобы различать короткую и длинную тряску.
 */
class MorseFragment : Fragment(), SensorEventListener {

    private var _b: FragmentMorseBinding? = null
    private val b get() = _b!!

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var shakeStart = 0L
    private var lastShakeEnd = 0L
    private var isCurrentlyShaking = false
    private val currentSymbol = StringBuilder()   // накапливаем точки-тире текущей буквы
    private val decodedText   = StringBuilder()   // финальный текст

    private val handler = Handler(Looper.getMainLooper())
    private val letterTimeout = Runnable {
        // Конец буквы
        val letter = decodeMorse(currentSymbol.toString())
        if (letter.isNotEmpty()) {
            decodedText.append(letter)
            b.tvMorseDecoded.text = decodedText.toString()
        }
        currentSymbol.clear()
        updateSymbolDisplay()
    }
    private val wordTimeout = Runnable {
        decodedText.append(" ")
        b.tvMorseDecoded.text = decodedText.toString()
    }

    private val MORSE_TABLE = mapOf(
        "·−" to "А", "−···" to "Б", "·−−" to "В", "−−·" to "Г",
        "−··" to "Д", "·" to "Е", "···−−−···" to "Ж", "−··−" to "З",
        "··" to "И", "·−−−" to "Й", "−·−" to "К", "·−··" to "Л",
        "−−" to "М", "−·" to "Н", "−−−" to "О", "·−−·" to "П",
        "·−·" to "Р", "···" to "С", "−" to "Т", "··−" to "У",
        "··−·" to "Ф", "····" to "Х", "−·−·" to "Ц", "−−·" to "Ч",
        "−−−·" to "Ш", "−−·−" to "Щ", "−··−·" to "Ъ", "−·−−" to "Ы",
        "−···−" to "Ь", "·−··−" to "Э", "··−−" to "Ю", "·−·−" to "Я",
        // Латиница
        "·−" to "A", "−···" to "B", "−·−·" to "C", "−··" to "D",
        "·" to "E", "··−·" to "F", "−−·" to "G", "····" to "H",
        "··" to "I", "·−−−" to "J", "−·−" to "K", "·−··" to "L",
        "−−" to "M", "−·" to "N", "−−−" to "O", "·−−·" to "P",
        "−−·−" to "Q", "·−·" to "R", "···" to "S", "−" to "T",
        "··−" to "U", "···−" to "V", "·−−" to "W", "−··−" to "X",
        "−·−−" to "Y", "−−··" to "Z",
        // Цифры
        "·−−−−" to "1", "··−−−" to "2", "···−−" to "3", "····−" to "4",
        "·····" to "5", "−····" to "6", "−−···" to "7", "−−−··" to "8",
        "−−−−·" to "9", "−−−−−" to "0"
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMorseBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        b.btnMorseClear.setOnClickListener {
            currentSymbol.clear(); decodedText.clear()
            b.tvMorseSymbols.text = ""; b.tvMorseDecoded.text = ""
        }
        b.btnMorseCopy.setOnClickListener {
            val txt = b.tvMorseDecoded.text.toString()
            if (txt.isNotEmpty()) {
                val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("morse", txt))
            }
        }
        b.tvMorseHint.text = buildString {
            appendLine("📖 Инструкция:")
            appendLine("• Короткая тряска (<300 мс) → · (точка)")
            appendLine("• Длинная тряска  (>300 мс) → − (тире)")
            appendLine("• Пауза 1.5 сек → конец буквы")
            appendLine("• Пауза 3 сек   → пробел")
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val (x, y, z) = event.values
        val magnitude = sqrt(x*x + y*y + z*z)
        val threshold = 14f  // м/с² — порог тряски

        if (magnitude > threshold && !isCurrentlyShaking) {
            // Начало тряски
            isCurrentlyShaking = true
            shakeStart = System.currentTimeMillis()
            handler.removeCallbacks(letterTimeout)
            handler.removeCallbacks(wordTimeout)
        } else if (magnitude <= threshold && isCurrentlyShaking) {
            // Конец тряски
            isCurrentlyShaking = false
            val duration = System.currentTimeMillis() - shakeStart
            lastShakeEnd = System.currentTimeMillis()

            val symbol = if (duration < 300L) "·" else "−"
            currentSymbol.append(symbol)
            updateSymbolDisplay()

            // Таймер конца буквы (1.5 сек после последней тряски)
            handler.postDelayed(letterTimeout, 1500L)
            // Таймер пробела (3 сек)
            handler.postDelayed(wordTimeout, 3000L)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSymbolDisplay() {
        b.tvMorseSymbols.text = currentSymbol.toString()
    }

    private fun decodeMorse(code: String): String {
        return MORSE_TABLE[code] ?: if (code.isEmpty()) "" else "?"
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
