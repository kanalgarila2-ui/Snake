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
import com.tryaskafon.shake.databinding.FragmentTamagotchiBinding
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * TamagotchiFragment — виртуальная собачка.
 * Тряска = кормить (поднимает голод).
 * Тап = гладить (поднимает счастье).
 * Каждые 30 сек: голод и счастье снижаются.
 * Если голод = 0 → питомец грустит, если > 0 и счастье > 50 → радуется.
 */
class TamagotchiFragment : Fragment() {

    private var _b: FragmentTamagotchiBinding? = null
    private val b get() = _b!!

    private var hunger   = 70   // 0-100: 100 = сытый
    private var happiness= 70   // 0-100: 100 = счастливый
    private var age      = 0    // в условных "минутах"
    private var weight   = 50
    private var isAlive  = true
    private var feedCooldown = false

    private val handler = Handler(Looper.getMainLooper())
    private val lifeTick = object : Runnable {
        override fun run() {
            if (!isAlive) return
            // Каждые 30 сек теряем 5 голода и 3 счастья
            hunger    = (hunger    - 5).coerceAtLeast(0)
            happiness = (happiness - 3).coerceAtLeast(0)
            age++
            if (hunger == 0 && happiness < 10) {
                isAlive = false
                b.tvPet.text = "💀"
                b.tvPetStatus.text = "Питомец умер от голода... Нажми Возродить"
                b.btnRevive.visibility = View.VISIBLE
                return
            }
            updateUI()
            handler.postDelayed(this, 30_000L)
        }
    }

    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ShakeDetectorService.ACTION_SHAKE_DETECTED && isAlive) feed()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTamagotchiBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Тап = погладить
        b.tvPet.setOnClickListener { if (isAlive) pet() }
        b.btnFeed.setOnClickListener { if (isAlive) feed() }
        b.btnPlay.setOnClickListener { if (isAlive) play() }
        b.btnRevive.setOnClickListener { revive() }

        b.tvShakeHint.text = "🤝 Тряска = покормить  |  Тап = погладить"
        updateUI()
        handler.post(lifeTick)
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
    }

    private fun feed() {
        if (feedCooldown) { flash("😅 Не так быстро!"); return }
        hunger    = (hunger    + 20).coerceAtMost(100)
        happiness = (happiness +  5).coerceAtMost(100)
        weight    = (weight    +  2).coerceAtMost(99)
        feedCooldown = true
        handler.postDelayed({ feedCooldown = false }, 2000L)
        flash("😋 Вкусно!")
        updateUI()
    }

    private fun pet() {
        happiness = (happiness + 15).coerceAtMost(100)
        flash("🥰 Хорошо!")
        updateUI()
    }

    private fun play() {
        happiness = (happiness + 20).coerceAtMost(100)
        hunger    = (hunger    -  5).coerceAtLeast(0)
        weight    = (weight    -  1).coerceAtLeast(1)
        flash("🎉 Весело!")
        updateUI()
    }

    private fun revive() {
        hunger = 70; happiness = 70; age = 0; weight = 50; isAlive = true
        b.btnRevive.visibility = View.GONE
        handler.post(lifeTick)
        updateUI()
    }

    private fun flash(msg: String) {
        b.tvPetAction.text = msg
        handler.postDelayed({ b.tvPetAction.text = "" }, 1500L)
    }

    private fun updateUI() {
        // Эмодзи питомца зависит от состояния
        b.tvPet.text = when {
            !isAlive          -> "💀"
            hunger < 20       -> "😰"
            happiness < 20    -> "😢"
            happiness > 80 && hunger > 80 -> "🐶"
            hunger > 60       -> "😊"
            else              -> "🐕"
        }
        b.progressHunger.progress   = hunger
        b.progressHappiness.progress= happiness
        b.tvPetStats.text = buildString {
            appendLine("🍖 Голод: $hunger/100")
            appendLine("😊 Счастье: $happiness/100")
            appendLine("⚖️ Вес: $weight")
            appendLine("📅 Возраст: $age")
        }
        b.tvPetStatus.text = when {
            hunger < 20    -> "😰 Очень голодный! Тряхни чтобы покормить!"
            happiness < 20 -> "😢 Грустит... Потрогай экран!"
            happiness > 80 -> "🎉 Счастлив!"
            else           -> "😊 Всё нормально"
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
