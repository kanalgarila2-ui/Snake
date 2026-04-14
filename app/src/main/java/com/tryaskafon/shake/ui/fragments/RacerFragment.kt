package com.tryaskafon.shake.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentRacerBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

class RacerFragment : Fragment() {

    private var _b: FragmentRacerBinding? = null
    private val b get() = _b!!

    private val ROWS = 12; private val COLS = 9
    private val road = Array(ROWS) { CharArray(COLS) { ' ' } }
    private var carCol = COLS / 2; private var speed = 300L
    private var score = 0; private var lives = 3; private var running = false
    private var shakeBoost = 0

    private val handler = Handler(Looper.getMainLooper())
    private val gameTick = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            handler.postDelayed(this, (speed - shakeBoost * 20L).coerceIn(80L, 600L))
        }
    }
    private val boostDecay = object : Runnable {
        override fun run() { if (shakeBoost > 0) shakeBoost--; handler.postDelayed(this, 500L) }
    }

    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRacerBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext(), cooldownMs = 200L) {
            shakeBoost = (shakeBoost + 3).coerceAtMost(15)
            b.tvBoost.text = "🔥 Буст: $shakeBoost"
        }
        b.btnStartRacer.setOnClickListener { if (running) stopGame() else startGame() }
        b.btnLeft.setOnClickListener  { carCol = (carCol - 1).coerceAtLeast(1) }
        b.btnRight.setOnClickListener { carCol = (carCol + 1).coerceAtMost(COLS - 2) }
        drawRoad()
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop(); stopGame() }

    private fun startGame() {
        score = 0; lives = 3; carCol = COLS / 2; shakeBoost = 0; running = true
        b.btnStartRacer.text = "⏹ Стоп"; initRoad()
        handler.post(gameTick); handler.post(boostDecay)
    }

    private fun stopGame() {
        running = false; handler.removeCallbacks(gameTick); handler.removeCallbacks(boostDecay)
        b.btnStartRacer.text = "▶ Старт"
    }

    private fun initRoad() {
        for (r in 0 until ROWS) for (c in 0 until COLS)
            road[r][c] = if (c == 0 || c == COLS - 1) '|' else ' '
        road[ROWS - 1][carCol] = 'A'
    }

    private fun tick() {
        for (r in ROWS - 1 downTo 1) road[r] = road[r - 1].copyOf()
        road[0] = CharArray(COLS) { c -> if (c == 0 || c == COLS - 1) '|' else ' ' }
        if (Random.nextInt(5) == 0) road[0][Random.nextInt(1, COLS - 1)] = '#'
        for (c in 1 until COLS - 1) if (road[ROWS - 1][c] == 'A') road[ROWS - 1][c] = ' '
        road[ROWS - 1][carCol] = 'A'
        if (road[ROWS - 2][carCol] == '#') {
            lives--; road[ROWS - 2][carCol] = 'X'
            if (lives <= 0) { gameOver(); return }
        }
        score++; drawRoad(); b.tvRacerScore.text = "Очки: $score  ❤️: $lives"
    }

    private fun gameOver() {
        running = false; handler.removeCallbacks(gameTick)
        b.tvRaceField.text = "💥 GAME OVER\nОчки: $score"; b.btnStartRacer.text = "▶ Заново"
    }

    private fun drawRoad() { b.tvRaceField.text = road.joinToString("\n") { it.concatToString() } }

    override fun onDestroyView() { stopGame(); super.onDestroyView(); _b = null }
}
