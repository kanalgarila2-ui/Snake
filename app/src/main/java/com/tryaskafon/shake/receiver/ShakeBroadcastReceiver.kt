package com.tryaskafon.shake.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ShakeBroadcastReceiver — зарегистрирован в манифесте как запасной получатель.
 *
 * Основная логика обработки событий тряски находится в MainActivity
 * (там он регистрируется динамически в onResume/onPause).
 *
 * Этот статический receiver нужен для получения событий когда приложение
 * свёрнуто, если понадобится дополнительная логика (расширяемость).
 */
class ShakeBroadcastReceiver : BroadcastReceiver() {

    private val TAG = "ShakeBroadcastReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            "com.tryaskafon.shake.SHAKE_DETECTED" -> {
                val timestamp = intent.getLongExtra("extra_timestamp", 0L)
                Log.d(TAG, "Получен SHAKE_DETECTED (статический receiver), timestamp=$timestamp")
                // Дополнительная логика при необходимости
                // Например: запись в базу данных, уведомление третьих сторон и т.д.
            }
            "com.tryaskafon.shake.SERVICE_STOPPED" -> {
                Log.d(TAG, "Получен SERVICE_STOPPED (статический receiver)")
            }
            else -> {
                Log.w(TAG, "Неизвестное действие: ${intent.action}")
            }
        }
    }
}
