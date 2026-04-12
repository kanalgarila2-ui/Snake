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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tryaskafon.shake.databinding.FragmentOcrBinding

/**
 * OcrFragment — распознавание текста с фото через ML Kit.
 * Выбираешь фото (галерея или камера) → ML Kit читает текст → показываем.
 */
class OcrFragment : Fragment() {

    private var _b: FragmentOcrBinding? = null
    private val b get() = _b!!

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    bitmap?.let { runOcr(it) }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { runOcr(it) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentOcrBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnPickPhoto.setOnClickListener {
            galleryPicker.launch(Intent(Intent.ACTION_PICK).apply { type = "image/*" })
        }
        b.btnTakePhoto.setOnClickListener { cameraPicker.launch(null) }
        b.btnCopyOcr.setOnClickListener {
            val text = b.tvOcrResult.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OCR", text))
                Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
            }
        }
        b.tvOcrHint.text = "📷 Наведи камеру или выбери фото с текстом"
    }

    private fun runOcr(bitmap: Bitmap) {
        b.tvOcrResult.text = "🔍 Распознаю текст..."
        b.progressOcr.visibility = View.VISIBLE
        b.ivOcrPreview.setImageBitmap(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                b.progressOcr.visibility = View.GONE
                val text = result.text
                if (text.isBlank()) {
                    b.tvOcrResult.text = "Текст не найден на изображении"
                } else {
                    b.tvOcrResult.text = text
                    // Показываем статистику
                    val words  = text.split("\\s+".toRegex()).size
                    val blocks = result.textBlocks.size
                    b.tvOcrStats.text = "Блоков: $blocks  Слов: ~$words  Символов: ${text.length}"
                }
            }
            .addOnFailureListener { e ->
                b.progressOcr.visibility = View.GONE
                b.tvOcrResult.text = "❌ Ошибка ML Kit: ${e.message}"
            }
    }

    override fun onDestroyView() { recognizer.close(); super.onDestroyView(); _b = null }
}
