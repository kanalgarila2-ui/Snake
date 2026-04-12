package com.tryaskafon.shake.osint

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentMatrixBinding
import kotlin.random.Random

/**
 * MatrixFragment — анимация падающих символов в стиле "Матрицы".
 * Чистый Easter Egg — красиво и не более того.
 */
class MatrixFragment : Fragment() {

    private var _b: FragmentMatrixBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMatrixBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnMatrixToggle.setOnClickListener {
            if (b.matrixView.running) {
                b.matrixView.stop()
                b.btnMatrixToggle.text = "▶ Запустить"
            } else {
                b.matrixView.start()
                b.btnMatrixToggle.text = "⏹ Стоп"
            }
        }
        b.matrixView.start()
    }

    override fun onPause()     { b.matrixView.stop(); super.onPause() }
    override fun onResume()    { b.matrixView.start(); super.onResume() }
    override fun onDestroyView() { b.matrixView.stop(); super.onDestroyView(); _b = null }
}

class MatrixView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var running = false

    private val charSet = "ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEF"
    private val fontSize = 28f
    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSize
        isAntiAlias = true
    }
    private val paintHead = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSize
        color = Color.WHITE
        isAntiAlias = true
    }

    private var columns = 0
    private var drops = IntArray(0)
    private var speeds = FloatArray(0)    // индивидуальная скорость каждой колонки
    private var alphas = FloatArray(0)    // яркость каждой колонки

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            handler.postDelayed(this, 40L) // ~25 fps
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        columns = (w / fontSize).toInt().coerceAtLeast(1)
        drops   = IntArray(columns) { Random.nextInt(-20, 0) }
        speeds  = FloatArray(columns) { Random.nextFloat() * 0.8f + 0.4f }
        alphas  = FloatArray(columns) { Random.nextFloat() * 0.5f + 0.5f }
    }

    fun start() {
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    override fun onDraw(canvas: Canvas) {
        // Полупрозрачный чёрный фон для эффекта "хвоста"
        val bgPaint = Paint().apply { color = Color.argb(35, 0, 0, 0) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val rows = (height / fontSize).toInt()

        for (col in 0 until columns) {
            val x = col * fontSize

            // Рисуем колонку символов
            for (row in 0..3) {
                val y = (drops[col] - row) * fontSize
                if (y < 0 || y > height) continue
                val char = charSet[Random.nextInt(charSet.length)].toString()

                if (row == 0) {
                    // Голова колонки — белая
                    canvas.drawText(char, x, y, paintHead)
                } else {
                    // Хвост — зелёный с затуханием
                    val green = (255 * alphas[col] * (1f - row / 4f)).toInt().coerceIn(50, 255)
                    paint.color = Color.argb(green, 0, green, 0)
                    canvas.drawText(char, x, y, paint)
                }
            }

            // Продвигаем капли
            drops[col] = (drops[col] + speeds[col]).toInt()

            // Сброс когда ушла за экран
            if (drops[col] * fontSize > height + fontSize * 4) {
                if (Random.nextFloat() > 0.85f) {
                    drops[col] = Random.nextInt(-15, 0)
                    speeds[col] = Random.nextFloat() * 0.8f + 0.4f
                    alphas[col] = Random.nextFloat() * 0.5f + 0.5f
                }
            }
        }
    }
}
