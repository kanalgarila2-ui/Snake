package com.tryaskafon.shake.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * AudioPlayer v2 — исправление ProgressBar.
 *
 * Теперь принимает два колбэка:
 *   onStarted() — вызывается когда MediaPlayer.start() срабатывает (ProgressBar = 100)
 *   onStopped() — вызывается из onCompletion/onError (ProgressBar = 0)
 *
 * Так ProgressBar горит РОВНО столько, сколько играет звук.
 */
class AudioPlayer(private val context: Context) {

    private val TAG = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Колбэки для ViewModel
    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackStopped: (() -> Unit)? = null

    fun play(filePath: String, onError: ((String) -> Unit)? = null) {
        mainHandler.post { playOnMain(filePath, onError) }
    }

    private fun playOnMain(filePath: String, onError: ((String) -> Unit)?) {
        try {
            releaseInternal()
            val player = MediaPlayer()

            when {
                filePath.startsWith("content://") -> player.setDataSource(context, Uri.parse(filePath))
                filePath.startsWith("file://")    -> player.setDataSource(context, Uri.parse(filePath))
                filePath.isNotEmpty() && File(filePath).exists() -> player.setDataSource(filePath)
                else -> {
                    playFromAssets(MediaPlayer(), onError)
                    return
                }
            }

            player.setOnPreparedListener { mp ->
                Log.d(TAG, "Готов, запускаем")
                mp.start()
                onPlaybackStarted?.invoke()   // ← ProgressBar ВКЛ
            }

            player.setOnCompletionListener { mp ->
                Log.d(TAG, "Завершено")
                mp.release()
                mediaPlayer = null
                onPlaybackStopped?.invoke()   // ← ProgressBar ВЫКЛ
            }

            player.setOnErrorListener { mp, what, extra ->
                val msg = "MediaPlayer ошибка: what=$what extra=$extra"
                Log.e(TAG, msg)
                onError?.invoke(msg)
                mp.release()
                mediaPlayer = null
                onPlaybackStopped?.invoke()   // ← ProgressBar ВЫКЛ даже при ошибке
                true
            }

            mediaPlayer = player
            player.prepareAsync()

        } catch (e: Exception) {
            Log.e(TAG, "play() exception: ${e.message}", e)
            onError?.invoke(e.message ?: "unknown")
            onPlaybackStopped?.invoke()
        }
    }

    private fun playFromAssets(player: MediaPlayer, onError: ((String) -> Unit)?) {
        try {
            context.assets.openFd("default_sound.mp3").use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            player.setOnPreparedListener { mp ->
                mp.start()
                onPlaybackStarted?.invoke()
            }
            player.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
                onPlaybackStopped?.invoke()
            }
            player.setOnErrorListener { mp, what, extra ->
                onError?.invoke("assets error $what/$extra")
                mp.release()
                mediaPlayer = null
                onPlaybackStopped?.invoke()
                true
            }
            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "assets fallback failed: ${e.message}")
            onError?.invoke(e.message ?: "assets error")
            player.release()
            onPlaybackStopped?.invoke()
        }
    }

    private fun releaseInternal() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        mainHandler.post {
            releaseInternal()
            onPlaybackStopped?.invoke()
        }
    }
}
