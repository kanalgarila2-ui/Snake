package com.tryaskafon.shake.media

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentMusicPlayerBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import java.io.File

/**
 * MusicPlayerFragment — встроенный MP3 плеер.
 * Функции:
 *  - Плейлист (несколько файлов)
 *  - Пауза / продолжить / след / пред
 *  - SeekBar прогресса
 *  - Тряска = следующий трек
 */
class MusicPlayerFragment : Fragment() {

    private var _b: FragmentMusicPlayerBinding? = null
    private val b get() = _b!!

    private var player: MediaPlayer? = null
    private val playlist = mutableListOf<String>()
    private var currentIndex = 0
    private var isPaused = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            try {
                player?.let { mp ->
                    if (mp.isPlaying) {
                        b.seekBarPlayer.max      = mp.duration
                        b.seekBarPlayer.progress = mp.currentPosition
                        val cur  = formatTime(mp.currentPosition)
                        val total = formatTime(mp.duration)
                        b.tvPlayerTime.text = "$cur / $total"
                    }
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 500L)
        }
    }

    // Тряска = следующий трек
    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED) nextTrack()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    // Копируем в кэш для стабильного доступа
                    val name = "track_${System.currentTimeMillis()}_$i.mp3"
                    val dst  = File(requireContext().cacheDir, name)
                    requireContext().contentResolver.openInputStream(uri)
                        ?.use { it.copyTo(dst.outputStream()) }
                    playlist.add(dst.absolutePath)
                }
            } ?: result.data?.data?.let { uri ->
                val name = "track_${System.currentTimeMillis()}.mp3"
                val dst  = File(requireContext().cacheDir, name)
                requireContext().contentResolver.openInputStream(uri)
                    ?.use { it.copyTo(dst.outputStream()) }
                playlist.add(dst.absolutePath)
            }
            updatePlaylistUI()
            if (playlist.size == 1) playTrack(0)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMusicPlayerBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnAddTracks.setOnClickListener {
            filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
        }
        b.btnPlayPause.setOnClickListener { togglePlayPause() }
        b.btnPrev.setOnClickListener { prevTrack() }
        b.btnNext.setOnClickListener { nextTrack() }
        b.btnStop.setOnClickListener { stopPlayer() }

        b.seekBarPlayer.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { try { player?.seekTo(p) } catch (_: Exception) {} }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        b.tvShakeHint.text = "🎵 Тряхни телефон → следующий трек"
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(ShakeDetectorService.ACTION_SHAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(shakeReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(shakeReceiver, f)
        handler.post(updateSeekBar)
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(shakeReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(updateSeekBar)
    }

    private fun playTrack(index: Int) {
        if (playlist.isEmpty()) return
        currentIndex = index.coerceIn(0, playlist.size - 1)
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(playlist[currentIndex])
                prepare()
                start()
                setOnCompletionListener { nextTrack() }
            }
            isPaused = false
            val name = File(playlist[currentIndex]).name
            b.tvCurrentTrack.text = "▶ $name"
            b.btnPlayPause.text = "⏸"
            updatePlaylistUI()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        val mp = player ?: run { if (playlist.isNotEmpty()) playTrack(currentIndex); return }
        if (mp.isPlaying) {
            mp.pause(); isPaused = true; b.btnPlayPause.text = "▶"
        } else {
            mp.start(); isPaused = false; b.btnPlayPause.text = "⏸"
        }
    }

    private fun nextTrack() {
        if (playlist.isEmpty()) return
        playTrack((currentIndex + 1) % playlist.size)
    }

    private fun prevTrack() {
        if (playlist.isEmpty()) return
        playTrack((currentIndex - 1 + playlist.size) % playlist.size)
    }

    private fun stopPlayer() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null; isPaused = false
        b.btnPlayPause.text = "▶"
        b.tvCurrentTrack.text = "— остановлено —"
        b.seekBarPlayer.progress = 0
        b.tvPlayerTime.text = "0:00 / 0:00"
    }

    private fun updatePlaylistUI() {
        if (playlist.isEmpty()) { b.tvPlaylist.text = "Плейлист пуст"; return }
        b.tvPlaylist.text = playlist.mapIndexed { i, path ->
            val marker = if (i == currentIndex) "▶ " else "  "
            "$marker${i + 1}. ${File(path).name}"
        }.joinToString("\n")
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000; return "${s / 60}:${"%02d".format(s % 60)}"
    }

    override fun onDestroyView() {
        stopPlayer()
        handler.removeCallbacks(updateSeekBar)
        super.onDestroyView()
        _b = null
    }
}
