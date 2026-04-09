package com.tryaskafon.shake

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ShakeViewModel — хранит UI-состояние между поворотами экрана.
 * Всё взаимодействие с UI идёт через LiveData.
 */
class ShakeViewModel : ViewModel() {

    private val TAG = "ShakeViewModel"

    // --- Путь к MP3 файлу ---
    private val _filePath = MutableLiveData<String>("")
    val filePath: LiveData<String> = _filePath

    // --- Сервис запущен? ---
    private val _isServiceRunning = MutableLiveData<Boolean>(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    // --- Счётчик трясок ---
    private val _shakeCount = MutableLiveData<Int>(0)
    val shakeCount: LiveData<Int> = _shakeCount

    // --- Время последней тряски (unix ms, 0 = никогда) ---
    private val _lastShakeTime = MutableLiveData<Long>(0L)
    val lastShakeTime: LiveData<Long> = _lastShakeTime

    // --- Чувствительность (5..30 м/с²) ---
    private val _sensitivity = MutableLiveData<Int>(15)
    val sensitivity: LiveData<Int> = _sensitivity

    // --- Вибрировать при тряске ---
    private val _vibrateEnabled = MutableLiveData<Boolean>(false)
    val vibrateEnabled: LiveData<Boolean> = _vibrateEnabled

    // --- Громкость воспроизведения (0..100, для ProgressBar) ---
    private val _playbackVolume = MutableLiveData<Int>(0)
    val playbackVolume: LiveData<Int> = _playbackVolume

    /**
     * Обновить путь к файлу.
     * @param fromUi — если true, источник — EditText (не обновляем обратно, чтобы не было петли)
     */
    fun setFilePath(path: String, fromUi: Boolean = false) {
        if (_filePath.value != path) {
            _filePath.value = path
            Log.d(TAG, "Путь обновлён: $path (fromUi=$fromUi)")
        }
    }

    /** Установить чувствительность */
    fun setSensitivity(value: Int) {
        val clamped = value.coerceIn(5, 30)
        _sensitivity.value = clamped
        Log.d(TAG, "Чувствительность: $clamped м/с²")
    }

    /** Включить/выключить вибрацию */
    fun setVibrateEnabled(enabled: Boolean) {
        _vibrateEnabled.value = enabled
        Log.d(TAG, "Вибрация: $enabled")
    }

    /** Вызывается при успешном запуске сервиса */
    fun onServiceStarted() {
        _isServiceRunning.value = true
        Log.d(TAG, "Сервис запущен")
    }

    /** Вызывается при остановке сервиса (из UI или снаружи) */
    fun onServiceStopped() {
        _isServiceRunning.value = false
        // Сбрасываем громкость ProgressBar
        _playbackVolume.value = 0
        Log.d(TAG, "Сервис остановлен")
    }

    /**
     * Вызывается при получении события SHAKE_DETECTED от сервиса.
     */
    fun onShakeDetected(timestamp: Long) {
        _lastShakeTime.value = timestamp
        _shakeCount.value = (_shakeCount.value ?: 0) + 1
        // Имитируем "вспышку" на ProgressBar при тряске
        _playbackVolume.value = 100
        Log.d(TAG, "Тряска зафиксирована! Счётчик: ${_shakeCount.value}")

        // Через 1 секунду сбрасываем ProgressBar обратно в 0
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            _playbackVolume.value = 0
        }, 1000L)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel очищена")
    }
}
