package com.tryaskafon.shake.osint

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tryaskafon.shake.databinding.FragmentWeatherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * WeatherFragment — погода по городу через OpenWeatherMap API.
 * Пользователь вводит свой API-ключ (бесплатный на openweathermap.org).
 */
class WeatherFragment : Fragment() {

    private var _b: FragmentWeatherBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWeatherBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnGetWeather.setOnClickListener { fetchWeather() }
        b.tvWeatherHint.text = "Бесплатный ключ: openweathermap.org/api"
    }

    private fun fetchWeather() {
        val city   = b.etCity.text.toString().trim()
        val apiKey = b.etWeatherKey.text.toString().trim()
        if (city.isEmpty())   { b.tvWeatherResult.text = "Введи город"; return }
        if (apiKey.isEmpty()) { b.tvWeatherResult.text = "Введи API ключ"; return }

        b.tvWeatherResult.text = "⏳ Загружаю..."
        b.btnGetWeather.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val url = "https://api.openweathermap.org/data/2.5/weather" +
                    "?q=${java.net.URLEncoder.encode(city, "UTF-8")}" +
                    "&appid=$apiKey&units=metric&lang=ru"
                val json = withContext(Dispatchers.IO) {
                    JSONObject(URL(url).readText())
                }
                val name    = json.getString("name")
                val country = json.getJSONObject("sys").getString("country")
                val temp    = json.getJSONObject("main").getDouble("temp")
                val feels   = json.getJSONObject("main").getDouble("feels_like")
                val hum     = json.getJSONObject("main").getInt("humidity")
                val desc    = json.getJSONArray("weather").getJSONObject(0).getString("description")
                val wind    = json.getJSONObject("wind").getDouble("speed")
                val icon = weatherIcon(json.getJSONArray("weather").getJSONObject(0).getString("icon"))

                b.tvWeatherResult.text = buildString {
                    appendLine("$icon  $name, $country")
                    appendLine("🌡 ${"%.1f".format(temp)}°C  (ощущается ${"%.1f".format(feels)}°C)")
                    appendLine("📝 $desc")
                    appendLine("💧 Влажность: $hum%")
                    appendLine("💨 Ветер: ${"%.1f".format(wind)} м/с")
                }
            } catch (e: Exception) {
                b.tvWeatherResult.text = "❌ Ошибка: ${e.message}\nПроверь ключ и название города"
            } finally {
                b.btnGetWeather.isEnabled = true
            }
        }
    }

    private fun weatherIcon(code: String) = when {
        code.startsWith("01") -> "☀️"
        code.startsWith("02") -> "⛅"
        code.startsWith("03") || code.startsWith("04") -> "☁️"
        code.startsWith("09") || code.startsWith("10") -> "🌧"
        code.startsWith("11") -> "⛈"
        code.startsWith("13") -> "❄️"
        code.startsWith("50") -> "🌫"
        else -> "🌡"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
