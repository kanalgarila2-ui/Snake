package com.tryaskafon.shake.crypto

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.FragmentTabsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import android.os.Handler
import android.os.Looper
import com.tryaskafon.shake.databinding.FragmentCryptoBinding
import com.tryaskafon.shake.databinding.FragmentCryptoTraderBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.math.abs
import kotlin.random.Random

// ── Хаб крипто-вкладок ──────────────────────────────────────────────────────
class CryptoFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!
    private val tabs = listOf(
        "📈 Курсы"      to { CryptoPriceFragment() as Fragment },
        "🎮 Трейдер"    to { CryptoTraderFragment() as Fragment },
        "⛏ Майнинг"    to { MiningSimFragment() as Fragment }
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

// ── Актуальные курсы + крипто-тряска ────────────────────────────────────────
class CryptoPriceFragment : Fragment() {
    private var _b: FragmentCryptoBinding? = null
    private val b get() = _b!!
    private var btcPrice = 0.0
    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCryptoBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Тряска меняет «курс биткоина» — понт как в ТЗ
        shakeHelper = ShakeDetectorHelper(requireContext()) {
            if (btcPrice > 0) {
                val delta = (Random.nextDouble(-500.0, 500.0))
                btcPrice += delta
                val sign = if (delta > 0) "📈 +" else "📉 "
                b.tvShakeEffect.text = "Тряска! $sign${"%.0f".format(abs(delta))}$ → BTC: ${"%.0f".format(btcPrice)}$"
            }
        }
        b.btnRefreshCrypto.setOnClickListener { fetchPrices() }
        b.tvCryptoHint.text = "🤝 Тряска влияет на курс BTC (локально, просто понт)"
        fetchPrices()
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun fetchPrices() {
        b.tvCryptoPrices.text = "⏳ Загружаю..."
        b.btnRefreshCrypto.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // CoinGecko API — бесплатно, без ключа
                val url = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids=bitcoin,ethereum,tether,binancecoin,solana" +
                    "&vs_currencies=usd,rub&include_24hr_change=true"
                val json = withContext(Dispatchers.IO) { JSONObject(URL(url).readText()) }

                val coins = listOf(
                    "bitcoin"     to "₿  Bitcoin",
                    "ethereum"    to "Ξ  Ethereum",
                    "tether"      to "₮  Tether",
                    "binancecoin" to "◈  BNB",
                    "solana"      to "◎  Solana"
                )
                val sb = StringBuilder()
                coins.forEach { (id, label) ->
                    try {
                        val obj    = json.getJSONObject(id)
                        val usd    = obj.getDouble("usd")
                        val rub    = obj.getDouble("rub")
                        val change = obj.optDouble("usd_24h_change", 0.0)
                        val arrow  = if (change >= 0) "▲" else "▼"
                        val color  = if (id == "bitcoin") { btcPrice = usd; "" } else ""
                        sb.appendLine("$label")
                        sb.appendLine("  $${"%.2f".format(usd)}  /  ${"%.0f".format(rub)}₽  $arrow ${"%.2f".format(change)}%")
                        sb.appendLine()
                    } catch (_: Exception) {}
                }
                b.tvCryptoPrices.text = sb.toString().trimEnd()
            } catch (e: Exception) {
                b.tvCryptoPrices.text = "❌ Ошибка: ${e.message}\nПроверь интернет"
            } finally {
                b.btnRefreshCrypto.isEnabled = true
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Симулятор трейдера ────────────────────────────────────────────────────────
class CryptoTraderFragment : Fragment() {
    private var _b: FragmentCryptoTraderBinding? = null
    private val b get() = _b!!

    private var balance = 10000.0   // стартовый баланс в USD
    private var btcHeld = 0.0
    private var ethHeld = 0.0
    private var currentBtc = 45000.0
    private var currentEth = 2500.0
    private val handler = Handler(Looper.getMainLooper())

    // Симуляция изменения цен каждые 3 сек
    private val priceTick = object : Runnable {
        override fun run() {
            currentBtc *= (1 + Random.nextDouble(-0.02, 0.02))
            currentEth *= (1 + Random.nextDouble(-0.025, 0.025))
            currentBtc = currentBtc.coerceIn(1000.0, 200000.0)
            currentEth = currentEth.coerceIn(100.0, 20000.0)
            updateUI()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCryptoTraderBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnBuyBtc.setOnClickListener  { trade("buy",  "btc") }
        b.btnSellBtc.setOnClickListener { trade("sell", "btc") }
        b.btnBuyEth.setOnClickListener  { trade("buy",  "eth") }
        b.btnSellEth.setOnClickListener { trade("sell", "eth") }
        b.btnResetTrader.setOnClickListener { reset() }
        updateUI()
        handler.post(priceTick)
    }

    private fun trade(action: String, coin: String) {
        val amount = b.etTradeAmount.text.toString().toDoubleOrNull() ?: 100.0
        when {
            action == "buy" && coin == "btc" -> {
                if (balance < amount) { b.tvTradeLog.text = "❌ Недостаточно USD"; return }
                balance -= amount; btcHeld += amount / currentBtc
                b.tvTradeLog.text = "✅ Купил BTC на ${"%.2f".format(amount)}$"
            }
            action == "sell" && coin == "btc" -> {
                val sellAmount = amount / currentBtc
                if (btcHeld < sellAmount) { b.tvTradeLog.text = "❌ Недостаточно BTC"; return }
                btcHeld -= sellAmount; balance += amount
                b.tvTradeLog.text = "✅ Продал BTC на ${"%.2f".format(amount)}$"
            }
            action == "buy" && coin == "eth" -> {
                if (balance < amount) { b.tvTradeLog.text = "❌ Недостаточно USD"; return }
                balance -= amount; ethHeld += amount / currentEth
                b.tvTradeLog.text = "✅ Купил ETH на ${"%.2f".format(amount)}$"
            }
            action == "sell" && coin == "eth" -> {
                val sellAmount = amount / currentEth
                if (ethHeld < sellAmount) { b.tvTradeLog.text = "❌ Недостаточно ETH"; return }
                ethHeld -= sellAmount; balance += amount
                b.tvTradeLog.text = "✅ Продал ETH на ${"%.2f".format(amount)}$"
            }
        }
        updateUI()
    }

    private fun reset() {
        balance = 10000.0; btcHeld = 0.0; ethHeld = 0.0
        b.tvTradeLog.text = "Портфель сброшен"
        updateUI()
    }

    private fun updateUI() {
        val portfolio = balance + btcHeld * currentBtc + ethHeld * currentEth
        val pnl = portfolio - 10000.0
        val pnlSign = if (pnl >= 0) "+" else ""
        b.tvTraderPrices.text = buildString {
            appendLine("₿  BTC: ${"%.0f".format(currentBtc)}$")
            appendLine("Ξ  ETH: ${"%.0f".format(currentEth)}$")
        }
        b.tvTraderPortfolio.text = buildString {
            appendLine("💵 USD: ${"%.2f".format(balance)}")
            appendLine("₿  BTC: ${"%.6f".format(btcHeld)}")
            appendLine("Ξ  ETH: ${"%.6f".format(ethHeld)}")
            appendLine("📊 Всего: ${"%.2f".format(portfolio)}$")
            appendLine("${if (pnl >= 0) "📈" else "📉"} PnL: $pnlSign${"%.2f".format(pnl)}$")
        }
    }

    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}

// ── Майнинг-симулятор ─────────────────────────────────────────────────────────
class MiningSimFragment : Fragment() {
    private var _b: com.tryaskafon.shake.databinding.FragmentMiningBinding? = null
    private val b get() = _b!!
    private var mining = false
    private var hashCount = 0L
    private var fakeBtc = 0.0
    private val handler = Handler(Looper.getMainLooper())
    private val hashTick = object : Runnable {
        override fun run() {
            if (!mining) return
            val hashes = Random.nextLong(1000, 5000)
            hashCount += hashes
            fakeBtc += hashes * 0.000000001
            val fakeHash = buildString {
                repeat(8) { append(('0'..'9').random()); append(('a'..'f').random()) }
            }
            b.tvMiningHash.text = "0x$fakeHash..."
            b.tvMiningStats.text = buildString {
                appendLine("⚡ Хэшрейт: ${hashes} H/s")
                appendLine("🔢 Всего хэшей: $hashCount")
                appendLine("₿  Добыто BTC: ${"%.9f".format(fakeBtc)}")
                appendLine("💵 В USD (~45k): ${"%.6f".format(fakeBtc * 45000)}")
            }
            b.progressMining.progress = (hashCount % 100).toInt()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = com.tryaskafon.shake.databinding.FragmentMiningBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnMineToggle.setOnClickListener {
            mining = !mining
            if (mining) { b.btnMineToggle.text = "⏹ Стоп"; handler.post(hashTick) }
            else { b.btnMineToggle.text = "⛏ Майнить!"; b.tvMiningHash.text = "—" }
        }
        b.tvMiningDisclaimer.text = "⚠️ Это симуляция. Реального BTC не добывается.\nПросто красивые числа."
    }
    override fun onDestroyView() { handler.removeCallbacksAndMessages(null); super.onDestroyView(); _b = null }
}
