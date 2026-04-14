package com.tryaskafon.shake.media

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentMusicPlayerBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import java.io.File

class MusicPlayerFragment : Fragment() {

    private var _b: FragmentMusicPlayerBinding? = null
    private val b get() = _b!!

    private var player: MediaPlayer? = null
    private val playlist = mutableListOf<String>()
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            try {
                player?.let { mp ->
                    if (mp.isPlaying) {
                        b.seekBarPlayer.max = mp.duration; b.seekBarPlayer.progress = mp.currentPosition
                        b.tvPlayerTime.text = "${formatTime(mp.currentPosition)} / ${formatTime(mp.duration)}"
                    }
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 500L)
        }
    }

    private lateinit var shakeHelper: ShakeDetectorHelper

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    val dst = File(requireContext().cacheDir, "track_${System.currentTimeMillis()}_$i.mp3")
                    requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(dst.outputStream()) }
                    playlist.add(dst.absolutePath)
                }
            } ?: result.data?.data?.let { uri ->
                val dst = File(requireContext().cacheDir, "track_${System.currentTimeMillis()}.mp3")
                requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(dst.outputStream()) }
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
        shakeHelper = ShakeDetectorHelper(requireContext()) { nextTrack() }

        b.btnAddTracks.setOnClickListener {
            filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            })
        }
        b.btnPlayPause.setOnClickListener { togglePlayPause() }
        b.btnPrev.setOnClickListener  { prevTrack() }
        b.btnNext.setOnClickListener  { nextTrack() }
        b.btnStop.setOnClickListener  { stopPlayer() }
        b.seekBarPlayer.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) try { player?.seekTo(p) } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        b.tvShakeHint.text = "🎵 Тряхни → следующий трек"
    }

    override fun onResume() { super.onResume(); shakeHelper.start(); handler.post(updateSeekBar) }
    override fun onPause()  { super.onPause();  shakeHelper.stop(); handler.removeCallbacks(updateSeekBar) }

    private fun playTrack(index: Int) {
        if (playlist.isEmpty()) return
        currentIndex = index.coerceIn(0, playlist.size - 1)
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(playlist[currentIndex]); prepare(); start()
                setOnCompletionListener { nextTrack() }
            }
            b.tvCurrentTrack.text = "▶ ${File(playlist[currentIndex]).name}"
            b.btnPlayPause.text = "⏸"; updatePlaylistUI()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        val mp = player ?: run { if (playlist.isNotEmpty()) playTrack(currentIndex); return }
        if (mp.isPlaying) { mp.pause(); b.btnPlayPause.text = "▶" }
        else { mp.start(); b.btnPlayPause.text = "⏸" }
    }
    private fun nextTrack() { if (playlist.isNotEmpty()) playTrack((currentIndex + 1) % playlist.size) }
    private fun prevTrack() { if (playlist.isNotEmpty()) playTrack((currentIndex - 1 + playlist.size) % playlist.size) }
    private fun stopPlayer() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null; b.btnPlayPause.text = "▶"; b.tvCurrentTrack.text = "— остановлено —"
        b.seekBarPlayer.progress = 0; b.tvPlayerTime.text = "0:00 / 0:00"
    }
    private fun updatePlaylistUI() {
        if (playlist.isEmpty()) { b.tvPlaylist.text = "Плейлист пуст"; return }
        b.tvPlaylist.text = playlist.mapIndexed { i, path ->
            "${if (i == currentIndex) "▶ " else "  "}${i+1}. ${File(path).name}"
        }.joinToString("\n")
    }
    private fun formatTime(ms: Int): String { val s = ms / 1000; return "${s / 60}:${"%02d".format(s % 60)}" }

    override fun onDestroyView() { stopPlayer(); handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}
