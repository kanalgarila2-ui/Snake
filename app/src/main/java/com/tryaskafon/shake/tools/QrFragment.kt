package com.tryaskafon.shake.tools

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator
import com.tryaskafon.shake.databinding.FragmentQrBinding

/**
 * QrFragment — генератор и сканер QR-кодов.
 * Генерация: текст → QR через ZXing MultiFormatWriter.
 * Сканирование: ZXing IntentIntegrator (открывает встроенный сканер).
 */
class QrFragment : Fragment() {

    private var _b: FragmentQrBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentQrBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnGenerateQr.setOnClickListener { generateQr() }
        b.btnScanQr.setOnClickListener     { scanQr() }
        b.btnCopyQr.setOnClickListener {
            val txt = b.tvQrResult.text.toString()
            if (txt.isNotEmpty() && txt != "Результат сканирования появится здесь") {
                val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("qr", txt))
                Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateQr() {
        val text = b.etQrText.text.toString().trim()
        if (text.isEmpty()) { Toast.makeText(requireContext(), "Введи текст", Toast.LENGTH_SHORT).show(); return }

        try {
            val size = 512
            val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            b.ivQrCode.setImageBitmap(bitmap)
            b.ivQrCode.visibility = View.VISIBLE
            b.tvQrResult.text = "QR для: $text"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка генерации: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanQr() {
        try {
            IntentIntegrator.forSupportFragment(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Наведи камеру на QR-код")
                setBeepEnabled(true)
                setCameraId(0)
                setOrientationLocked(false)
                initiateScan()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка сканера: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                b.tvQrResult.text = "Сканирование отменено"
            } else {
                b.tvQrResult.text = result.contents
                b.ivQrCode.visibility = View.GONE
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
