package com.tryaskafon.shake.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * ConfigRepository v2.
 * savePathImmediately() вызывается только:
 *   - при debounce-таймере через 10 сек после последнего изменения
 *   - при onCleared() ViewModel (закрытие приложения)
 * Больше НЕ вызывается при каждом символе — телефон не тормозит.
 */
class ConfigRepository(private val context: Context) {

    private val TAG = "ConfigRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tryaskafon_prefs", Context.MODE_PRIVATE)

    private val saveDir by lazy {
        File(android.os.Environment.getExternalStorageDirectory(), "TryaskaFon").also {
            if (!it.exists()) it.mkdirs()
        }
    }
    private val pathFile by lazy { File(saveDir, "last_path.txt") }

    companion object {
        private const val KEY_FILE_PATH   = "file_path"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_VIBRATE     = "vibrate_enabled"
        private const val KEY_THEME       = "app_theme"
        private const val KEY_CHAT_KEY    = "chatgpt_api_key"
        private const val KEY_WEATHER_KEY = "weather_api_key"
    }

    /** Синхронное сохранение — вызывается редко (не каждую мс) */
    fun savePathImmediately(path: String) {
        prefs.edit().putString(KEY_FILE_PATH, path).commit()
        try {
            if (!saveDir.exists()) saveDir.mkdirs()
            pathFile.writeText(path)
            Log.d(TAG, "Путь сохранён: $path")
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка записи в файл: ${e.message}")
        }
    }

    fun loadFilePath(): String {
        return try {
            if (pathFile.exists()) {
                val t = pathFile.readText().trim()
                if (t.isNotEmpty()) return t
            }
            prefs.getString(KEY_FILE_PATH, "") ?: ""
        } catch (e: Exception) {
            prefs.getString(KEY_FILE_PATH, "") ?: ""
        }
    }

    fun saveSensitivity(v: Int) = prefs.edit().putInt(KEY_SENSITIVITY, v).apply()
    fun loadSensitivity() = prefs.getInt(KEY_SENSITIVITY, 15)

    fun saveVibrateEnabled(e: Boolean) = prefs.edit().putBoolean(KEY_VIBRATE, e).apply()
    fun loadVibrateEnabled() = prefs.getBoolean(KEY_VIBRATE, false)

    fun saveTheme(theme: String) = prefs.edit().putString(KEY_THEME, theme).apply()
    fun loadTheme() = prefs.getString(KEY_THEME, "light") ?: "light"

    fun saveChatGptKey(key: String) = prefs.edit().putString(KEY_CHAT_KEY, key).apply()
    fun loadChatGptKey() = prefs.getString(KEY_CHAT_KEY, "") ?: ""

    fun saveWeatherKey(key: String) = prefs.edit().putString(KEY_WEATHER_KEY, key).apply()
    fun loadWeatherKey() = prefs.getString(KEY_WEATHER_KEY, "") ?: ""
}
