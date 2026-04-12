package com.tryaskafon.shake.tools

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentSosBinding
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * SosFragment — экстренная функция.
 * При активации + тряске 3 раза подряд → отправляет SMS с координатами.
 * Требует явного включения и ввода номера телефона.
 */
class SosFragment : Fragment() {

    private var _b: FragmentSosBinding? = null
    private val b get() = _b!!

    private var sosEnabled = false
    private var shakeCounter = 0
    private var lastShakeReset = 0L
    private val SHAKES_NEEDED = 3
    private val RESET_WINDOW  = 5000L // 5 сек — окно для 3 трясок

    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ShakeDetectorService.ACTION_SHAKE_DETECTED || !sosEnabled) return
            val now = System.currentTimeMillis()
            if (now - lastShakeReset > RESET_WINDOW) {
                shakeCounter = 0
                lastShakeReset = now
            }
            shakeCounter++
            b.tvSosCounter.text = "Трясок: $shakeCounter / $SHAKES_NEEDED"
            if (shakeCounter >= SHAKES_NEEDED) {
                shakeCounter = 0
                sendSos()
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSosBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.switchSos.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val phone = b.etSosPhone.text.toString().trim()
                if (phone.isEmpty()) {
                    Toast.makeText(requireContext(), "Введи номер телефона", Toast.LENGTH_SHORT).show()
                    b.switchSos.isChecked = false; return@setOnCheckedChangeListener
                }
                sosEnabled = true
                b.tvSosStatus.text = "🔴 SOS активен. Тряхни $SHAKES_NEEDED раза быстро!"
                b.tvSosStatus.setTextColor(android.graphics.Color.RED)
            } else {
                sosEnabled = false; shakeCounter = 0
                b.tvSosStatus.text = "⚫ SOS выключен"
                b.tvSosStatus.setTextColor(android.graphics.Color.GRAY)
                b.tvSosCounter.text = ""
            }
        }

        b.btnTestSos.setOnClickListener {
            Toast.makeText(requireContext(), "Тест: SMS было бы отправлено на ${b.etSosPhone.text}", Toast.LENGTH_LONG).show()
        }

        b.tvSosHint.text = buildString {
            appendLine("⚠️ Как работает SOS:")
            appendLine("1. Введи номер получателя (экстренный контакт)")
            appendLine("2. Включи переключатель")
            appendLine("3. При опасности: тряхни телефон $SHAKES_NEEDED раза за 5 сек")
            appendLine("4. Автоматически отправится SMS с координатами GPS")
            appendLine()
            appendLine("SMS текст: \"ЭКСТРЕННО! Мне нужна помощь! Координаты: [lat, lon]\"")
        }
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

    private fun sendSos() {
        val phone = b.etSosPhone.text.toString().trim()
        if (phone.isEmpty()) { Toast.makeText(requireContext(), "Нет номера телефона", Toast.LENGTH_SHORT).show(); return }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Нет разрешения на отправку SMS", Toast.LENGTH_LONG).show()
            return
        }

        // Получаем последние известные координаты
        var locationStr = "координаты недоступны"
        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    locationStr = "${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}"
                }
            }
        } catch (_: Exception) {}

        val message = "🆘 ЭКСТРЕННО! Мне нужна помощь!\nКоординаты: $locationStr\nhttps://maps.google.com/?q=$locationStr"

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                requireContext().getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

            smsManager.sendTextMessage(phone, null, message, null, null)
            b.tvSosStatus.text = "✅ SOS отправлен на $phone!"
            Toast.makeText(requireContext(), "SOS SMS отправлен!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
