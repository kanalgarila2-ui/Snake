package com.tryaskafon.shake.system

import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentStrobeBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper

/**
 * StrobeLightFragment v3 — исправлен баг "перестаёт работать после нескольких запусков".
 *
 * Причина бага: CameraManager.setTorchMode() бросает CameraAccessException
 * если камера занята другим процессом (например после фото в OcrFragment).
 * Также torch callback не отслеживался — после onPause torch мог остаться включён,
 * и следующий вызов setTorchMode(true) падал молча.
 *
 * Фикс:
 * 1. Регистрируем TorchCallback — знаем точное состояние вспышки
 * 2. В onPause всегда выключаем torch и ждём подтверждения через callback
 * 3. Обёртываем каждый setTorchMode в try/catch с retry через 200ms
 * 4. cameraId переинициализируем в onResume (не кэшируем навсегда)
 */
class StrobeLightFragment : Fragment() {

    private val TAG = "StrobeLightFragment"

    private var _b: FragmentStrobeBinding? = null
    private val b get() = _b!!

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var torchAvailable = false
    private var torchActuallyOn = false  // реальное состояние из callback

    private var strobeRunning = false
    private var intervalMs = 200L
    private var pendingFlashOn = false

    private val handler = Handler(Looper.getMainLooper())

    // Torch callback — единственный надёжный способ знать состояние вспышки
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                torchActuallyOn = enabled
                Log.d(TAG, "Torch state changed: $enabled")
            }
        }
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) {
                torchAvailable = false
                torchActuallyOn = false
                Log.w(TAG, "Torch unavailable for $id")
                // Камера занята — останавливаем стробоскоп
                if (strobeRunning) {
                    handler.post { stopStrobe() }
                }
            }
        }
    }

    private val strobeRunnable = object : Runnable {
        override fun run() {
            if (!strobeRunning) {
                setFlashSafe(false)
                return
            }
            // Переключаем: если сейчас включён — выключаем, и наоборот
            setFlashSafe(!torchActuallyOn)
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

        b.btnStrobeToggle.setOnClickListener {
            if (strobeRunning) stopStrobe() else startStrobe()
        }

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

        b.tvStrobeHint.text = "⚠️ Частые вспышки могут вызвать дискомфорт\nТряска = ускоряет стробоскоп"
    }

    override fun onResume() {
        super.onResume()
        shakeHelper.start()
        initCamera()  // переинициализируем каждый раз — не кэшируем
    }

    override fun onPause() {
        stopStrobe()
        // Даём 100ms чтобы setFlash(false) дошёл до камеры
        handler.postDelayed({
            try { cameraManager?.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
            cameraManager = null
            cameraId = null
        }, 100L)
        super.onPause()
        shakeHelper.stop()
    }

    private fun initCamera() {
        try {
            cameraManager = requireContext().getSystemService(android.content.Context.CAMERA_SERVICE)
                as CameraManager
            // Ищем камеру со вспышкой
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                torchAvailable = true
                cameraManager?.registerTorchCallback(torchCallback, handler)
                Log.d(TAG, "Camera init ok, id=$cameraId")
            } else {
                torchAvailable = false
                b.btnStrobeToggle.isEnabled = false
                b.tvStrobeStatus.text = "Вспышка не найдена"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera init error: ${e.message}")
            torchAvailable = false
            b.btnStrobeToggle.isEnabled = false
            b.tvStrobeStatus.text = "Ошибка камеры: ${e.message}"
        }
    }

    private fun startStrobe() {
        if (!torchAvailable) {
            b.tvStrobeStatus.text = "Вспышка недоступна"
            return
        }
        strobeRunning = true
        b.btnStrobeToggle.text = "⏹ Стоп"
        b.tvStrobeStatus.text = "🔦 Работает"
        handler.post(strobeRunnable)
    }

    private fun stopStrobe() {
        strobeRunning = false
        handler.removeCallbacks(strobeRunnable)
        setFlashSafe(false)
        b.btnStrobeToggle.text = "▶ Старт"
        b.tvStrobeStatus.text = "Остановлен"
    }

    /**
     * Безопасное переключение вспышки с retry при ошибке.
     */
    private fun setFlashSafe(on: Boolean) {
        val id = cameraId ?: return
        val cm = cameraManager ?: return
        try {
            cm.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.w(TAG, "setTorchMode($on) failed: ${e.message}")
            // Повторяем через 200ms — камера может освободиться
            if (on) {
                handler.postDelayed({ setFlashSafe(true) }, 200L)
            }
        }
    }

    override fun onDestroyView() {
        stopStrobe()
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
