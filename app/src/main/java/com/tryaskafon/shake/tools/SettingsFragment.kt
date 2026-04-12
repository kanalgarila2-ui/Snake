package com.tryaskafon.shake.tools

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentSettingsBinding
import com.tryaskafon.shake.repository.ConfigRepository

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private lateinit var repo: ConfigRepository

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = ConfigRepository(requireContext())

        // Тема
        when (repo.loadTheme()) {
            "dark"  -> b.rgTheme.check(b.rbDark.id)
            "amoled"-> b.rgTheme.check(b.rbAmoled.id)
            else    -> b.rgTheme.check(b.rbLight.id)
        }
        b.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                b.rbDark.id   -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); "dark" }
                b.rbAmoled.id -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); "amoled" }
                else          -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); "light" }
            }
            repo.saveTheme(theme)
        }

        // API ключи
        b.etSettingsChatKey.setText(repo.loadChatGptKey())
        b.etSettingsWeatherKey.setText(repo.loadWeatherKey())

        b.btnSaveKeys.setOnClickListener {
            repo.saveChatGptKey(b.etSettingsChatKey.text.toString().trim())
            repo.saveWeatherKey(b.etSettingsWeatherKey.text.toString().trim())
            Toast.makeText(requireContext(), "✅ Ключи сохранены", Toast.LENGTH_SHORT).show()
        }

        b.tvSettingsInfo.text = buildString {
            appendLine("📖 Версия: 2.0")
            appendLine("🔑 ChatGPT ключ: platform.openai.com/api-keys")
            appendLine("🌦 Weather ключ: openweathermap.org/api")
            appendLine()
            appendLine("Все данные хранятся только на устройстве.")
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
