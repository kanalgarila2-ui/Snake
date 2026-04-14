package com.tryaskafon.shake.system

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentTamagotchiBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper

class TamagotchiFragment : Fragment() {

    private var _b: FragmentTamagotchiBinding? = null
    private val b get() = _b!!

    private var hunger = 70; private var happiness = 70
    private var age = 0; private var weight = 50; private var isAlive = true
    private var feedCooldown = false

    private val handler = Handler(Looper.getMainLooper())
    private val lifeTick = object : Runnable {
        override fun run() {
            if (!isAlive) return
            hunger = (hunger - 5).coerceAtLeast(0); happiness = (happiness - 3).coerceAtLeast(0); age++
            if (hunger == 0 && happiness < 10) {
                isAlive = false; b.tvPet.text = "💀"
                b.tvPetStatus.text = "Питомец умер от голода... Нажми Возродить"
                b.btnRevive.visibility = View.VISIBLE; return
            }
            updateUI(); handler.postDelayed(this, 30_000L)
        }
    }

    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTamagotchiBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { if (isAlive) feed() }
        b.tvPet.setOnClickListener { if (isAlive) pet() }
        b.btnFeed.setOnClickListener   { if (isAlive) feed() }
        b.btnPlay.setOnClickListener   { if (isAlive) play() }
        b.btnRevive.setOnClickListener { revive() }
        b.tvShakeHint.text = "🤝 Тряска = покормить  |  Тап = погладить"
        updateUI(); handler.post(lifeTick)
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun feed() {
        if (feedCooldown) { flash("😅 Не так быстро!"); return }
        hunger = (hunger + 20).coerceAtMost(100); happiness = (happiness + 5).coerceAtMost(100); weight = (weight + 2).coerceAtMost(99)
        feedCooldown = true; handler.postDelayed({ feedCooldown = false }, 2000L); flash("😋 Вкусно!"); updateUI()
    }
    private fun pet()    { happiness = (happiness + 15).coerceAtMost(100); flash("🥰 Хорошо!"); updateUI() }
    private fun play()   { happiness = (happiness + 20).coerceAtMost(100); hunger = (hunger - 5).coerceAtLeast(0); weight = (weight - 1).coerceAtLeast(1); flash("🎉 Весело!"); updateUI() }
    private fun revive() { hunger = 70; happiness = 70; age = 0; weight = 50; isAlive = true; b.btnRevive.visibility = View.GONE; handler.post(lifeTick); updateUI() }
    private fun flash(msg: String) { b.tvPetAction.text = msg; handler.postDelayed({ b.tvPetAction.text = "" }, 1500L) }

    private fun updateUI() {
        b.tvPet.text = when { !isAlive -> "💀"; hunger < 20 -> "😰"; happiness < 20 -> "😢"; happiness > 80 && hunger > 80 -> "🐶"; else -> "🐕" }
        b.progressHunger.progress = hunger; b.progressHappiness.progress = happiness
        b.tvPetStats.text = "🍖 Голод: $hunger/100\n😊 Счастье: $happiness/100\n⚖️ Вес: $weight\n📅 Возраст: $age"
        b.tvPetStatus.text = when { hunger < 20 -> "😰 Очень голодный! Тряхни!"; happiness < 20 -> "😢 Грустит... Тапни!"; happiness > 80 -> "🎉 Счастлив!"; else -> "😊 Всё нормально" }
    }

    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}
