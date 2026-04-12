package com.tryaskafon.shake.osint

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tryaskafon.shake.databinding.FragmentIpInfoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * IpInfoFragment — показывает публичную сетевую информацию об устройстве:
 * внешний IP, страну, провайдера, временную зону.
 * Использует публичный API ipinfo.io (без ключа, лимит 50k/мес).
 */
class IpInfoFragment : Fragment() {

    private var _b: FragmentIpInfoBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentIpInfoBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnCheckIp.setOnClickListener { fetchIpInfo() }
        b.tvIpHint.text = "Показывает только публичную информацию о подключении"
        fetchIpInfo()
    }

    private fun fetchIpInfo() {
        b.tvIpResult.text = "⏳ Определяю..."
        b.btnCheckIp.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    JSONObject(URL("https://ipinfo.io/json").readText())
                }
                val ip       = json.optString("ip", "?")
                val city     = json.optString("city", "?")
                val region   = json.optString("region", "?")
                val country  = json.optString("country", "?")
                val org      = json.optString("org", "?")
                val timezone = json.optString("timezone", "?")
                val loc      = json.optString("loc", "")

                val flag = countryFlag(country)

                b.tvIpResult.text = buildString {
                    appendLine("🌐 Внешний IP: $ip")
                    appendLine("$flag Страна: $country")
                    appendLine("🏙 Город: $city, $region")
                    appendLine("🏢 Провайдер: $org")
                    appendLine("🕐 Часовой пояс: $timezone")
                    if (loc.isNotEmpty()) appendLine("📍 Координаты: $loc")
                    appendLine()
                    appendLine("ℹ️ Это публичная информация о вашем интернет-соединении,")
                    appendLine("видная любому серверу в интернете.")
                }
            } catch (e: Exception) {
                b.tvIpResult.text = "❌ Ошибка: ${e.message}"
            } finally {
                b.btnCheckIp.isEnabled = true
            }
        }
    }

    /** Флаг из двухбуквенного кода страны (emoji regional indicators) */
    private fun countryFlag(code: String): String {
        if (code.length != 2) return "🌍"
        return code.uppercase().map { 0x1F1E0 + (it - 'A') }
            .joinToString("") { String(Character.toChars(it)) }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
