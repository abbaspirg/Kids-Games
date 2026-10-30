package com.kidsgames.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A Halloween-themed snake game drawn entirely on a Canvas.
 *
 * Swipe to steer, tap to start / resume / play again. The snake wraps around the
 * edges of the board (friendlier for kids) and only loses when it bites itself.
 */
class SnakeGameView(context: Context) : View(context) {

    private enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        fun isOpposite(other: Direction) = dx == -other.dx && dy == -other.dy
    }

    private enum class State { READY, PLAYING, PAUSED, OVER }

    private data class Cell(val x: Int, val y: Int)

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)

    private val density = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("spooky_snake", Context.MODE_PRIVATE)

    // Board geometry, recomputed whenever the size or system insets change.
    private val columns = 12
    private var rows = 16
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val boardRect = RectF()
    private var insetTop = 0
    private var insetBottom = 0

    // Game state.
    private var state = State.READY
    private val snake = ArrayDeque<Cell>()
    private var fromCells = emptyList<Cell>()
    private var direction = Direction.UP
    private val pendingTurns = ArrayDeque<Direction>()
    private var food = Cell(0, 0)
    private var foodEmoji = TREATS[0]
    private var score = 0
    private var best = prefs.getInt(KEY_BEST, 0)
    private var won = false
    private val particles = mutableListOf<Particle>()

    // Timing.
    private var running = false
    private var lastFrameTime = 0L
    private var tickAccumulator = 0f
    private var clock = 0f

    // Decorations.
    private val topWeb = Path()
    private val bottomWeb = Path()
    private var spiderAnchorX = 0f
    private var spiderAnchorY = 0f
    private var spiderDrop = 0f

    // Touch tracking.
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var swipedThisTouch = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE8, 0xE8, 0xE8) }
    private val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(0xB4, 0xB4, 0xB4)
        strokeWidth = density * 0.9f
        strokeCap = Paint.Cap.ROUND
    }
    private val spiderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x9A, 0x9A, 0x9A)
        strokeWidth = density * 1.1f
        strokeCap = Paint.Cap.ROUND
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x5A, 0x4A, 0x46)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(density * 12f, 0f, density * 4f, Color.argb(40, 0, 0, 0))
    }
    private val cardRect = RectF()

    init {
        isFocusable = true
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                insetTop = bars.top
                insetBottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                insetTop = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                insetBottom = insets.systemWindowInsetBottom
            }
            layoutBoard()
            insets
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle

    fun resume() {
        running = true
        lastFrameTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
        if (state == State.PLAYING) state = State.PAUSED
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard()
    }

    private fun layoutBoard() {
        if (width == 0 || height == 0) return
        val sideMargin = 16f * density
        val hudHeight = 64f * density
        val availableWidth = width - sideMargin * 2
        val availableHeight = height - insetTop - insetBottom - hudHeight - sideMargin
        cellSize = availableWidth / columns
        rows = max(8, floor(availableHeight / cellSize).toInt())
        boardLeft = sideMargin
        boardTop = insetTop + hudHeight + (availableHeight - rows * cellSize) / 2f
        boardRect.set(boardLeft, boardTop, boardLeft + columns * cellSize, boardTop + rows * cellSize)

        emojiPaint.textSize = cellSize * 0.82f
        buildDecorations()
        if (state == State.READY || snake.isEmpty()) newGame()
        invalidate()
    }

    // ---------------------------------------------------------------------------------------------
    // Game logic

    private fun newGame() {
        snake.clear()
        pendingTurns.clear()
        particles.clear()
        val startX = columns / 4
        val startY = rows / 2
        for (i in 0 until 4) snake.addLast(Cell(startX, startY + i))
        fromCells = snake.toList()
        direction = Direction.UP
        score = 0
        won = false
        tickAccumulator = 0f
        spawnFood()
    }

    private fun spawnFood() {
        val free = ArrayList<Cell>()
        val occupied = snake.toHashSet()
        for (y in 0 until rows) for (x in 0 until columns) {
            val cell = Cell(x, y)
            if (cell !in occupied) free.add(cell)
        }
        if (free.isEmpty()) {
            won = true
            endGame()
            return
        }
        food = free[Random.nextInt(free.size)]
        foodEmoji = TREATS[Random.nextInt(TREATS.size)]
    }

    private fun tickInterval(): Float = if (state == State.READY) 150f else max(75f, 170f - score * 4f)

    /** While waiting on the start screen the snake plays by itself, heading for the nearest treat. */
    private fun autopilot() {
        val head = snake.first()
        val body = snake.toList().dropLast(1).toHashSet()
        var bestTurn: Direction? = null
        var bestDistance = Int.MAX_VALUE
        for (turn in Direction.values()) {
            if (turn.isOpposite(direction)) continue
            val next = Cell((head.x + turn.dx + columns) % columns, (head.y + turn.dy + rows) % rows)
            if (next in body) continue
            val dx = abs(next.x - food.x).let { min(it, columns - it) }
            val dy = abs(next.y - food.y).let { min(it, rows - it) }
            if (dx + dy < bestDistance) {
                bestDistance = dx + dy
                bestTurn = turn
            }
        }
        bestTurn?.let { direction = it }
    }

    private fun step() {
        if (state == State.READY) autopilot()
        pendingTurns.removeFirstOrNull()?.let { direction = it }

        val head = snake.first()
        val next = Cell(
            (head.x + direction.dx + columns) % columns,
            (head.y + direction.dy + rows) % rows
        )
        val grows = next == food

        // The tail moves out of the way this tick unless we're growing.
        val bodyCount = if (grows) snake.size else snake.size - 1
        for ((index, cell) in snake.withIndex()) {
            if (index >= bodyCount) break
            if (cell == next) {
                if (state == State.READY) newGame() else endGame()
                return
            }
        }

        val previous = snake.toList()
        snake.addFirst(next)
        if (!grows) snake.removeLast()
        fromCells = List(snake.size) { i -> previous[min(i, previous.size - 1)] }

        if (grows) {
            burstAt(next)
            if (state == State.PLAYING) {
                score++
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            spawnFood()
        }
    }

    private fun endGame() {
        state = State.OVER
        pendingTurns.clear()
        fromCells = snake.toList()
        if (score > best) {
            best = score
            prefs.edit().putInt(KEY_BEST, best).apply()
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun queueTurn(turn: Direction) {
        // A swipe can start or resume a round, but restarting after a loss needs a deliberate tap.
        if (state == State.OVER) return
        if (state != State.PLAYING) onTap()
        val last = pendingTurns.lastOrNull() ?: direction
        if (turn == last || turn.isOpposite(last)) return
        if (pendingTurns.size < 2) pendingTurns.addLast(turn)
    }

    private fun onTap() {
        when (state) {
            State.READY -> {
                newGame()
                state = State.PLAYING
            }
            State.PAUSED -> state = State.PLAYING
            State.OVER -> {
                newGame()
                state = State.PLAYING
            }
            State.PLAYING -> Unit
        }
        lastFrameTime = SystemClock.uptimeMillis()
    }

    private fun burstAt(cell: Cell) {
        val cx = cellCenterX(cell.x.toFloat())
        val cy = cellCenterY(cell.y.toFloat())
        repeat(12) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = cellSize * (2f + Random.nextFloat() * 3f)
            particles.add(Particle(cx, cy, cos(angle) * speed, sin(angle) * speed, 1f))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                swipedThisTouch = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val threshold = 24f * density
                if (abs(dx) > threshold || abs(dy) > threshold) {
                    val turn = if (abs(dx) > abs(dy)) {
                        if (dx > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (dy > 0) Direction.DOWN else Direction.UP
                    }
                    queueTurn(turn)
                    // Re-anchor so one continuous finger drag can make several turns.
                    touchStartX = event.x
                    touchStartY = event.y
                    swipedThisTouch = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!swipedThisTouch) {
                    onTap()
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dtMs = (now - lastFrameTime).coerceIn(0L, 100L).toFloat()
        lastFrameTime = now
        clock += dtMs / 1000f

        val moving = state == State.PLAYING || state == State.READY
        if (moving) {
            tickAccumulator += dtMs
            while ((state == State.PLAYING || state == State.READY) && tickAccumulator >= tickInterval()) {
                tickAccumulator -= tickInterval()
                step()
            }
        }
        val progress = if (moving) (tickAccumulator / tickInterval()).coerceIn(0f, 1f) else 1f

        canvas.drawColor(BACKGROUND)
        drawGridDots(canvas)
        canvas.drawPath(topWeb, webPaint)
        canvas.drawPath(bottomWeb, webPaint)
        drawSpider(canvas)
        drawFood(canvas)
        drawSnake(canvas, progress)
        drawParticles(canvas, dtMs / 1000f)
        drawHud(canvas)
        drawOverlay(canvas)

        if (running) postInvalidateOnAnimation()
    }

    private fun cellCenterX(x: Float) = boardLeft + (x + 0.5f) * cellSize
    private fun cellCenterY(y: Float) = boardTop + (y + 0.5f) * cellSize

    private fun drawGridDots(canvas: Canvas) {
        val radius = cellSize * 0.07f
        for (y in 0 until rows) for (x in 0 until columns) {
            canvas.drawCircle(cellCenterX(x.toFloat()), cellCenterY(y.toFloat()), radius, dotPaint)
        }
    }

    private fun drawFood(canvas: Canvas) {
        if (state == State.OVER && won) return
        val pulse = 1f + 0.06f * sin(clock * 5f)
        val cx = cellCenterX(food.x.toFloat())
        val cy = cellCenterY(food.y.toFloat())
        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)
        val metrics = emojiPaint.fontMetrics
        canvas.drawText(foodEmoji, cx, cy - (metrics.ascent + metrics.descent) / 2f, emojiPaint)
        canvas.restore()
    }

    private fun drawSnake(canvas: Canvas, progress: Float) {
        val count = snake.size
        canvas.save()
        canvas.clipRect(boardRect)
        // Draw tail first so the head ends up on top.
        for (i in count - 1 downTo 0) {
            val to = snake[i]
            val from = fromCells.getOrElse(i) { to }
            var fx = from.x.toFloat()
            var fy = from.y.toFloat()
            // Slide in from the opposite edge when wrapping around the board.
            if (to.x - fx > 1) fx += columns else if (fx - to.x > 1) fx -= columns
            if (to.y - fy > 1) fy += rows else if (fy - to.y > 1) fy -= rows
            val gx = fx + (to.x - fx) * progress
            val gy = fy + (to.y - fy) * progress
            val cx = cellCenterX(gx)
            val cy = cellCenterY(gy)

            val t = if (count > 1) i / (count - 1f) else 0f
            fillPaint.color = blend(HEAD_COLOR, TAIL_COLOR, t)
            if (state == State.OVER && !won && i == 0 && (clock * 4f).toInt() % 2 == 0) {
                fillPaint.color = Color.rgb(0xE5, 0x4B, 0x3C)
            }

            val radius = when {
                i == 0 -> cellSize * 0.43f
                i == count - 1 -> cellSize * 0.09f
                else -> cellSize * (0.36f - 0.06f * t)
            }
            canvas.drawCircle(cx, cy, radius, fillPaint)
            if (i == 0) drawEyes(canvas, cx, cy)
        }
        canvas.restore()
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float) {
        val looking = pendingTurns.firstOrNull() ?: direction
        val forward = cellSize * 0.1f
        val side = cellSize * 0.12f
        val eyeRadius = cellSize * 0.065f
        // Perpendicular to the direction of travel.
        val px = -looking.dy
        val py = looking.dx
        fillPaint.color = Color.WHITE
        val blink = (clock % 4f) > 3.85f
        for (s in intArrayOf(-1, 1)) {
            val ex = cx + looking.dx * forward + px * side * s
            val ey = cy + looking.dy * forward + py * side * s
            if (blink) {
                canvas.drawRect(ex - eyeRadius, ey - eyeRadius * 0.3f, ex + eyeRadius, ey + eyeRadius * 0.3f, fillPaint)
            } else {
                canvas.drawCircle(ex, ey, eyeRadius, fillPaint)
            }
        }
    }

    private fun drawParticles(canvas: Canvas, dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt * 1.8f
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            fillPaint.color = Color.argb((p.life * 255).toInt(), 0xF2, 0x8C, 0x28)
            canvas.drawCircle(p.x, p.y, cellSize * 0.08f * p.life + 1f, fillPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val baseline = insetTop + 44f * density
        textPaint.textSize = 26f * density
        textPaint.textAlign = Paint.Align.LEFT
        emojiPaint.textSize = 24f * density
        canvas.drawText(PUMPKIN, boardLeft + 14f * density, baseline - 1f * density, emojiPaint)
        canvas.drawText(score.toString(), boardLeft + 32f * density, baseline, textPaint)

        textPaint.textSize = 16f * density
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.alpha = 160
        canvas.drawText("BEST $best", boardRect.right - 4f * density, baseline - 4f * density, textPaint)
        textPaint.alpha = 255
        emojiPaint.textSize = cellSize * 0.82f
    }

    private fun drawOverlay(canvas: Canvas) {
        val (title, subtitle) = when (state) {
            State.PLAYING -> return
            State.READY -> "Spooky Snake" to "Swipe to steer • Tap to start"
            State.PAUSED -> "Paused" to "Tap to keep playing"
            State.OVER -> (if (won) "You Win! 🎉" else "Oops!") to "You ate $score treats • Tap to play again"
        }
        val cx = width / 2f
        // Keep the start card low so the self-playing demo snake stays visible above it.
        val cy = if (state == State.READY) boardRect.bottom - 90f * density else boardRect.centerY()
        val cardWidth = min(width - 48f * density, 340f * density)
        cardRect.set(cx - cardWidth / 2f, cy - 70f * density, cx + cardWidth / 2f, cy + 70f * density)
        canvas.drawRoundRect(cardRect, 24f * density, 24f * density, cardPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f * density
        textPaint.color = HEAD_COLOR
        canvas.drawText(title, cx, cy - 6f * density, textPaint)
        textPaint.textSize = 15f * density
        textPaint.color = Color.rgb(0x7A, 0x6A, 0x66)
        canvas.drawText(subtitle, cx, cy + 32f * density, textPaint)
        textPaint.color = Color.rgb(0x5A, 0x4A, 0x46)
    }

    // ---------------------------------------------------------------------------------------------
    // Cobwebs and the dangling spider

    private fun buildDecorations() {
        val random = Random(31)
        topWeb.reset()
        bottomWeb.reset()

        // Big web in the top-right corner, partly off screen.
        spiderAnchorX = width * 0.80f
        spiderAnchorY = insetTop + height * 0.09f
        buildWeb(topWeb, spiderAnchorX, spiderAnchorY, width * 0.30f, 0f, 360f, 15, 8, random)

        // Fan-shaped web tucked into the bottom-left corner.
        val hubX = boardLeft * 0.8f
        val hubY = boardRect.bottom - cellSize * 2.2f
        buildWeb(bottomWeb, hubX, hubY, width * 0.48f, 8f, 72f, 6, 7, random)
        bottomWeb.moveTo(hubX + cellSize * 0.3f, hubY + cellSize * 1.8f)
        bottomWeb.lineTo(hubX + cellSize * 1.0f, hubY + cellSize * 2.2f)

        spiderDrop = height * 0.30f
    }

    private fun buildWeb(
        path: Path,
        hubX: Float,
        hubY: Float,
        radius: Float,
        fromDeg: Float,
        toDeg: Float,
        spokes: Int,
        rings: Int,
        random: Random
    ) {
        val fullCircle = toDeg - fromDeg >= 360f
        val spokeCount = if (fullCircle) spokes else spokes + 1
        val step = (toDeg - fromDeg) / spokes
        val angles = FloatArray(spokeCount) { i ->
            val jitter = if (fullCircle || (i in 1 until spokeCount - 1)) (random.nextFloat() - 0.5f) * step * 0.4f else 0f
            Math.toRadians((fromDeg + i * step + jitter).toDouble()).toFloat()
        }
        val lengths = FloatArray(spokeCount) { radius * (0.8f + random.nextFloat() * 0.4f) }

        for (i in 0 until spokeCount) {
            path.moveTo(hubX, hubY)
            path.lineTo(hubX + cos(angles[i]) * lengths[i] * 1.25f, hubY + sin(angles[i]) * lengths[i] * 1.25f)
        }

        for (ring in 1..rings) {
            val fraction = ring / rings.toFloat()
            val links = if (fullCircle) spokeCount else spokeCount - 1
            for (i in 0 until links) {
                val j = (i + 1) % spokeCount
                val ax = hubX + cos(angles[i]) * lengths[i] * fraction
                val ay = hubY + sin(angles[i]) * lengths[i] * fraction
                val bx = hubX + cos(angles[j]) * lengths[j] * fraction
                val by = hubY + sin(angles[j]) * lengths[j] * fraction
                // Each thread sags a little toward the hub.
                val mx = (ax + bx) / 2f
                val my = (ay + by) / 2f
                val sag = 0.18f
                path.moveTo(ax, ay)
                path.quadTo(mx + (hubX - mx) * sag, my + (hubY - my) * sag, bx, by)
            }
        }
    }

    private fun drawSpider(canvas: Canvas) {
        val sway = sin(clock * 1.3f) * 0.08f
        val bob = sin(clock * 0.9f) * cellSize * 0.6f
        val length = spiderDrop + bob
        val x = spiderAnchorX + sin(sway) * length
        val y = spiderAnchorY + cos(sway) * length
        canvas.drawLine(spiderAnchorX, spiderAnchorY, x, y, webPaint)

        val s = cellSize * 0.32f
        spiderPaint.style = Paint.Style.STROKE
        val legWiggle = sin(clock * 6f) * s * 0.08f
        for (side in intArrayOf(-1, 1)) {
            for (leg in 0 until 4) {
                val rootY = y + s * 0.25f + leg * s * 0.18f
                val kneeX = x + side * s * (0.75f + leg * 0.05f)
                val kneeY = rootY - s * (0.55f - leg * 0.3f) + legWiggle * side
                val footX = x + side * s * (1.05f + leg * 0.1f)
                val footY = kneeY + s * (0.7f + leg * 0.12f)
                canvas.drawLine(x, rootY, kneeX, kneeY, spiderPaint)
                canvas.drawLine(kneeX, kneeY, footX, footY, spiderPaint)
            }
        }
        spiderPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y + s * 0.15f, s * 0.28f, spiderPaint)
        canvas.drawOval(x - s * 0.33f, y + s * 0.3f, x + s * 0.33f, y + s * 1.05f, spiderPaint)
    }

    companion object {
        private const val KEY_BEST = "best_score"
        private const val PUMPKIN = "🎃"
        private val TREATS = listOf(PUMPKIN, "👻", "🍬")
        private val BACKGROUND = Color.rgb(0xFB, 0xFB, 0xFB)
        private val HEAD_COLOR = Color.rgb(0xFF, 0x70, 0x5B)
        private val TAIL_COLOR = Color.rgb(0xFF, 0xBC, 0xB0)

        private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        )
    }
}
