package com.tryaskafon.shake.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tryaskafon.shake.MainActivity
import com.tryaskafon.shake.R
import com.tryaskafon.shake.utils.AudioPlayer
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private val TAG = "ShakeDetectorService"

    companion object {
        const val CHANNEL_ID       = "shake_detector"
        const val NOTIFICATION_ID  = 1001
        const val EXTRA_FILE_PATH  = "extra_file_path"
        const val EXTRA_SENSITIVITY= "extra_sensitivity"
        const val EXTRA_VIBRATE    = "extra_vibrate"
        const val EXTRA_TIMESTAMP  = "extra_timestamp"
        const val ACTION_SHAKE_DETECTED   = "com.tryaskafon.shake.SHAKE_DETECTED"
        const val ACTION_SERVICE_STOPPED  = "com.tryaskafon.shake.SERVICE_STOPPED"
        const val ACTION_PLAYBACK_STARTED = "com.tryaskafon.shake.PLAYBACK_STARTED"
        const val ACTION_PLAYBACK_STOPPED = "com.tryaskafon.shake.PLAYBACK_STOPPED"
        const val ACTION_STOP_SERVICE     = "com.tryaskafon.shake.STOP_SERVICE"

        // Статический флаг — для виджета
        @Volatile var isRunning = false

        fun start(ctx: Context, filePath: String, sensitivity: Int, vibrateEnabled: Boolean) {
            val intent = Intent(ctx, ShakeDetectorService::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SENSITIVITY, sensitivity)
                putExtra(EXTRA_VIBRATE, vibrateEnabled)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, ShakeDetectorService::class.java))
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioPlayer: AudioPlayer
    private var wakeLock: PowerManager.WakeLock? = null

    private var filePath = ""
    private var sensitivity = 15f
    private var vibrateEnabled = false

    @Volatile private var isShaking = false
    private val handler = Handler(Looper.getMainLooper())
    private val resetShaking = Runnable { isShaking = false }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        audioPlayer = AudioPlayer(this).apply {
            // ИСПРАВЛЕНИЕ: колбэки для ProgressBar
            onPlaybackStarted = { sendBroadcast(Intent(ACTION_PLAYBACK_STARTED).apply { setPackage(packageName) }) }
            onPlaybackStopped = { sendBroadcast(Intent(ACTION_PLAYBACK_STOPPED).apply { setPackage(packageName) }) }
        }

        createNotificationChannel()

        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TryaskaFon::ShakeWakeLock")
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (e: Exception) { Log.e(TAG, "WakeLock: ${e.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            sendBroadcast(Intent(ACTION_SERVICE_STOPPED).apply { setPackage(packageName) })
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.let {
            filePath       = it.getStringExtra(EXTRA_FILE_PATH) ?: ""
            sensitivity    = it.getIntExtra(EXTRA_SENSITIVITY, 15).toFloat()
            vibrateEnabled = it.getBooleanExtra(EXTRA_VIBRATE, false)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        return START_REDELIVER_INTENT
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = event.values
        val mag = sqrt(x * x + y * y + z * z)
        if (mag > sensitivity && !isShaking) {
            isShaking = true
            val ts = System.currentTimeMillis()
            audioPlayer.play(filePath)
            if (vibrateEnabled) vibrate()
            sendBroadcast(Intent(ACTION_SHAKE_DETECTED).apply {
                putExtra(EXTRA_TIMESTAMP, ts)
                setPackage(packageName)
            })
            handler.removeCallbacks(resetShaking)
            handler.postDelayed(resetShaking, 1000L)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        try { audioPlayer.release() } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1,
            Intent(this, ShakeDetectorService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ТряскаФон активен 🔥")
            .setContentText("Порог: ${sensitivity.toInt()} м/с²")
            .setSmallIcon(R.drawable.ic_shake)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Stop", stopPi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Детектор тряски", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun vibrate() {
        try {
            val pattern = longArrayOf(0L, 100L, 50L, 100L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(pattern, -1)
            }
        } catch (e: Exception) { Log.e(TAG, "Vibrate: ${e.message}") }
    }
}
