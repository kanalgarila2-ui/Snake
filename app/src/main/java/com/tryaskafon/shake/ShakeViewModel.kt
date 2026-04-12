package com.tryaskafon.shake

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tryaskafon.shake.repository.ConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ShakeViewModel v2 — расширен, переключён на AndroidViewModel для доступа к контексту.
 *
 * ИСПРАВЛЕНИЕ #1: ProgressBar горит ВСЁ время воспроизведения.
 * Теперь playbackVolume не сбрасывается через 1 сек — его сбрасывает AudioPlayer
 * через колбэк onPlaybackStopped() когда MediaPlayer.onCompletion срабатывает.
 *
 * ИСПРАВЛЕНИЕ #2: Сохранение пути — больше не каждую мс.
 * Используется debounce 10 секунд через корутину.
 */
class ShakeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ShakeViewModel"
    private val repo = ConfigRepository(application)

    // ── Поля состояния ────────────────────────────────────────────────────────

    private val _filePath = MutableLiveData(repo.loadFilePath())
    val filePath: LiveData<String> = _filePath

    private val _isServiceRunning = MutableLiveData(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    private val _shakeCount = MutableLiveData(0)
    val shakeCount: LiveData<Int> = _shakeCount

    private val _lastShakeTime = MutableLiveData(0L)
    val lastShakeTime: LiveData<Long> = _lastShakeTime

    private val _sensitivity = MutableLiveData(repo.loadSensitivity())
    val sensitivity: LiveData<Int> = _sensitivity

    private val _vibrateEnabled = MutableLiveData(repo.loadVibrateEnabled())
    val vibrateEnabled: LiveData<Boolean> = _vibrateEnabled

    // ── ИСПРАВЛЕНИЕ #1: ProgressBar ──────────────────────────────────────────
    // 0 = тихо, 1-100 = идёт воспроизведение
    // Сбрасывается ТОЛЬКО через onPlaybackStopped(), не по таймеру
    private val _playbackVolume = MutableLiveData(0)
    val playbackVolume: LiveData<Int> = _playbackVolume

    // Флаг: сейчас играет звук
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    // ── ИСПРАВЛЕНИЕ #2: Debounce сохранения пути ─────────────────────────────
    private var saveJob: Job? = null

    fun setFilePath(path: String, fromUi: Boolean = false) {
        if (_filePath.value == path) return
        _filePath.value = path
        if (fromUi) scheduleSave(path)
    }

    private fun scheduleSave(path: String) {
        // Отменяем предыдущий отложенный save, запускаем новый через 10 сек
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(10_000L)
            repo.savePathImmediately(path)
            Log.d(TAG, "Путь сохранён (debounce 10s): $path")
        }
    }

    /** Вызывается из AudioPlayer когда воспроизведение НАЧАЛОСЬ */
    fun onPlaybackStarted() {
        _isPlaying.postValue(true)
        _playbackVolume.postValue(100)
        Log.d(TAG, "Воспроизведение началось — ProgressBar ON")
    }

    /** Вызывается из AudioPlayer когда воспроизведение ЗАВЕРШИЛОСЬ */
    fun onPlaybackStopped() {
        _isPlaying.postValue(false)
        _playbackVolume.postValue(0)
        Log.d(TAG, "Воспроизведение закончилось — ProgressBar OFF")
    }

    fun setSensitivity(v: Int) {
        _sensitivity.value = v.coerceIn(5, 30)
        repo.saveSensitivity(v)
    }

    fun setVibrateEnabled(e: Boolean) {
        _vibrateEnabled.value = e
        repo.saveVibrateEnabled(e)
    }

    fun onServiceStarted() { _isServiceRunning.value = true }

    fun onServiceStopped() {
        _isServiceRunning.value = false
        _playbackVolume.value = 0
        _isPlaying.value = false
    }

    fun onShakeDetected(timestamp: Long) {
        _lastShakeTime.value = timestamp
        _shakeCount.value = (_shakeCount.value ?: 0) + 1
        Log.d(TAG, "Тряска! Счётчик: ${_shakeCount.value}")
    }

    override fun onCleared() {
        super.onCleared()
        // Сохраняем немедленно при уничтожении ViewModel (закрытие приложения)
        saveJob?.cancel()
        _filePath.value?.let { repo.savePathImmediately(it) }
    }
}
