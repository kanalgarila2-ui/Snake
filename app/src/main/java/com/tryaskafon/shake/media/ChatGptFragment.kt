package com.tryaskafon.shake.media

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tryaskafon.shake.databinding.FragmentChatGptBinding
import com.tryaskafon.shake.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ChatGptFragment — минималистичный ChatGPT клиент.
 * Пользователь вводит свой API ключ (openai.com/api-keys).
 * История чата хранится в памяти (не персистируется).
 */
class ChatGptFragment : Fragment() {

    private var _b: FragmentChatGptBinding? = null
    private val b get() = _b!!
    private lateinit var repo: ConfigRepository

    // История сообщений для multi-turn
    private val messages = mutableListOf<JSONObject>()
    private val chatDisplay = StringBuilder()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentChatGptBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = ConfigRepository(requireContext())

        // Восстанавливаем сохранённый ключ
        val savedKey = repo.loadChatGptKey()
        if (savedKey.isNotEmpty()) b.etApiKey.setText(savedKey)

        b.btnSaveKey.setOnClickListener {
            val key = b.etApiKey.text.toString().trim()
            repo.saveChatGptKey(key)
            b.tvChat.text = "✅ Ключ сохранён. Можно писать!"
        }

        b.btnSend.setOnClickListener { sendMessage() }

        b.btnClearChat.setOnClickListener {
            messages.clear()
            chatDisplay.clear()
            b.tvChat.text = "💬 Чат очищен"
        }

        b.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }

        // Системный промпт
        messages.add(JSONObject().apply {
            put("role", "system")
            put("content", "Ты полезный ассистент встроенный в приложение ТряскаФон. Отвечай кратко и по делу.")
        })

        b.tvChat.text = "👋 Введи API ключ и начни чат!\nКлюч: platform.openai.com/api-keys"
    }

    private fun sendMessage() {
        val key  = b.etApiKey.text.toString().trim()
        val text = b.etMessage.text.toString().trim()

        if (key.isEmpty())  { b.tvChat.text = "⚠️ Введи API ключ"; return }
        if (text.isEmpty()) return

        b.etMessage.setText("")
        b.btnSend.isEnabled = false

        // Добавляем сообщение пользователя
        messages.add(JSONObject().apply { put("role", "user"); put("content", text) })
        chatDisplay.appendLine("👤 Ты: $text\n")
        b.tvChat.text = chatDisplay.toString()
        // Прокручиваем вниз
        b.scrollChat.post { b.scrollChat.fullScroll(View.FOCUS_DOWN) }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { callOpenAI(key) }
                messages.add(JSONObject().apply { put("role", "assistant"); put("content", reply) })
                chatDisplay.appendLine("🤖 GPT: $reply\n")
                b.tvChat.text = chatDisplay.toString()
                b.scrollChat.post { b.scrollChat.fullScroll(View.FOCUS_DOWN) }
            } catch (e: Exception) {
                chatDisplay.appendLine("❌ Ошибка: ${e.message}\n")
                b.tvChat.text = chatDisplay.toString()
            } finally {
                b.btnSend.isEnabled = true
            }
        }
    }

    private fun callOpenAI(apiKey: String): String {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30_000
            readTimeout    = 60_000
        }

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray(messages))
            put("max_tokens", 500)
            put("temperature", 0.7)
        }.toString()

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val responseCode = conn.responseCode
        val stream = if (responseCode == 200) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().readText()

        if (responseCode != 200) {
            val errMsg = try { JSONObject(response).getJSONObject("error").getString("message") }
                         catch (_: Exception) { response.take(200) }
            throw Exception("API $responseCode: $errMsg")
        }

        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
