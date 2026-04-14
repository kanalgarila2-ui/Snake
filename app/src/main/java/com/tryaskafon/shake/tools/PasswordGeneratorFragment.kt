package com.tryaskafon.shake.tools

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentPasswordGenBinding
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

/**
 * PasswordGeneratorFragment — генерирует криптостойкие пароли.
 * Тряска = новый пароль.
 * Показывает оценку сложности по энтропии.
 */
class PasswordGeneratorFragment : Fragment() {

    private var _b: FragmentPasswordGenBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val lowercase = "abcdefghijklmnopqrstuvwxyz"
    private val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val digits    = "0123456789"
    private val symbols   = "!@#\$%^&*()-_=+[]{}|;:,.<>?"

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPasswordGenBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }

        b.btnGenPass.setOnClickListener { generate() }
        b.btnCopyPass.setOnClickListener {
            val pass = b.tvPassword.text.toString()
            if (pass.isNotEmpty() && pass != "Нажми Generate") {
                val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("pass", pass))
                Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
            }
        }
        b.tvPassHint.text = "🔐 Тряска или кнопка = новый пароль"
        generate()
    }

    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun generate() {
        val length = b.seekBarPassLen.progress.coerceAtLeast(8)
        b.tvPassLength.text = "$length символов"

        var charset = ""
        if (b.cbLower.isChecked)   charset += lowercase
        if (b.cbUpper.isChecked)   charset += uppercase
        if (b.cbDigits.isChecked)  charset += digits
        if (b.cbSymbols.isChecked) charset += symbols
        if (charset.isEmpty()) charset = lowercase + digits

        // Гарантируем хотя бы по одному символу каждого выбранного класса
        val required = buildList {
            if (b.cbLower.isChecked)   add(lowercase.random())
            if (b.cbUpper.isChecked)   add(uppercase.random())
            if (b.cbDigits.isChecked)  add(digits.random())
            if (b.cbSymbols.isChecked) add(symbols.random())
        }
        val remaining = (length - required.size).coerceAtLeast(0)
        val pass = (required + (1..remaining).map { charset.random() }).shuffled().joinToString("")
        b.tvPassword.text = pass

        // Оценка энтропии: E = L * log2(|charset|)
        val entropy = length * (Math.log(charset.length.toDouble()) / Math.log(2.0))
        val (strength, color, icon) = when {
            entropy < 28  -> Triple("Очень слабый 💀",  "#F44336", 10)
            entropy < 36  -> Triple("Слабый 😟",        "#FF5722", 25)
            entropy < 60  -> Triple("Средний 😐",       "#FFC107", 50)
            entropy < 80  -> Triple("Сильный 💪",       "#4CAF50", 75)
            else          -> Triple("Очень сильный 🔐", "#2196F3", 100)
        }
        b.tvPassStrength.text = "Сила: $strength  |  Энтропия: ${"%.0f".format(entropy)} бит"
        b.progressPassStrength.progress = icon
        b.progressPassStrength.progressTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
