package com.tryaskafon.shake.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * AudioPlayer — обёртка над MediaPlayer для воспроизведения MP3 файла.
 *
 * Поддерживает:
 * - Реальные пути к файлам (/storage/emulated/0/...)
 * - content:// URI (из файлового менеджера)
 * - Fallback на встроенный звук из assets
 *
 * Потокобезопасность: play() вызывается из потока сенсора (через handler),
 * MediaPlayer операции — на главном потоке.
 */
class AudioPlayer(private val context: Context) {

    private val TAG = "AudioPlayer"

    // Текущий MediaPlayer — один, переиспользуем
    private var mediaPlayer: MediaPlayer? = null

    // Handler для операций на главном потоке
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Воспроизвести звук из [filePath].
     * @param filePath — путь к файлу или content:// URI строкой
     * @param onError — колбэк при ошибке (опционально)
     */
    fun play(filePath: String, onError: ((String) -> Unit)? = null) {
        // Переносим на главный поток — MediaPlayer требует Looper
        mainHandler.post {
            playOnMainThread(filePath, onError)
        }
    }

    private fun playOnMainThread(filePath: String, onError: ((String) -> Unit)?) {
        try {
            // Освобождаем предыдущий плеер если есть
            releaseInternal()

            val player = MediaPlayer()

            // Определяем источник звука
            when {
                filePath.startsWith("content://") -> {
                    // content:// URI
                    val uri = Uri.parse(filePath)
                    player.setDataSource(context, uri)
                    Log.d(TAG, "Источник: content URI = $filePath")
                }
                filePath.startsWith("file://") -> {
                    // file:// URI
                    val uri = Uri.parse(filePath)
                    player.setDataSource(context, uri)
                    Log.d(TAG, "Источник: file URI = $filePath")
                }
                filePath.isNotEmpty() && File(filePath).exists() -> {
                    // Реальный путь к файлу
                    player.setDataSource(filePath)
                    Log.d(TAG, "Источник: реальный путь = $filePath")
                }
                else -> {
                    // Fallback — встроенный звук из assets
                    Log.w(TAG, "Файл не найден ($filePath), используем встроенный звук")
                    playFromAssets(player, onError)
                    return
                }
            }

            player.setOnPreparedListener { mp ->
                Log.d(TAG, "MediaPlayer готов, запускаем воспроизведение")
                mp.start()
            }

            player.setOnCompletionListener { mp ->
                Log.d(TAG, "Воспроизведение завершено")
                mp.release()
                mediaPlayer = null
            }

            player.setOnErrorListener { mp, what, extra ->
                val msg = "MediaPlayer ошибка: what=$what, extra=$extra"
                Log.e(TAG, msg)
                onError?.invoke(msg)
                mp.release()
                mediaPlayer = null
                true // Обработали ошибку
            }

            mediaPlayer = player
            player.prepareAsync() // Асинхронная подготовка

        } catch (e: IllegalArgumentException) {
            val msg = "Неверный путь к файлу: ${e.message}"
            Log.e(TAG, msg, e)
            onError?.invoke(msg)
            playFallback(onError)
        } catch (e: SecurityException) {
            val msg = "Нет доступа к файлу: ${e.message}"
            Log.e(TAG, msg, e)
            onError?.invoke(msg)
        } catch (e: Exception) {
            val msg = "Ошибка AudioPlayer: ${e.message}"
            Log.e(TAG, msg, e)
            onError?.invoke(msg)
            playFallback(onError)
        }
    }

    /**
     * Воспроизведение встроенного звука из assets/default_sound.mp3.
     */
    private fun playFromAssets(player: MediaPlayer, onError: ((String) -> Unit)?) {
        try {
            val afd = context.assets.openFd("default_sound.mp3")
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            player.setOnPreparedListener { mp ->
                Log.d(TAG, "Assets MediaPlayer готов")
                mp.start()
            }
            player.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "Assets MediaPlayer ошибка: what=$what, extra=$extra")
                onError?.invoke("Assets ошибка: $what/$extra")
                mp.release()
                mediaPlayer = null
                true
            }

            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка воспроизведения из assets: ${e.message}", e)
            onError?.invoke("Fallback звук тоже недоступен: ${e.message}")
            player.release()
        }
    }

    /**
     * Пробуем запустить fallback (новый экземпляр MediaPlayer из assets).
     */
    private fun playFallback(onError: ((String) -> Unit)?) {
        try {
            val fallbackPlayer = MediaPlayer()
            playFromAssets(fallbackPlayer, onError)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback тоже не сработал: ${e.message}", e)
        }
    }

    /**
     * Освободить текущий MediaPlayer (внутренний, без переноса на поток).
     */
    private fun releaseInternal() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
                Log.d(TAG, "Предыдущий MediaPlayer освобождён")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка освобождения предыдущего MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Освободить ресурсы (вызывается при уничтожении сервиса).
     */
    fun release() {
        mainHandler.post {
            releaseInternal()
            Log.d(TAG, "AudioPlayer полностью освобождён")
        }
    }
}
