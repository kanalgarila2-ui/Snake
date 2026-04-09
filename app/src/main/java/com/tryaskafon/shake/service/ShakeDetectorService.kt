package com.tryaskafon.shake.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tryaskafon.shake.MainActivity
import com.tryaskafon.shake.R
import com.tryaskafon.shake.utils.AudioPlayer
import kotlin.math.sqrt

/**
 * ShakeDetectorService — Foreground Service, который слушает акселерометр и
 * воспроизводит звук при обнаружении тряски.
 *
 * Не останавливается при сворачивании приложения.
 * Остановить можно только через Switch в UI или кнопку "Stop" в уведомлении.
 */
class ShakeDetectorService : Service(), SensorEventListener {

    private val TAG = "ShakeDetectorService"

    // --- Константы ---
    companion object {
        const val CHANNEL_ID = "shake_detector"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_SHAKE_ID = 1002

        // Ключи для Intent extras
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_TIMESTAMP = "extra_timestamp"

        // Broadcast actions
        const val ACTION_SHAKE_DETECTED = "com.tryaskafon.shake.SHAKE_DETECTED"
        const val ACTION_SERVICE_STOPPED = "com.tryaskafon.shake.SERVICE_STOPPED"
        const val ACTION_STOP_SERVICE = "com.tryaskafon.shake.STOP_SERVICE"

        /**
         * Запустить сервис с параметрами.
         */
        fun start(context: Context, filePath: String, sensitivity: Int, vibrateEnabled: Boolean) {
            val intent = Intent(context, ShakeDetectorService::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SENSITIVITY, sensitivity)
                putExtra(EXTRA_VIBRATE, vibrateEnabled)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Остановить сервис.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeDetectorService::class.java))
        }
    }

    // --- Системные сервисы ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var notificationManager: NotificationManager

    // WakeLock — чтобы CPU не засыпал во время работы сервиса
    private var wakeLock: PowerManager.WakeLock? = null

    // Проигрыватель звука
    private lateinit var audioPlayer: AudioPlayer

    // --- Параметры из Intent ---
    private var filePath: String = ""
    private var sensitivity: Float = 15f
    private var vibrateEnabled: Boolean = false

    // --- Антиспам тряски ---
    // Флаг: мы сейчас "в состоянии тряски" и не должны реагировать повторно
    @Volatile
    private var isShaking = false

    // Handler для сброса isShaking через 1 секунду
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val resetShakingRunnable = Runnable {
        isShaking = false
        Log.d(TAG, "isShaking сброшен — готов к следующей тряске")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // Инициализация SensorManager и акселерометра
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "Акселерометр не найден! Сервис будет работать вхолостую.")
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        audioPlayer = AudioPlayer(this)

        // Создаём канал уведомлений (только Android 8+)
        createNotificationChannel()

        // WakeLock — CPU не спит пока сервис активен
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TryaskaFon::ShakeWakeLock"
            ).also {
                it.acquire(12 * 60 * 60 * 1000L) // Максимум 12 часов
            }
            Log.d(TAG, "WakeLock получен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения WakeLock: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand, action=${intent?.action}")

        // Обрабатываем кнопку "Stop" из уведомления
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Получена команда остановки из уведомления")
            broadcastServiceStopped()
            stopSelf()
            return START_NOT_STICKY
        }

        // Читаем параметры из Intent
        intent?.let {
            filePath = it.getStringExtra(EXTRA_FILE_PATH) ?: ""
            sensitivity = it.getIntExtra(EXTRA_SENSITIVITY, 15).toFloat()
            vibrateEnabled = it.getBooleanExtra(EXTRA_VIBRATE, false)
        }

        Log.d(TAG, "Параметры: filePath=$filePath, sensitivity=$sensitivity, vibrate=$vibrateEnabled")

        // Запускаем foreground с уведомлением
        startForeground(NOTIFICATION_ID, buildMainNotification())

        // Регистрируем слушатель акселерометра
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI // ~20000 мкс = 50 Hz
            )
            Log.d(TAG, "Слушатель акселерометра зарегистрирован (SENSOR_DELAY_UI)")
        } ?: Log.e(TAG, "Акселерометр null, слушатель не зарегистрирован")

        // Сервис перезапустится при убийстве системой с последним Intent
        return START_REDELIVER_INTENT
    }

    /**
     * Главный метод сенсора — вызывается ~50 раз в секунду.
     * Вычисляем magnitude, сравниваем с порогом, реагируем на тряску.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Вычисляем magnitude вектора ускорения
        val magnitude = sqrt(x * x + y * y + z * z)

        // Лог на уровне VERBOSE — не замусориваем, включается только при отладке
        // Log.v(TAG, "Magnitude: $magnitude (порог: $sensitivity)")

        if (magnitude > sensitivity && !isShaking) {
            // ТРЯСКА ОБНАРУЖЕНА!
            isShaking = true
            val timestamp = System.currentTimeMillis()
            Log.i(TAG, "ТРЯСКА! magnitude=$magnitude > sensitivity=$sensitivity")

            // 1. Воспроизводим звук (асинхронно через AudioPlayer)
            audioPlayer.play(filePath) { error ->
                Log.e(TAG, "Ошибка воспроизведения: $error")
            }

            // 2. Вибрация (если включена)
            if (vibrateEnabled) {
                performVibration()
            }

            // 3. Broadcast → MainActivity для обновления счётчика
            broadcastShakeDetected(timestamp)

            // 4. Обновляем уведомление "Тряска! 🎵"
            showShakeNotification()

            // 5. Через 1 секунду сбрасываем флаг (антиспам)
            handler.removeCallbacks(resetShakingRunnable)
            handler.postDelayed(resetShakingRunnable, 1000L)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Точность сенсора изменилась: $accuracy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy — освобождаем ресурсы")

        // Отменяем слушатель сенсора
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка отмены слушателя: ${e.message}")
        }

        // Освобождаем WakeLock
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            Log.d(TAG, "WakeLock освобождён")
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка освобождения WakeLock: ${e.message}")
        }

        // Освобождаем AudioPlayer (MediaPlayer)
        try {
            audioPlayer.release()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка освобождения AudioPlayer: ${e.message}")
        }

        // Убираем отложенные колбэки
        handler.removeCallbacksAndMessages(null)
    }

    // -------------------------------------------------------------------------
    // Приватные методы
    // -------------------------------------------------------------------------

    /**
     * Создаём канал уведомлений для Android 8+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Детектор тряски",
                NotificationManager.IMPORTANCE_LOW // Без звука, чтобы не мешать
            ).apply {
                description = "Уведомления о работе сервиса ТряскаФон"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Канал уведомлений создан: $CHANNEL_ID")
        }
    }

    /**
     * Строим основное persistent-уведомление сервиса.
     */
    private fun buildMainNotification(): Notification {
        // Intent для открытия MainActivity при тапе на уведомление
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent для кнопки "Stop" в уведомлении
        val stopIntent = Intent(this, ShakeDetectorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ТряскаФон активен 🔥")
            .setContentText("Жду тряску... Порог: ${sensitivity.toInt()} м/с²")
            .setSmallIcon(R.drawable.ic_shake)
            .setContentIntent(openAppPi)
            .setOngoing(true) // Нельзя смахнуть пальцем
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
    }

    /**
     * Показываем краткое уведомление "Тряска обнаружена!".
     */
    private fun showShakeNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Тряска! 🎵")
            .setContentText("Воспроизвожу звук...")
            .setSmallIcon(R.drawable.ic_shake)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(3000L) // Само исчезнет через 3 сек
            .build()

        try {
            notificationManager.notify(NOTIFICATION_SHAKE_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка показа уведомления тряски: ${e.message}")
        }
    }

    /**
     * Отправляем Broadcast с событием SHAKE_DETECTED.
     */
    private fun broadcastShakeDetected(timestamp: Long) {
        val intent = Intent(ACTION_SHAKE_DETECTED).apply {
            putExtra(EXTRA_TIMESTAMP, timestamp)
            setPackage(packageName) // Только наше приложение получит broadcast
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast SHAKE_DETECTED отправлен, timestamp=$timestamp")
    }

    /**
     * Отправляем Broadcast с событием SERVICE_STOPPED (при остановке через уведомление).
     */
    private fun broadcastServiceStopped() {
        val intent = Intent(ACTION_SERVICE_STOPPED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast SERVICE_STOPPED отправлен")
    }

    /**
     * Вибрация по паттерну: пауза 0ms, виб 100ms, пауза 50ms, виб 100ms.
     */
    private fun performVibration() {
        try {
            val pattern = longArrayOf(0L, 100L, 50L, 100L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ — используем VibratorManager
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8-11
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                // Android 7
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            Log.d(TAG, "Вибрация запущена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка вибрации: ${e.message}", e)
        }
    }
}
