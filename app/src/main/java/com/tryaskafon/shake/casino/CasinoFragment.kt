package com.tryaskafon.shake.casino

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.*
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

// ── Хаб казино ───────────────────────────────────────────────────────────────
class CasinoFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!
    private val tabs = listOf(
        "🎰 Слоты"   to { SlotsFragment() as Fragment },
        "🎹 Пианино" to { PianoShakeFragment() as Fragment },
        "🧱 Арканоид"to { ArkanoidFragment() as Fragment }
    )
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTabsBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(pos: Int) = tabs[pos].second()
        }
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos -> tab.text = tabs[pos].first }.attach()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Слоты ─────────────────────────────────────────────────────────────────────
class SlotsFragment : Fragment() {
    private var _b: FragmentSlotsBinding? = null
    private val b get() = _b!!
    private var balance = 500
    private var spinning = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val symbols = listOf("🍒","🍋","🍊","🍇","💎","7️⃣","⭐","🎰")
    private val payouts = mapOf(
        "🍒" to 2, "🍋" to 3, "🍊" to 4, "🍇" to 5,
        "⭐" to 8, "💎" to 15, "7️⃣" to 25, "🎰" to 50
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSlotsBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { if (!spinning) spin() }
        b.btnSpin.setOnClickListener { if (!spinning) spin() }
        b.tvSlotHint.text = "🎰 Тряска или кнопка = крутить. 3 одинаковых = выигрыш!"
        updateBalanceUI()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun spin() {
        val bet = b.etBet.text.toString().toIntOrNull() ?: 10
        if (balance < bet) { Toast.makeText(requireContext(), "Недостаточно фишек!", Toast.LENGTH_SHORT).show(); return }
        balance -= bet; spinning = true; b.btnSpin.isEnabled = false
        updateBalanceUI()

        // Анимация крутящихся барабанов
        var ticks = 0
        val animRunnable = object : Runnable {
            override fun run() {
                b.tvSlot1.text = symbols.random()
                b.tvSlot2.text = symbols.random()
                b.tvSlot3.text = symbols.random()
                ticks++
                if (ticks < 20) handler.postDelayed(this, 80L)
                else stopSpin(bet)
            }
        }
        handler.post(animRunnable)
    }

    private fun stopSpin(bet: Int) {
        val s1 = symbols.random(); val s2 = symbols.random(); val s3 = symbols.random()
        b.tvSlot1.text = s1; b.tvSlot2.text = s2; b.tvSlot3.text = s3
        spinning = false; b.btnSpin.isEnabled = true

        val win = when {
            s1 == s2 && s2 == s3 -> bet * (payouts[s1] ?: 2)
            s1 == s2 || s2 == s3 || s1 == s3 -> bet
            else -> 0
        }
        balance += win
        b.tvSlotResult.text = when {
            win > bet * 5 -> "🎉 ДЖЕКПОТ! +$win фишек!"
            win > 0       -> "✅ Выиграл +$win фишек!"
            else          -> "😢 Не повезло"
        }
        if (balance <= 0) { balance = 500; b.tvSlotResult.text = "💸 Банкрот! Даём 500 фишек" }
        updateBalanceUI()
    }

    private fun updateBalanceUI() { b.tvSlotBalance.text = "Фишки: $balance 🪙" }
    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}

// ── Тряси-пианино ─────────────────────────────────────────────────────────────
class PianoShakeFragment : Fragment() {
    private var _b: FragmentPianoBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper
    private val melody = mutableListOf<Int>()
    private var toneGen: ToneGenerator? = null
    private val notes = listOf(
        "До" to ToneGenerator.TONE_DTMF_1,
        "Ре" to ToneGenerator.TONE_DTMF_2,
        "Ми" to ToneGenerator.TONE_DTMF_3,
        "Фа" to ToneGenerator.TONE_DTMF_4,
        "Соль" to ToneGenerator.TONE_DTMF_5,
        "Ля" to ToneGenerator.TONE_DTMF_6,
        "Си" to ToneGenerator.TONE_DTMF_7,
        "До²" to ToneGenerator.TONE_DTMF_8
    )
    private var noteIndex = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPianoBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        shakeHelper = ShakeDetectorHelper(requireContext(), cooldownMs = 400L) { playNextNote() }
        b.btnPianoClear.setOnClickListener { melody.clear(); noteIndex = 0; b.tvMelody.text = "—"; b.tvCurrentNote.text = "—" }
        b.btnPianoPlay.setOnClickListener  { playRecordedMelody() }
        b.tvPianoHint.text = "🎹 Каждая тряска = следующая нота. Записывай мелодию!"
        b.tvPianoScale.text = notes.joinToString("  ") { it.first }
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun playNextNote() {
        val note = notes[noteIndex % notes.size]
        noteIndex++
        melody.add(note.second)
        b.tvCurrentNote.text = "🎵 ${note.first}"
        b.tvMelody.text = melody.mapIndexed { i, _ -> notes[(i) % notes.size].first }.joinToString(" → ")
        try { toneGen?.startTone(note.second, 300) } catch (_: Exception) {}
    }

    private fun playRecordedMelody() {
        if (melody.isEmpty()) return
        val handler = Handler(Looper.getMainLooper())
        melody.forEachIndexed { i, tone ->
            handler.postDelayed({
                try { toneGen?.startTone(tone, 250) } catch (_: Exception) {}
                b.tvCurrentNote.text = "▶ ${notes[i % notes.size].first}"
            }, i * 400L)
        }
    }

    override fun onDestroyView() {
        toneGen?.release(); toneGen = null
        super.onDestroyView(); _b = null
    }
}

// ── Арканоид ─────────────────────────────────────────────────────────────────
class ArkanoidFragment : Fragment() {
    private var _b: FragmentArkanoidBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentArkanoidBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext(), cooldownMs = 100L) {
            b.arkanoidView.shakeInput()
        }
        b.btnArkanoidStart.setOnClickListener {
            b.arkanoidView.startGame()
            b.btnArkanoidStart.text = "Рестарт"
        }
        b.arkanoidView.onScoreChanged = { score -> b.tvArkanoidScore.text = "Очки: $score" }
        b.arkanoidView.onGameOver     = { b.tvArkanoidScore.text = "💀 Game Over! Нажми Рестарт" }
        b.arkanoidView.onWin          = { b.tvArkanoidScore.text = "🎉 Победа! Все кирпичи разбиты!" }
        b.tvArkanoidHint.text = "🎮 Тряска = платформа влево/вправо (чередование)"
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
