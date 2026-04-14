package com.tryaskafon.shake.games

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentDiceBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

class DiceFragment : Fragment() {

    private var _b: FragmentDiceBinding? = null
    private val b get() = _b!!

    private val diceFaces = arrayOf("⚀","⚁","⚂","⚃","⚄","⚅")
    private var totalRolls = 0
    private val history = mutableListOf<Int>()

    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDiceBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { rollDice() }
        b.tvDiceFace.text = diceFaces[0]
        b.tvInstruction.text = "Тряхни телефон или нажми на кубик!"
        b.tvDiceFace.setOnClickListener { rollDice() }
        b.btnRollDice.setOnClickListener { rollDice() }
        updateStats()
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun rollDice() {
        b.tvDiceFace.animate().scaleX(0f).scaleY(0f).rotationY(180f).setDuration(150)
            .withEndAction {
                val value = Random.nextInt(1, 7)
                b.tvDiceFace.text = diceFaces[value - 1]
                b.tvDiceNumber.text = value.toString()
                b.tvDiceFace.animate().scaleX(1f).scaleY(1f).rotationY(360f).setDuration(200).start()
                history.add(value); if (history.size > 20) history.removeAt(0)
                totalRolls++; updateStats()
            }.start()
    }

    private fun updateStats() {
        b.tvTotalRolls.text = "Бросков: $totalRolls"
        if (history.isEmpty()) return
        val avg  = history.average()
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
