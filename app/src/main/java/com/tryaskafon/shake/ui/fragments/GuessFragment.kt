package com.tryaskafon.shake.games

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentGuessBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

class GuessFragment : Fragment() {

    private var _b: FragmentGuessBinding? = null
    private val b get() = _b!!

    private var secret = 0; private var low = 1; private var high = 100
    private var shakeCount = 0; private var gameOver = false

    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGuessBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { if (!gameOver) onShake() }
        b.btnNewGame.setOnClickListener { newGame() }
        b.btnGuessLow.setOnClickListener  { guess(low) }
        b.btnGuessMid.setOnClickListener  { guess((low + high) / 2) }
        b.btnGuessHigh.setOnClickListener { guess(high) }
        newGame()
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun newGame() {
        secret = Random.nextInt(1, 101); low = 1; high = 100; shakeCount = 0; gameOver = false
        updateUI(); b.tvGuessResult.text = "Новая игра! Тряси для подсказки или жми кнопки."
    }

    private fun onShake() { shakeCount++; guess((low + high) / 2) }

    private fun guess(n: Int) {
        if (gameOver) return
        when {
            n == secret -> { gameOver = true; b.tvGuessResult.text = "🎉 Угадал! Было $secret. Трясок: $shakeCount"; b.tvRange.text = "[$secret]" }
            n < secret  -> { low = n + 1;  b.tvGuessResult.text = "📈 $n — мало!";  updateUI() }
            else        -> { high = n - 1; b.tvGuessResult.text = "📉 $n — много!"; updateUI() }
        }
    }

    private fun updateUI() {
        val mid = (low + high) / 2
        b.tvRange.text = "Диапазон: [$low … $high]"
        b.tvShakeCount.text = "Трясок: $shakeCount"
        b.btnGuessLow.text  = "$low"
        b.btnGuessMid.text  = "$mid (середина)"
        b.btnGuessHigh.text = "$high"
        b.progressGuess.max = 100; b.progressGuess.progress = 100 - (high - low)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
