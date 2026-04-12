package com.tryaskafon.shake.games

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
import com.tryaskafon.shake.databinding.FragmentRacerBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import kotlin.random.Random

/**
 * RacerFragment — ASCII гонки.
 * Тряска = ускорение. Машинка едет по дороге, объезжает препятствия.
 * Чем сильнее трясёшь — тем быстрее.
 */
class RacerFragment : Fragment() {

    private var _b: FragmentRacerBinding? = null
    private val b get() = _b!!

    // Игровое поле: 7 строк, 20 символов
    private val ROWS = 12
    private val COLS = 9
    private val road = Array(ROWS) { CharArray(COLS) { ' ' } }

    private var carCol = COLS / 2  // позиция машинки по горизонтали
    private var speed = 300L       // мс между тиками (меньше = быстрее)
    private var score = 0
    private var lives = 3
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    // Частота трясок для вычисления скорости
    private var shakeBoost = 0

    private val gameTick = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            // Скорость зависит от boost (трясок за последние 2 сек)
            val dynamicDelay = (speed - shakeBoost * 20L).coerceIn(80L, 600L)
            handler.postDelayed(this, dynamicDelay)
        }
    }

    private val boostDecay = object : Runnable {
        override fun run() {
            if (shakeBoost > 0) shakeBoost--
            handler.postDelayed(this, 500L)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED) {
                shakeBoost = (shakeBoost + 3).coerceAtMost(15)
                b.tvBoost.text = "🔥 Буст: $shakeBoost"
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRacerBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnStartRacer.setOnClickListener { if (running) stopGame() else startGame() }
        b.btnLeft.setOnClickListener  { carCol = (carCol - 1).coerceAtLeast(1) }
        b.btnRight.setOnClickListener { carCol = (carCol + 1).coerceAtMost(COLS - 2) }
        drawRoad()
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(ShakeDetectorService.ACTION_SHAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(receiver, f)
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(receiver) } catch (_: Exception) {}
        stopGame()
    }

    private fun startGame() {
        score = 0; lives = 3; carCol = COLS / 2; shakeBoost = 0; running = true
        b.btnStartRacer.text = "⏹ Стоп"
        initRoad()
        handler.post(gameTick)
        handler.post(boostDecay)
    }

    private fun stopGame() {
        running = false
        handler.removeCallbacks(gameTick)
        handler.removeCallbacks(boostDecay)
        b.btnStartRacer.text = "▶ Старт"
    }

    private fun initRoad() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                road[r][c] = if (c == 0 || c == COLS - 1) '|' else ' '
            }
        }
        // Машинка снизу
        road[ROWS - 1][carCol] = 'A'
    }

    private fun tick() {
        // Сдвигаем дорогу вниз
        for (r in ROWS - 1 downTo 1) road[r] = road[r - 1].copyOf()
        // Новая строка сверху
        road[0] = CharArray(COLS) { c -> if (c == 0 || c == COLS - 1) '|' else ' ' }

        // Случайное препятствие
        if (Random.nextInt(0, 5) == 0) {
            val obstCol = Random.nextInt(1, COLS - 1)
            road[0][obstCol] = '#'
        }

        // Убираем старую позицию машинки
        for (c in 1 until COLS - 1) if (road[ROWS - 1][c] == 'A') road[ROWS - 1][c] = ' '
        // Ставим машинку
        road[ROWS - 1][carCol] = 'A'

        // Проверяем столкновение
        if (road[ROWS - 2][carCol] == '#') {
            lives--
            road[ROWS - 2][carCol] = 'X'
            if (lives <= 0) { gameOver(); return }
        }

        score++
        drawRoad()
        b.tvRacerScore.text = "Очки: $score  ❤️: $lives"
    }

    private fun gameOver() {
        running = false
        handler.removeCallbacks(gameTick)
        b.tvRaceField.text = "💥 GAME OVER\nОчки: $score\n\nЛучший счёт — твой рекорд!"
        b.btnStartRacer.text = "▶ Заново"
    }

    private fun drawRoad() {
        b.tvRaceField.text = road.joinToString("\n") { it.concatToString() }
    }

    override fun onDestroyView() {
        stopGame()
        super.onDestroyView()
        _b = null
    }
}
