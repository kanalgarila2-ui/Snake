package com.tryaskafon.shake.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * ConfigRepository — хранит настройки приложения.
 *
 * ВАЖНО: savePathImmediately() пишет СИНХРОННО (commit + файл).
 * Это намеренно по ТЗ — сохранение при каждом символе в EditText.
 * Да, это блокирует UI поток. Да, мы это знаем. Таково ТЗ.
 */
class ConfigRepository(private val context: Context) {

    private val TAG = "ConfigRepository"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Директория для файла с путём
    private val saveDir: File by lazy {
        File(android.os.Environment.getExternalStorageDirectory(), "TryaskaFon").also { dir ->
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "Директория TryaskaFon создана: $created → ${dir.absolutePath}")
            }
        }
    }

    private val pathFile: File by lazy {
        File(saveDir, "last_path.txt")
    }

    companion object {
        private const val PREFS_NAME = "tryaskafon_prefs"
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_VIBRATE = "vibrate_enabled"
    }

    /**
     * Сохранить путь к файлу НЕМЕДЛЕННО и СИНХРОННО.
     * - SharedPreferences.commit() (блокирующий, не apply)
     * - Запись в файл через BufferedWriter с flush()
     *
     * Вызывается при каждом изменении текста в EditText (afterTextChanged).
     * По ТЗ это должно происходить "каждую 1 мс" — на практике столько раз,
     * сколько раз срабатывает TextWatcher.
     */
    fun savePathImmediately(path: String) {
        // 1. SharedPreferences.commit() — синхронная запись на диск
        val commitResult = prefs.edit()
            .putString(KEY_FILE_PATH, path)
            .commit() // commit, не apply!
        Log.v(TAG, "SharedPreferences.commit() → $commitResult, path=$path")

        // 2. Запись в файл /sdcard/TryaskaFon/last_path.txt
        try {
            // Создаём директорию если нет
            if (!saveDir.exists()) saveDir.mkdirs()

            // BufferedWriter с autoFlush через явный flush после write
            BufferedWriter(FileWriter(pathFile, false)).use { writer ->
                writer.write(path)
                writer.flush() // Принудительный сброс буфера
            }
            Log.v(TAG, "Файл записан: ${pathFile.absolutePath}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет доступа к хранилищу для записи файла: ${e.message}")
            // Не краш — просто логируем, SharedPreferences всё равно сохранены
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи в файл: ${e.message}", e)
        }
    }

    /**
     * Загрузить сохранённый путь к файлу.
     */
    fun loadFilePath(): String {
        // Сначала пробуем прочитать из файла (он актуальнее при внешнем редактировании)
        return try {
            if (pathFile.exists()) {
                val fromFile = pathFile.readText().trim()
                if (fromFile.isNotEmpty()) {
                    Log.d(TAG, "Путь загружен из файла: $fromFile")
                    return fromFile
                }
            }
            // Fallback — SharedPreferences
            val fromPrefs = prefs.getString(KEY_FILE_PATH, "") ?: ""
            Log.d(TAG, "Путь загружен из SharedPreferences: $fromPrefs")
            fromPrefs
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки пути: ${e.message}", e)
            prefs.getString(KEY_FILE_PATH, "") ?: ""
        }
    }

    /** Сохранить чувствительность (обычным apply — не критично) */
    fun saveSensitivity(value: Int) {
        prefs.edit().putInt(KEY_SENSITIVITY, value).apply()
    }

    /** Загрузить чувствительность */
    fun loadSensitivity(): Int = prefs.getInt(KEY_SENSITIVITY, 15)

    /** Сохранить флаг вибрации */
    fun saveVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    /** Загрузить флаг вибрации */
    fun loadVibrateEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATE, false)
}
