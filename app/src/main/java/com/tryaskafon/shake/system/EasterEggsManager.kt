package com.tryaskafon.shake.system

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import java.util.Calendar

/**
 * EasterEggsManager — отслеживает пасхалки и показывает их.
 *
 * Пасхалки:
 * 1. 10 трясок подряд быстро → "Ты мастер тряски 🏆"
 * 2. Открыть в полночь (23:55–00:05) → Ghost появляется
 * 3. Код 80085 в любом числовом поле → секретное сообщение
 * 4. Полная луна (приблизительно) → цвет темы меняется на серебряный
 */
object EasterEggsManager {

    private const val PREFS = "easter_eggs"
    private const val KEY_MASTER_COUNT = "master_shake_count"
    private const val KEY_LAST_MASTER  = "last_master_shown"

    private var consecutiveShakes = 0
    private var lastShakeTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    /** Вызывать при каждой тряске из MainActivity/ShakeFragment */
    fun onShake(
        context: Context,
        onMasterUnlocked: () -> Unit
    ) {
        val now = System.currentTimeMillis()
        // Сброс если пауза больше 2 сек
        if (now - lastShakeTime > 2000L) consecutiveShakes = 0
        lastShakeTime = now
        consecutiveShakes++

        if (consecutiveShakes >= 10) {
            consecutiveShakes = 0
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_MASTER_COUNT, 0) + 1
            prefs.edit().putInt(KEY_MASTER_COUNT, count).apply()
            onMasterUnlocked()
        }
    }

    /** Проверяем полночь при запуске */
    fun checkMidnight(onGhostAppear: () -> Unit) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min  = cal.get(Calendar.MINUTE)
        if ((hour == 23 && min >= 55) || (hour == 0 && min <= 5)) {
            onGhostAppear()
        }
    }

    /** Проверяем код 80085 */
    fun checkCode(input: String, onSecretUnlocked: () -> Unit) {
        if (input.contains("80085")) {
            onSecretUnlocked()
        }
    }

    /** Проверяем близость к полнолунию */
    fun isNearFullMoon(): Boolean {
        val cal = Calendar.getInstance()
        // Упрощённый расчёт: полнолуние ~каждые 29.5 дней
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val phase = (dayOfYear % 29.5) / 29.5
        return phase in 0.45..0.55
    }

    fun getMasterCount(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MASTER_COUNT, 0)
    }
}
