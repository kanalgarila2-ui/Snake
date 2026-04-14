package com.tryaskafon.shake.media

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tryaskafon.shake.databinding.FragmentOcrBinding

/**
 * OcrFragment v2 — распознавание текста (латиница + кириллица).
 * ML Kit Latin recognizer хорошо работает для обоих скриптов.
 * Результат показывается как есть — ML Kit сам определяет язык.
 */
class OcrFragment : Fragment() {

    private var _b: FragmentOcrBinding? = null
    private val b get() = _b!!

    // Latin recognizer — распознаёт и латиницу, и кириллицу
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val bitmap = requireContext().contentResolver
                        .openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    bitmap?.let { runOcr(it) }
                        ?: Toast.makeText(requireContext(), "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val cameraPicker = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) runOcr(bitmap)
        else Toast.makeText(requireContext(), "Фото не сделано", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentOcrBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnPickPhoto.setOnClickListener {
            galleryPicker.launch(
                Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            )
        }
        b.btnTakePhoto.setOnClickListener { cameraPicker.launch(null) }

        b.btnCopyOcr.setOnClickListener {
            val text = b.tvOcrResult.text.toString()
            if (text.isNotEmpty() && text != getString(com.tryaskafon.shake.R.string.ocr_placeholder)) {
                val cb = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("ocr", text))
                Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
            }
        }

        b.tvOcrHint.text = "📷 Выбери фото или сними — распознаю текст (рус/eng)"
        b.tvOcrResult.text = "Результат появится здесь"
    }

    private fun runOcr(bitmap: Bitmap) {
        b.tvOcrResult.text = "🔍 Распознаю..."
        b.progressOcr.visibility = View.VISIBLE
        b.ivOcrPreview.setImageBitmap(bitmap)
        b.tvOcrStats.text = ""

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                b.progressOcr.visibility = View.GONE
                if (visionText.text.isBlank()) {
                    b.tvOcrResult.text = "Текст не найден на изображении"
                    return@addOnSuccessListener
                }

                // Строим читаемый результат с разбивкой по блокам
                val sb = StringBuilder()
                visionText.textBlocks.forEach { block ->
                    block.lines.forEach { line ->
                        sb.appendLine(line.text)
                    }
                    sb.appendLine() // пустая строка между блоками
                }
                b.tvOcrResult.text = sb.toString().trimEnd()

                val words  = visionText.text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                val blocks = visionText.textBlocks.size
                b.tvOcrStats.text = "Блоков: $blocks  Слов: ~$words  Символов: ${visionText.text.length}"
            }
            .addOnFailureListener { e ->
                b.progressOcr.visibility = View.GONE
                b.tvOcrResult.text = "❌ Ошибка: ${e.message}"
            }
    }

    override fun onDestroyView() {
        recognizer.close()
        super.onDestroyView()
        _b = null
    }
}
