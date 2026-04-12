package com.tryaskafon.shake.games

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentGuessBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import kotlin.random.Random

/**
 * GuessFragment — телефон загадывает число 1-100.
 * Каждая тряска — уменьшаешь интервал вдвое (бинарный поиск).
 * Угадал — тряска = победа.
 */
class GuessFragment : Fragment() {

    private var _b: FragmentGuessBinding? = null
    private val b get() = _b!!

    private var secret = 0
    private var low = 1
    private var high = 100
    private var shakeCount = 0
    private var gameOver = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED && !gameOver) onShake()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGuessBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnNewGame.setOnClickListener { newGame() }
        b.btnGuessLow.setOnClickListener  { guess(low) }
        b.btnGuessMid.setOnClickListener  { guess((low + high) / 2) }
        b.btnGuessHigh.setOnClickListener { guess(high) }
        newGame()
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
    }

    private fun newGame() {
        secret = Random.nextInt(1, 101)
        low = 1; high = 100; shakeCount = 0; gameOver = false
        updateUI()
        b.tvGuessResult.text = "Новая игра! Тряси для подсказки или жми кнопки."
    }

    // Тряска = автоматически угадываем середину
    private fun onShake() {
        if (gameOver) return
        shakeCount++
        guess((low + high) / 2)
    }

    private fun guess(n: Int) {
        if (gameOver) return
        when {
            n == secret -> {
                gameOver = true
                b.tvGuessResult.text = "🎉 Угадал! Число было $secret. Понадобилось $shakeCount трясок!"
                b.tvRange.text = "[$secret]"
            }
            n < secret -> {
                low = n + 1
                b.tvGuessResult.text = "📈 $n — слишком мало. Попробуй выше!"
                updateUI()
            }
            else -> {
                high = n - 1
                b.tvGuessResult.text = "📉 $n — слишком много. Попробуй ниже!"
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val mid = (low + high) / 2
        b.tvRange.text = "Диапазон: [$low … $high]"
        b.tvShakeCount.text = "Трясок: $shakeCount"
        b.btnGuessLow.text  = "$low"
        b.btnGuessMid.text  = "$mid (середина)"
        b.btnGuessHigh.text = "$high"
        b.progressGuess.max = 100
        b.progressGuess.progress = 100 - (high - low)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
