package com.tryaskafon.shake.games

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.R
import com.tryaskafon.shake.databinding.FragmentDiceBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import kotlin.random.Random

/**
 * DiceFragment — тряхни телефон → кубик падает и показывает число 1-6.
 * "3D" эффект — через scale + rotate анимацию на ImageView с эмодзи-кубиками.
 */
class DiceFragment : Fragment() {

    private var _b: FragmentDiceBinding? = null
    private val b get() = _b!!

    // Грани кубика: эмодзи 1-6
    private val diceFaces = arrayOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
    private var currentValue = 1
    private var totalRolls = 0
    private val history = mutableListOf<Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED) rollDice()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDiceBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.tvDiceFace.text = diceFaces[0]
        b.tvInstruction.text = "Тряхни телефон или нажми на кубик!"
        b.tvDiceFace.setOnClickListener { rollDice() }
        b.btnRollDice.setOnClickListener { rollDice() }
        updateStats()
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

    private fun rollDice() {
        // 3D spin анимация
        b.tvDiceFace.animate()
            .scaleX(0f).scaleY(0f).rotationY(180f).setDuration(150)
            .withEndAction {
                currentValue = Random.nextInt(1, 7)
                b.tvDiceFace.text = diceFaces[currentValue - 1]
                b.tvDiceNumber.text = currentValue.toString()
                b.tvDiceFace.animate()
                    .scaleX(1f).scaleY(1f).rotationY(360f).setDuration(200).start()
                history.add(currentValue)
                if (history.size > 20) history.removeAt(0)
                totalRolls++
                updateStats()
            }.start()
    }

    private fun updateStats() {
        b.tvTotalRolls.text = "Бросков: $totalRolls"
        if (history.isEmpty()) return
        val avg = history.average()
        val freq = history.groupingBy { it }.eachCount()
        val most = freq.maxByOrNull { it.value }
        b.tvDiceStats.text = buildString {
            append("Среднее: ${"%.1f".format(avg)}\n")
            append("Последние: ${history.takeLast(5).joinToString(" ")}\n")
            most?.let { append("Чаще всего: ${diceFaces[it.key - 1]} (${it.value} раз)") }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
