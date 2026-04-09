package com.tryaskafon.shake.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * FileUtils — вспомогательные методы для работы с файлами и URI.
 */
object FileUtils {

    private const val TAG = "FileUtils"

    /**
     * Получить реальный путь к файлу из content:// URI.
     * Работает для MediaStore URI на Android 7-13.
     */
    fun getRealPath(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path

                "content" -> {
                    // Пробуем MediaStore
                    val projection = arrayOf(MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        if (cursor.moveToFirst()) {
                            val path = cursor.getString(columnIndex)
                            if (!path.isNullOrBlank()) return path
                        }
                    }
                    // Fallback — возвращаем URI как строку
                    uri.toString()
                }

                else -> uri.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения пути из URI: ${e.message}", e)
            null
        }
    }

    /**
     * Проверяет, что файл по пути существует и читаем.
     */
    fun isFileReadable(path: String): Boolean {
        return try {
            if (path.startsWith("content://") || path.startsWith("file://")) return true
            val file = File(path)
            file.exists() && file.canRead() && file.isFile
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки файла: ${e.message}", e)
            false
        }
    }

    /**
     * Создать директорию если не существует.
     * @return true если директория существует или была создана
     */
    fun ensureDirectoryExists(dir: File): Boolean {
        return try {
            if (dir.exists()) return true
            val result = dir.mkdirs()
            Log.d(TAG, "Создана директория ${dir.absolutePath}: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка создания директории: ${e.message}", e)
            false
        }
    }

    /**
     * Получить размер файла в читаемом формате (KB/MB).
     */
    fun getReadableFileSize(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) return "файл не найден"
            val bytes = file.length()
            when {
                bytes < 1024 -> "$bytes Б"
                bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
                else -> "${bytes / (1024 * 1024)} МБ"
            }
        } catch (e: Exception) {
            "неизвестно"
        }
    }
}
