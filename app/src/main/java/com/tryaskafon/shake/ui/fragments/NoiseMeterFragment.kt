package com.tryaskafon.shake.osint

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentNoiseMeterBinding
import kotlin.math.log10

/**
 * NoiseMeterFragment v2 — исправлен "start failed".
 * Причина: повторный вызов startMeter() без release предыдущего recorder.
 * Теперь stopMeter() всегда вызывается перед новым стартом.
 */
class NoiseMeterFragment : Fragment() {

    private var _b: FragmentNoiseMeterBinding? = null
    private val b get() = _b!!

    private var recorder: MediaRecorder? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private var maxDb = 0.0

    private val readAmplitude = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                val amp = recorder?.maxAmplitude ?: 0
                if (amp > 0) {
                    val db = (20 * log10(amp.toDouble() / 32768.0) + 90.0).coerceIn(30.0, 120.0)
                    if (db > maxDb) maxDb = db
                    updateUI(db)
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentNoiseMeterBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnStartNoise.setOnClickListener {
            if (running) stopMeter() else startMeter()
        }
        b.btnResetMax.setOnClickListener {
            maxDb = 0.0
            b.tvMaxDb.text = "Макс: 0.0 дБ"
        }
    }

    private fun startMeter() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            b.tvDbValue.text = "Нет разрешения на микрофон"
            return
        }

        // Всегда освобождаем предыдущий перед стартом — это и было причиной "start failed"
        releaseRecorder()

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(requireContext())
            else
                @Suppress("DEPRECATION") MediaRecorder()

            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(requireContext().cacheDir.absolutePath + "/noise_meter_tmp.3gp")
                prepare()
                start()
            }

            running = true
            b.btnStartNoise.text = "⏹ Стоп"
            handler.post(readAmplitude)

        } catch (e: Exception) {
            releaseRecorder()
            b.tvDbValue.text = "Ошибка: ${e.message}"
        }
    }

    private fun stopMeter() {
        running = false
        handler.removeCallbacks(readAmplitude)
        releaseRecorder()
        b.btnStartNoise.text = "▶ Старт"
        b.progressNoise.progress = 0
    }

    private fun releaseRecorder() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun updateUI(db: Double) {
        b.tvDbValue.text   = "${"%.1f".format(db)} дБ"
        b.tvMaxDb.text     = "Макс: ${"%.1f".format(maxDb)} дБ"
        b.progressNoise.progress = db.toInt()
        b.tvNoiseLevel.text = when {
            db < 40  -> "😴 Тихо"
            db < 55  -> "🗣 Нормально"
            db < 70  -> "😬 Громко"
            db < 85  -> "😨 Очень громко"
            else     -> "😱 ОПАСНО!"
        }
        val color = when {
            db < 55 -> android.graphics.Color.parseColor("#4CAF50")
            db < 70 -> android.graphics.Color.parseColor("#FFC107")
            db < 85 -> android.graphics.Color.parseColor("#FF9800")
            else    -> android.graphics.Color.parseColor("#F44336")
        }
        b.progressNoise.progressTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun onDestroyView() {
        stopMeter()
        super.onDestroyView()
        _b = null
    }
}
