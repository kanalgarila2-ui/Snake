package com.tryaskafon.shake.osint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentWifiScannerBinding

/**
 * WifiScannerFragment — сканирует доступные Wi-Fi сети и показывает:
 * SSID, уровень сигнала (dBm → %), тип безопасности.
 */
class WifiScannerFragment : Fragment() {

    private var _b: FragmentWifiScannerBinding? = null
    private val b get() = _b!!
    private lateinit var wifiManager: WifiManager

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            showResults(success)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWifiScannerBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wifiManager = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        b.btnScanWifi.setOnClickListener { startScan() }
        b.tvWifiStatus.text = if (wifiManager.isWifiEnabled) "Wi-Fi: включён" else "Wi-Fi: выключен"
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    private fun startScan() {
        if (!wifiManager.isWifiEnabled) {
            b.tvWifiResults.text = "⚠️ Wi-Fi выключен. Включи в настройках."
            return
        }
        b.tvWifiResults.text = "🔍 Сканирую..."
        b.btnScanWifi.isEnabled = false
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    }

    private fun showResults(success: Boolean) {
        b.btnScanWifi.isEnabled = true
        val results = try {
            @Suppress("DEPRECATION") wifiManager.scanResults
        } catch (e: SecurityException) {
            b.tvWifiResults.text = "⚠️ Нет разрешения ACCESS_FINE_LOCATION"
            return
        }

        if (results.isNullOrEmpty()) {
            b.tvWifiResults.text = "Сетей не найдено"
            return
        }

        val sorted = results.sortedByDescending { it.level }
        val sb = StringBuilder()
        sb.appendLine("📡 Найдено сетей: ${sorted.size}\n")

        sorted.forEach { r ->
            val pct   = WifiManager.calculateSignalLevel(r.level, 100)
            val bars  = when { pct >= 75 -> "████" ; pct >= 50 -> "███░" ; pct >= 25 -> "██░░" ; else -> "█░░░" }
            val lock  = if (r.capabilities.contains("WPA") || r.capabilities.contains("WEP")) "🔒" else "🔓"
            val ssid  = if (r.SSID.isNullOrBlank()) "(скрытая сеть)" else r.SSID
            sb.appendLine("$lock $ssid")
            sb.appendLine("   $bars $pct%  ${r.level} dBm  CH${r.frequency / 1000}GHz")
            sb.appendLine()
        }
        b.tvWifiResults.text = sb.toString()
        b.tvWifiStatus.text = "Сканирование: ${if (success) "успешно" else "из кэша"}"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
