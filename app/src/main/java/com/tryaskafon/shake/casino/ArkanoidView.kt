package com.tryaskafon.shake.casino

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ArkanoidView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var onScoreChanged: ((Int) -> Unit)? = null
    var onGameOver:     (() -> Unit)?    = null
    var onWin:          (() -> Unit)?    = null

    // Конфигурация
    private val COLS    = 8
    private val ROWS    = 5
    private val PAD_W   get() = width * 0.25f
    private val PAD_H   = 18f
    private val BALL_R  = 12f

    // Состояние игры
    private val bricks  = Array(ROWS) { BooleanArray(COLS) { true } }
    private var score   = 0
    private var running = false

    // Мяч
    private var bx = 0f; private var by = 0f
    private var dx = 6f; private var dy = -6f

    // Платформа
    private var padX = 0f
    private var shakeDir = 1  // чередуем лево/право

    private val handler = Handler(Looper.getMainLooper())
    private val gameTick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            invalidate()
            handler.postDelayed(this, 16L) // ~60fps
        }
    }

    // Краски
    private val paintBall  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6D00") }
    private val paintPad   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9E40") }
    private val paintBrick = Paint(Paint.ANTI_ALIAS_FLAG).apply { isAntiAlias = true }
    private val paintBg    = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val paintText  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 48f; typeface = Typeface.DEFAULT_BOLD }

    private val brickColors = listOf("#F44336","#E91E63","#9C27B0","#3F51B5","#2196F3","#00BCD4","#4CAF50","#FF9800")

    fun startGame() {
        for (r in 0 until ROWS) bricks[r].fill(true)
        score = 0
        bx = width / 2f; by = height * 0.6f
        dx = 6f; dy = -6f
        padX = (width - PAD_W) / 2f
        running = true
        handler.removeCallbacks(gameTick)
        handler.post(gameTick)
        onScoreChanged?.invoke(0)
    }

    /** Тряска двигает платформу влево/вправо чередуясь */
    fun shakeInput() {
        val move = PAD_W * 0.7f * shakeDir
        padX = (padX + move).coerceIn(0f, (width - PAD_W))
        shakeDir = -shakeDir
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!running) return true
        // Тач тоже двигает платформу
        padX = (e.x - PAD_W / 2).coerceIn(0f, (width - PAD_W))
        return true
    }

    private fun step() {
        bx += dx; by += dy
        // Стены
        if (bx - BALL_R < 0)       { bx = BALL_R;        dx = abs(dx) }
        if (bx + BALL_R > width)   { bx = width - BALL_R; dx = -abs(dx) }
        if (by - BALL_R < 0)       { by = BALL_R;        dy = abs(dy) }

        // Платформа
        val padY = height - 60f
        if (by + BALL_R >= padY && by + BALL_R <= padY + PAD_H &&
            bx >= padX && bx <= padX + PAD_W) {
            dy = -abs(dy)
            // Угол отражения зависит от точки попадания
            val relHit = (bx - padX) / PAD_W - 0.5f // -0.5..0.5
            dx = relHit * 12f
        }

        // Дно — game over
        if (by + BALL_R > height) {
            running = false; handler.removeCallbacks(gameTick)
            post { onGameOver?.invoke() }
            return
        }

        // Кирпичи
        val brickW = width.toFloat() / COLS
        val brickH = height * 0.4f / ROWS
        val offsetY = 40f
        outer@ for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (!bricks[r][c]) continue
                val left = c * brickW + 2; val top = offsetY + r * brickH + 2
                val right = left + brickW - 4; val bottom = top + brickH - 4
                if (bx + BALL_R > left && bx - BALL_R < right &&
                    by + BALL_R > top  && by - BALL_R < bottom) {
                    bricks[r][c] = false; score += 10
                    post { onScoreChanged?.invoke(score) }
                    // Отражение
                    val fromLeft  = abs(bx - left);  val fromRight = abs(bx - right)
                    val fromTop   = abs(by - top);   val fromBottom = abs(by - bottom)
                    val minDist = minOf(fromLeft, fromRight, fromTop, fromBottom)
                    if (minDist == fromTop || minDist == fromBottom) dy = -dy else dx = -dx
                    break@outer
                }
            }
        }
        // Победа
        if (bricks.all { row -> row.none { it } }) {
            running = false; handler.removeCallbacks(gameTick)
            post { onWin?.invoke() }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        if (width == 0) return

        val brickW = width.toFloat() / COLS
        val brickH = height * 0.4f / ROWS
        val offsetY = 40f

        // Кирпичи
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (!bricks[r][c]) continue
                paintBrick.color = Color.parseColor(brickColors[c % brickColors.size])
                canvas.drawRoundRect(
                    c * brickW + 3, offsetY + r * brickH + 3,
                    (c + 1) * brickW - 3, offsetY + (r + 1) * brickH - 3,
                    6f, 6f, paintBrick
                )
            }
        }

        // Платформа
        val padY = height - 60f
        canvas.drawRoundRect(padX, padY, padX + PAD_W, padY + PAD_H, PAD_H/2, PAD_H/2, paintPad)

        // Мяч
        canvas.drawCircle(bx, by, BALL_R, paintBall)

        // Не начата
        if (!running && bricks.any { row -> row.any { it } }) {
            canvas.drawText("Нажми Старт", width / 2f, height / 2f, paintText)
        }
    }

    override fun onDetachedFromWindow() {
        running = false; handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
