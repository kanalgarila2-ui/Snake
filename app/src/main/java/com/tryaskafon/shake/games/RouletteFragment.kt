package com.tryaskafon.shake.games

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentRouletteBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import kotlin.random.Random

/**
 * RouletteFragment — европейская рулетка (0-36).
 * Ставки: красное/чёрное, чётное/нечётное, конкретное число.
 * Тряска = крутить колесо.
 */
class RouletteFragment : Fragment() {

    private var _b: FragmentRouletteBinding? = null
    private val b get() = _b!!

    // Европейская рулетка: 0 зелёный, 1-36 чередуются красный/чёрный
    private val redNumbers = setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36)
    private var balance = 1000
    private var bet = 0
    private var betType = "" // "red","black","even","odd","number"
    private var betNumber = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED) spin()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRouletteBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateBalance()

        // Ставки на цвет
        b.btnBetRed.setOnClickListener  { placeBet("red", "Красное", 100) }
        b.btnBetBlack.setOnClickListener { placeBet("black", "Чёрное", 100) }
        b.btnBetEven.setOnClickListener  { placeBet("even", "Чётное", 100) }
        b.btnBetOdd.setOnClickListener   { placeBet("odd", "Нечётное", 100) }

        // Ставка на число (вводит пользователь)
        b.btnBetNumber.setOnClickListener {
            val num = b.etBetNumber.text.toString().toIntOrNull()
            if (num == null || num !in 0..36) {
                Toast.makeText(requireContext(), "Число от 0 до 36", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            betNumber = num
            placeBet("number", "Число $num", 200)
        }

        b.btnSpin.setOnClickListener { spin() }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ShakeDetectorService.ACTION_SHAKE_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(receiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun placeBet(type: String, label: String, amount: Int) {
        if (balance < amount) {
            Toast.makeText(requireContext(), "Недостаточно средств!", Toast.LENGTH_SHORT).show()
            return
        }
        betType = type
        bet = amount
        balance -= amount
        updateBalance()
        b.tvCurrentBet.text = "Ставка: $label (−$amount 💰)"
        b.tvResult.text = "Тряхни или нажми Крутить!"
    }

    private fun spin() {
        if (betType.isEmpty()) {
            Toast.makeText(requireContext(), "Сначала сделай ставку!", Toast.LENGTH_SHORT).show()
            return
        }

        // Анимация вращения колеса
        b.tvWheel.animate().rotationBy(1440f).setDuration(1500).withEndAction {
            val result = Random.nextInt(0, 37)
            val color = when {
                result == 0 -> "🟢"
                result in redNumbers -> "🔴"
                else -> "⚫"
            }
            b.tvWheelResult.text = "$color $result"

            // Считаем выигрыш
            val win = when (betType) {
                "red"    -> if (result != 0 && result in redNumbers) bet * 2 else 0
                "black"  -> if (result != 0 && result !in redNumbers) bet * 2 else 0
                "even"   -> if (result != 0 && result % 2 == 0) bet * 2 else 0
                "odd"    -> if (result != 0 && result % 2 != 0) bet * 2 else 0
                "number" -> if (result == betNumber) bet * 36 else 0
                else     -> 0
            }

            balance += win
            updateBalance()

            b.tvResult.text = if (win > 0) "🎉 Выиграл +$win 💰!" else "😢 Проиграл"
            betType = ""
            bet = 0
            b.tvCurrentBet.text = "Ставки нет"

            // Если банкрот — сброс
            if (balance <= 0) {
                balance = 1000
                Toast.makeText(requireContext(), "Банкрот! Даём тебе 1000 💰", Toast.LENGTH_SHORT).show()
                updateBalance()
            }
        }.start()
    }

    private fun updateBalance() {
        b.tvBalance.text = "Баланс: $balance 💰"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
