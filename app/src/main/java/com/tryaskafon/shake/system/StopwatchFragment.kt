package com.tryaskafon.shake.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentStopwatchBinding
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * StopwatchFragment — секундомер с фиксацией кругов.
 * Тряска = новый круг.
 */
class StopwatchFragment : Fragment() {

    private var _b: FragmentStopwatchBinding? = null
    private val b get() = _b!!

    private var running   = false
    private var startTime = 0L
    private var elapsed   = 0L
    private var lastLap   = 0L
    private val laps      = mutableListOf<Long>()
    private val handler   = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            elapsed = now - startTime
            b.tvStopwatch.text = formatMs(elapsed)
            b.tvLapCurrent.text = "Текущий: ${formatMs(elapsed - lastLap)}"
            handler.postDelayed(this, 16L) // ~60fps
        }
    }

    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED && running) addLap()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStopwatchBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnSwStart.setOnClickListener {
            if (running) { pause() } else { resume() }
        }
        b.btnSwLap.setOnClickListener { if (running) addLap() }
        b.btnSwReset.setOnClickListener { reset() }
        b.tvShakeHint.text = "🔔 Тряска = новый круг"
        updateDisplay()
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(ShakeDetectorService.ACTION_SHAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(shakeReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(shakeReceiver, f)
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(shakeReceiver) } catch (_: Exception) {}
        if (running) pause()
    }

    private fun resume() {
        startTime = System.currentTimeMillis() - elapsed
        running = true
        b.btnSwStart.text = "⏸"
        handler.post(tick)
    }

    private fun pause() {
        running = false
        handler.removeCallbacks(tick)
        b.btnSwStart.text = "▶"
    }

    private fun reset() {
        pause()
        elapsed = 0L; lastLap = 0L; laps.clear()
        updateDisplay()
    }

    private fun addLap() {
        val lapTime = elapsed - lastLap
        laps.add(lapTime)
        lastLap = elapsed
        updateLaps()
    }

    private fun updateDisplay() {
        b.tvStopwatch.text = formatMs(elapsed)
        b.tvLapCurrent.text = "Текущий: ${formatMs(elapsed - lastLap)}"
        updateLaps()
    }

    private fun updateLaps() {
        if (laps.isEmpty()) { b.tvLaps.text = "Кругов нет"; return }
        val best  = laps.min()
        val worst = laps.max()
        b.tvLaps.text = laps.mapIndexed { i, ms ->
            val mark = when (ms) { best -> "🥇"; worst -> "🐢"; else -> "  " }
            "$mark Круг ${i+1}: ${formatMs(ms)}"
        }.reversed().joinToString("\n")
    }

    private fun formatMs(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000; val cs = (ms % 1000) / 10
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs)
        else "%02d:%02d.%02d".format(m, s, cs)
    }

    override fun onDestroyView() { handler.removeCallbacks(tick); super.onDestroyView(); _b = null }
}
