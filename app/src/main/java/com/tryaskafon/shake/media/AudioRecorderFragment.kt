package com.tryaskafon.shake.media

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentAudioRecorderBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AudioRecorderFragment — диктофон.
 * Записывает в AAC (M4A), сохраняет в кэш.
 * Показывает список записей с возможностью воспроизвести или удалить.
 */
class AudioRecorderFragment : Fragment() {

    private var _b: FragmentAudioRecorderBinding? = null
    private val b get() = _b!!

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var isRecording = false
    private var recSeconds = 0

    private val handler = Handler(Looper.getMainLooper())
    private val timer = object : Runnable {
        override fun run() {
            recSeconds++
            val m = recSeconds / 60; val s = recSeconds % 60
            b.tvRecTime.text = "${"%02d".format(m)}:${"%02d".format(s)}"
            handler.postDelayed(this, 1000L)
        }
    }

    // Amplitude update для индикатора уровня
    private val ampUpdater = object : Runnable {
        override fun run() {
            if (!isRecording) return
            try {
                val amp = recorder?.maxAmplitude ?: 0
                b.progressRecLevel.progress = (amp / 327).coerceIn(0, 100)
            } catch (_: Exception) {}
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAudioRecorderBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnRecord.setOnClickListener { if (isRecording) stopRecording() else startRecording() }
        refreshFileList()
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Нет разрешения на микрофон", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sdf  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val name = "rec_${sdf.format(Date())}.m4a"
            currentFile = File(requireContext().cacheDir, name)

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(requireContext())
            else @Suppress("DEPRECATION") MediaRecorder()

            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true; recSeconds = 0
            b.btnRecord.text = "⏹ Стоп"
            b.tvRecStatus.text = "● REC"
            b.tvRecStatus.setTextColor(android.graphics.Color.RED)
            handler.post(timer)
            handler.post(ampUpdater)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка записи: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop(); recorder?.release()
        } catch (_: Exception) {}
        recorder = null; isRecording = false
        handler.removeCallbacks(timer)
        handler.removeCallbacks(ampUpdater)
        b.btnRecord.text = "⏺ Запись"
        b.tvRecStatus.text = "Готов"
        b.tvRecStatus.setTextColor(android.graphics.Color.GRAY)
        b.progressRecLevel.progress = 0
        Toast.makeText(requireContext(), "Сохранено: ${currentFile?.name}", Toast.LENGTH_SHORT).show()
        refreshFileList()
    }

    private fun refreshFileList() {
        val files = requireContext().cacheDir.listFiles { f -> f.name.endsWith(".m4a") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) { b.tvRecordings.text = "Нет записей"; return }

        b.tvRecordings.text = files.joinToString("\n") { f ->
            val kb = f.length() / 1024
            "🎙 ${f.name}  (${kb} КБ)"
        }

        // Тап по тексту воспроизводит последнюю запись
        b.tvRecordings.setOnClickListener {
            files.firstOrNull()?.let { playFile(it) }
        }
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare(); start()
                setOnCompletionListener { release(); player = null }
            }
            Toast.makeText(requireContext(), "▶ ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        if (isRecording) stopRecording()
        player?.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
