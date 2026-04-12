package com.tryaskafon.shake.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentStrobeBinding
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * StrobeLightFragment — стробоскоп на вспышке камеры.
 * Частота мигания: SeekBar или тряска (трясёшь быстрее → чаще моргает).
 */
class StrobeLightFragment : Fragment() {

    private var _b: FragmentStrobeBinding? = null
    private val b get() = _b!!

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var strobeRunning = false
    private var flashOn = false
    private var intervalMs = 200L  // дефолт 5 Hz

    private val handler = Handler(Looper.getMainLooper())
    private val strobeRunnable = object : Runnable {
        override fun run() {
            if (!strobeRunning) { setFlash(false); return }
            toggleFlash()
            handler.postDelayed(this, intervalMs)
        }
    }

    // Тряска = ускоряет стробоскоп
    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED) {
                intervalMs = (intervalMs - 20L).coerceAtLeast(50L)
                b.tvStrobeHz.text = "${1000L / intervalMs} Hz"
                b.seekBarStrobe.progress = (1000L / intervalMs).toInt()
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStrobeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            b.btnStrobeToggle.isEnabled = false
            b.tvStrobeStatus.text = "Вспышка недоступна: ${e.message}"
        }

        b.btnStrobeToggle.setOnClickListener {
            if (strobeRunning) stopStrobe() else startStrobe()
        }

        // SeekBar: 1..20 Hz
        b.seekBarStrobe.min = 1
        b.seekBarStrobe.max = 20
        b.seekBarStrobe.progress = 5
        b.tvStrobeHz.text = "5 Hz"
        b.seekBarStrobe.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                val hz = p.coerceAtLeast(1)
                intervalMs = 1000L / hz
                b.tvStrobeHz.text = "$hz Hz"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        b.tvStrobeHint.text = "⚠️ Осторожно: частые вспышки могут вызвать дискомфорт\nТряска = ускоряет стробоскоп"
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(ShakeDetectorService.ACTION_SHAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(shakeReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(shakeReceiver, f)
    }

    override fun onPause() {
        super.onPause()
        stopStrobe()
        try { requireContext().unregisterReceiver(shakeReceiver) } catch (_: Exception) {}
    }

    private fun startStrobe() {
        strobeRunning = true
        b.btnStrobeToggle.text = "⏹ Стоп"
        b.tvStrobeStatus.text = "🔦 Работает"
        handler.post(strobeRunnable)
    }

    private fun stopStrobe() {
        strobeRunning = false
        handler.removeCallbacks(strobeRunnable)
        setFlash(false)
        b.btnStrobeToggle.text = "▶ Старт"
        b.tvStrobeStatus.text = "Остановлен"
    }

    private fun toggleFlash() {
        flashOn = !flashOn
        setFlash(flashOn)
    }

    private fun setFlash(on: Boolean) {
        try {
            cameraManager?.setTorchMode(cameraId ?: return, on)
        } catch (e: Exception) {
            // Вспышка занята или недоступна
        }
    }

    override fun onDestroyView() { stopStrobe(); super.onDestroyView(); _b = null }
}
