package com.tryaskafon.shake.system

import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentStrobeBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper

class StrobeLightFragment : Fragment() {

    private var _b: FragmentStrobeBinding? = null
    private val b get() = _b!!

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var strobeRunning = false
    private var flashOn = false
    private var intervalMs = 200L

    private val handler = Handler(Looper.getMainLooper())
    private val strobeRunnable = object : Runnable {
        override fun run() {
            if (!strobeRunning) { setFlash(false); return }
            toggleFlash()
            handler.postDelayed(this, intervalMs)
        }
    }

    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStrobeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext(), cooldownMs = 300L) {
            intervalMs = (intervalMs - 20L).coerceAtLeast(50L)
            b.tvStrobeHz.text = "${1000L / intervalMs} Hz"
            b.seekBarStrobe.progress = (1000L / intervalMs).toInt()
        }

        try {
            cameraManager = requireContext().getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            b.btnStrobeToggle.isEnabled = false
            b.tvStrobeStatus.text = "Вспышка недоступна"
        }

        b.btnStrobeToggle.setOnClickListener { if (strobeRunning) stopStrobe() else startStrobe() }
        b.seekBarStrobe.min = 1; b.seekBarStrobe.max = 20; b.seekBarStrobe.progress = 5
        b.tvStrobeHz.text = "5 Hz"
        b.seekBarStrobe.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                val hz = p.coerceAtLeast(1); intervalMs = 1000L / hz; b.tvStrobeHz.text = "$hz Hz"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        b.tvStrobeHint.text = "⚠️ Частые вспышки могут вызвать дискомфорт\nТряска = ускоряет стробоскоп"
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { stopStrobe(); super.onPause(); shakeHelper.stop() }

    private fun startStrobe() {
        strobeRunning = true; b.btnStrobeToggle.text = "⏹ Стоп"; b.tvStrobeStatus.text = "🔦 Работает"
        handler.post(strobeRunnable)
    }
    private fun stopStrobe() {
        strobeRunning = false; handler.removeCallbacks(strobeRunnable); setFlash(false)
        b.btnStrobeToggle.text = "▶ Старт"; b.tvStrobeStatus.text = "Остановлен"
    }
    private fun toggleFlash() { flashOn = !flashOn; setFlash(flashOn) }
    private fun setFlash(on: Boolean) {
        try { cameraManager?.setTorchMode(cameraId ?: return, on) } catch (_: Exception) {}
    }

    override fun onDestroyView() { stopStrobe(); super.onDestroyView(); _b = null }
}
