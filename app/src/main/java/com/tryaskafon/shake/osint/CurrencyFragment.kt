package com.tryaskafon.shake.osint

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tryaskafon.shake.databinding.FragmentCurrencyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * CurrencyFragment — курс валют с сайта ЦБ РФ (JSON API, без ключа).
 */
class CurrencyFragment : Fragment() {

    private var _b: FragmentCurrencyBinding? = null
    private val b get() = _b!!

    // Интересующие валюты
    private val currencies = listOf("USD","EUR","GBP","CNY","JPY","CHF","BTC_unofficial")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCurrencyBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnRefreshCurrency.setOnClickListener { fetchRates() }
        fetchRates()
    }

    private fun fetchRates() {
        b.tvCurrencyResult.text = "⏳ Загружаю курсы..."
        b.btnRefreshCurrency.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ЦБ РФ JSON API — официальный, без ключа
                val url = "https://www.cbr-xml-daily.ru/daily_json.js"
                val json = withContext(Dispatchers.IO) { JSONObject(URL(url).readText()) }
                val valute = json.getJSONObject("Valute")
                val date = json.getString("Date").take(10)

                val sb = StringBuilder()
                sb.appendLine("📅 Дата: $date")
                sb.appendLine("Источник: ЦБ РФ\n")

                // Форматируем таблицу
                val displayNames = mapOf(
                    "USD" to "🇺🇸 Доллар",
                    "EUR" to "🇪🇺 Евро",
                    "GBP" to "🇬🇧 Фунт",
                    "CNY" to "🇨🇳 Юань",
                    "JPY" to "🇯🇵 Иена (100)",
                    "CHF" to "🇨🇭 Франк",
                    "BYR" to "🇧🇾 Бел.рубль",
                    "KZT" to "🇰🇿 Тенге (100)",
                    "TRY" to "🇹🇷 Лира"
                )

                displayNames.keys.forEach { code ->
                    try {
                        val obj    = valute.getJSONObject(code)
                        val value  = obj.getDouble("Value")
                        val prev   = obj.getDouble("Previous")
                        val nom    = obj.getInt("Nominal")
                        val diff   = value - prev
                        val arrow  = if (diff >= 0) "▲" else "▼"
                        val sign   = if (diff >= 0) "+" else ""
                        val name   = displayNames[code] ?: code
                        sb.appendLine("$name")
                        if (nom > 1)
                            sb.appendLine("  $nom шт → ${"%.2f".format(value)} ₽  $arrow $sign${"%.2f".format(diff)}")
                        else
                            sb.appendLine("  ${"%.4f".format(value)} ₽  $arrow $sign${"%.4f".format(diff)}")
                    } catch (_: Exception) {}
                }

                b.tvCurrencyResult.text = sb.toString()
            } catch (e: Exception) {
                b.tvCurrencyResult.text = "❌ Ошибка загрузки: ${e.message}\nПроверь интернет"
            } finally {
                b.btnRefreshCurrency.isEnabled = true
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
