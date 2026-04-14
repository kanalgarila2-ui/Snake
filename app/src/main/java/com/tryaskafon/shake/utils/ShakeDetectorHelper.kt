package com.tryaskafon.shake.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * ShakeDetectorHelper — автономный детектор тряски для фрагментов.
 * Работает НЕЗАВИСИМО от ShakeDetectorService.
 * Фрагменты используют его чтобы тряска работала ВСЕГДА,
 * даже когда основной сервис выключен.
 *
 * Использование:
 *   private val shakeHelper = ShakeDetectorHelper(requireContext()) { onShake() }
 *   override fun onResume() { shakeHelper.start() }
 *   override fun onPause()  { shakeHelper.stop() }
 */
class ShakeDetectorHelper(
    private val context: Context,
    private val sensitivity: Float = 14f,   // м/с², порог тряски
    private val cooldownMs: Long = 800L,     // антиспам между тряскими
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val (x, y, z) = event.values
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        if (magnitude > sensitivity && now - lastShakeTime > cooldownMs) {
            lastShakeTime = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
