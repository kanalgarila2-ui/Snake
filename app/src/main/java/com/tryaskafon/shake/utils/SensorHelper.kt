package com.tryaskafon.shake.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log

/**
 * SensorHelper — утилиты для работы с датчиками Android.
 */
object SensorHelper {

    private const val TAG = "SensorHelper"

    /**
     * Проверяет наличие акселерометра на устройстве.
     * @return true если акселерометр доступен
     */
    fun hasAccelerometer(context: Context): Boolean {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val has = sensor != null
            Log.d(TAG, "Акселерометр: ${if (has) "найден (${sensor?.name})" else "НЕ НАЙДЕН"}")
            has
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки акселерометра: ${e.message}", e)
            false
        }
    }

    /**
     * Получить информацию об акселерометре (для отладки).
     * @return строка с характеристиками или null
     */
    fun getAccelerometerInfo(context: Context): String? {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            buildString {
                append("Модель: ${sensor.name}\n")
                append("Производитель: ${sensor.vendor}\n")
                append("Версия: ${sensor.version}\n")
                append("Максимальный диапазон: ${sensor.maximumRange} м/с²\n")
                append("Разрешение: ${sensor.resolution}\n")
                append("Мощность: ${sensor.power} мА")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения информации о сенсоре: ${e.message}", e)
            null
        }
    }
}
