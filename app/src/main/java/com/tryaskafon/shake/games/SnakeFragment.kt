package com.tryaskafon.shake.games

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentSnakeBinding
import kotlin.random.Random

class SnakeFragment : Fragment() {
    private var _b: FragmentSnakeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSnakeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnSnakeStart.setOnClickListener {
            b.snakeView.startGame()
            b.btnSnakeStart.text = "Рестарт"
        }
        b.btnUp.setOnClickListener    { b.snakeView.setDirection(SnakeView.Dir.UP) }
        b.btnDown.setOnClickListener  { b.snakeView.setDirection(SnakeView.Dir.DOWN) }
        b.btnLeft.setOnClickListener  { b.snakeView.setDirection(SnakeView.Dir.LEFT) }
        b.btnRight.setOnClickListener { b.snakeView.setDirection(SnakeView.Dir.RIGHT) }

        b.snakeView.onScoreChanged = { score -> b.tvSnakeScore.text = "Очки: $score" }
        b.snakeView.onGameOver     = { score ->
            b.tvSnakeScore.text = "Game Over! Очки: $score"
            b.btnSnakeStart.text = "Заново"
        }
    }

    override fun onDestroyView() { b.snakeView.stopGame(); super.onDestroyView(); _b = null }
}

/** Кастомная View для змейки */
class SnakeView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    enum class Dir { UP, DOWN, LEFT, RIGHT }

    companion object {
        const val COLS = 20
        const val ROWS = 20
        const val TICK = 150L
    }

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private val snake = ArrayDeque<Pair<Int,Int>>()
    private var food = Pair(5, 5)
    private var dir = Dir.RIGHT
    private var nextDir = Dir.RIGHT
    private var score = 0
    private var running = false

    private val paintSnakeHead = Paint().apply { color = Color.parseColor("#FF6D00"); isAntiAlias = true }
    private val paintSnakeBody = Paint().apply { color = Color.parseColor("#FF9E40"); isAntiAlias = true }
    private val paintFood       = Paint().apply { color = Color.parseColor("#F44336"); isAntiAlias = true }
    private val paintGrid       = Paint().apply { color = Color.parseColor("#22000000"); strokeWidth = 0.5f }
    private val paintBg         = Paint().apply { color = Color.parseColor("#1A1A2E") }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            handler.postDelayed(this, TICK)
        }
    }

    fun startGame() {
        snake.clear()
        snake.addFirst(Pair(COLS/2, ROWS/2))
        snake.addFirst(Pair(COLS/2+1, ROWS/2))
        snake.addFirst(Pair(COLS/2+2, ROWS/2))
        dir = Dir.RIGHT; nextDir = Dir.RIGHT
        score = 0; running = true
        spawnFood()
        handler.post(tick)
    }

    fun stopGame() { running = false; handler.removeCallbacks(tick) }

    fun setDirection(d: Dir) {
        // Запрет разворота на 180°
        val invalid = (d == Dir.UP && dir == Dir.DOWN) ||
                      (d == Dir.DOWN && dir == Dir.UP) ||
                      (d == Dir.LEFT && dir == Dir.RIGHT) ||
                      (d == Dir.RIGHT && dir == Dir.LEFT)
        if (!invalid) nextDir = d
    }

    private fun step() {
        dir = nextDir
        val head = snake.first()
        val newHead = when (dir) {
            Dir.UP    -> Pair(head.first, head.second - 1)
            Dir.DOWN  -> Pair(head.first, head.second + 1)
            Dir.LEFT  -> Pair(head.first - 1, head.second)
            Dir.RIGHT -> Pair(head.first + 1, head.second)
        }
        // Проверяем стены
        if (newHead.first !in 0 until COLS || newHead.second !in 0 until ROWS || snake.contains(newHead)) {
            running = false
            post { onGameOver?.invoke(score) }
            return
        }
        snake.addFirst(newHead)
        if (newHead == food) {
            score++
            post { onScoreChanged?.invoke(score) }
            spawnFood()
        } else {
            snake.removeLast()
        }
        invalidate()
    }

    private fun spawnFood() {
        do { food = Pair(Random.nextInt(COLS), Random.nextInt(ROWS)) } while (snake.contains(food))
    }

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / COLS
        val ch = height.toFloat() / ROWS
        // Фон
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        // Сетка
        for (i in 0..COLS) canvas.drawLine(i * cw, 0f, i * cw, height.toFloat(), paintGrid)
        for (i in 0..ROWS) canvas.drawLine(0f, i * ch, width.toFloat(), i * ch, paintGrid)
        // Еда
        canvas.drawCircle((food.first + 0.5f) * cw, (food.second + 0.5f) * ch,
            minOf(cw, ch) * 0.4f, paintFood)
        // Змейка
        snake.forEachIndexed { idx, (x, y) ->
            val paint = if (idx == 0) paintSnakeHead else paintSnakeBody
            val margin = if (idx == 0) 2f else 3f
            canvas.drawRoundRect(
                x * cw + margin, y * ch + margin,
                (x + 1) * cw - margin, (y + 1) * ch - margin,
                8f, 8f, paint
            )
        }
    }
}
