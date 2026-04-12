package com.tryaskafon.shake.games

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.FragmentGame2048Binding
import kotlin.random.Random

class Game2048Fragment : Fragment() {
    private var _b: FragmentGame2048Binding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGame2048Binding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnNew2048.setOnClickListener { b.game2048View.newGame() }
        b.game2048View.onScoreChanged = { s -> b.tvScore2048.text = "Очки: $s" }
        b.game2048View.onWin  = { b.tvScore2048.text = "🏆 Победа! 2048!" }
        b.game2048View.onLose = { b.tvScore2048.text = "😢 Нет ходов! Попробуй снова." }
        b.game2048View.newGame()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

@SuppressLint("ClickableViewAccessibility")
class Game2048View @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var onScoreChanged: ((Int) -> Unit)? = null
    var onWin:  (() -> Unit)? = null
    var onLose: (() -> Unit)? = null

    private val grid = Array(4) { IntArray(4) }
    private var score = 0
    private var touchX = 0f; private var touchY = 0f

    private val tileColors = mapOf(
        0 to "#CDC1B4", 2 to "#EEE4DA", 4 to "#EDE0C8", 8 to "#F2B179",
        16 to "#F59563", 32 to "#F67C5F", 64 to "#F65E3B",
        128 to "#EDCF72", 256 to "#EDCC61", 512 to "#EDC850",
        1024 to "#EDC53F", 2048 to "#EDC22E"
    )

    private val paintBg   = Paint().apply { color = Color.parseColor("#BBADA0") }
    private val paintText = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    fun newGame() {
        for (r in 0..3) grid[r].fill(0)
        score = 0
        addRandom(); addRandom()
        invalidate()
        post { onScoreChanged?.invoke(0) }
    }

    private fun addRandom() {
        val empty = mutableListOf<Pair<Int,Int>>()
        for (r in 0..3) for (c in 0..3) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return
        val (r, c) = empty.random()
        grid[r][c] = if (Random.nextFloat() < 0.9f) 2 else 4
    }

    override fun setOnTouchListener(l: OnTouchListener?) { /* не используем */ }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { touchX = e.x; touchY = e.y }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - touchX; val dy = e.y - touchY
                val moved = when {
                    Math.abs(dx) > Math.abs(dy) && dx > 50  -> swipeRight()
                    Math.abs(dx) > Math.abs(dy) && dx < -50 -> swipeLeft()
                    Math.abs(dy) > Math.abs(dx) && dy > 50  -> swipeDown()
                    Math.abs(dy) > Math.abs(dx) && dy < -50 -> swipeUp()
                    else -> false
                }
                if (moved) {
                    addRandom()
                    invalidate()
                    post { onScoreChanged?.invoke(score) }
                    if (grid.any { row -> row.any { it == 2048 } }) post { onWin?.invoke() }
                    else if (!canMove()) post { onLose?.invoke() }
                }
            }
        }
        return true
    }

    private fun swipeLeft(): Boolean {
        var moved = false
        for (r in 0..3) {
            val row = grid[r].filter { it != 0 }.toMutableList()
            var i = 0
            while (i < row.size - 1) {
                if (row[i] == row[i+1]) { row[i] *= 2; score += row[i]; row.removeAt(i+1); moved = true }
                i++
            }
            while (row.size < 4) row.add(0)
            if (!grid[r].contentEquals(row.toIntArray())) moved = true
            for (c in 0..3) grid[r][c] = row[c]
        }
        return moved
    }

    private fun swipeRight(): Boolean {
        var moved = false
        for (r in 0..3) {
            val row = grid[r].filter { it != 0 }.reversed().toMutableList()
            var i = 0
            while (i < row.size - 1) {
                if (row[i] == row[i+1]) { row[i] *= 2; score += row[i]; row.removeAt(i+1); moved = true }
                i++
            }
            while (row.size < 4) row.add(0)
            val result = row.reversed()
            if (!grid[r].contentEquals(result.toIntArray())) moved = true
            for (c in 0..3) grid[r][c] = result[c]
        }
        return moved
    }

    private fun swipeUp(): Boolean {
        var moved = false
        for (c in 0..3) {
            val col = (0..3).map { grid[it][c] }.filter { it != 0 }.toMutableList()
            var i = 0
            while (i < col.size - 1) {
                if (col[i] == col[i+1]) { col[i] *= 2; score += col[i]; col.removeAt(i+1); moved = true }
                i++
            }
            while (col.size < 4) col.add(0)
            for (r in 0..3) { if (grid[r][c] != col[r]) moved = true; grid[r][c] = col[r] }
        }
        return moved
    }

    private fun swipeDown(): Boolean {
        var moved = false
        for (c in 0..3) {
            val col = (0..3).map { grid[it][c] }.filter { it != 0 }.reversed().toMutableList()
            var i = 0
            while (i < col.size - 1) {
                if (col[i] == col[i+1]) { col[i] *= 2; score += col[i]; col.removeAt(i+1); moved = true }
                i++
            }
            while (col.size < 4) col.add(0)
            val result = col.reversed()
            for (r in 0..3) { if (grid[r][c] != result[r]) moved = true; grid[r][c] = result[r] }
        }
        return moved
    }

    private fun canMove(): Boolean {
        for (r in 0..3) for (c in 0..3) {
            if (grid[r][c] == 0) return true
            if (c < 3 && grid[r][c] == grid[r][c+1]) return true
            if (r < 3 && grid[r][c] == grid[r+1][c]) return true
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val tile = size / 4f
        val pad = tile * 0.06f

        canvas.drawRoundRect(0f, 0f, size, size, 12f, 12f, paintBg)

        for (r in 0..3) for (c in 0..3) {
            val v = grid[r][c]
            val color = tileColors[v.coerceAtMost(2048)] ?: "#3C3A32"
            val tilePaint = Paint().apply { this.color = Color.parseColor(color) }
            val left  = c * tile + pad; val top  = r * tile + pad
            val right = (c+1) * tile - pad; val bottom = (r+1) * tile - pad
            canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, tilePaint)

            if (v > 0) {
                val textSize = when {
                    v >= 1000 -> tile * 0.28f
                    v >= 100  -> tile * 0.34f
                    else      -> tile * 0.42f
                }
                paintText.textSize = textSize
                paintText.color = if (v <= 4) Color.parseColor("#776E65") else Color.WHITE
                canvas.drawText(v.toString(), left + (right - left)/2, top + (bottom - top)/2 + textSize/3, paintText)
            }
        }
    }
}
