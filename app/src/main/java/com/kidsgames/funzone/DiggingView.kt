package com.kidsgames.funzone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Side-on building site: drag the JCB's digger arm into the dirt pile to
 * scoop, then over the dump truck to tip it in. Three scoops fill a truck,
 * which drives off and is replaced by a new one. Some scoops dig up treasure.
 */
@SuppressLint("ViewConstructor")
class DiggingView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun say(text: String)
        fun onTruckFull(trucks: Int)
    }

    private class Dirt(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)
    private class Treasure(val emoji: String, var x: Float, var y: Float, var life: Float)
    private enum class TruckState { ARRIVING, WAITING, LEAVING }

    private val random = Random(System.nanoTime())
    private val engine = EngineSound(0.6f, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()
    private val rect = RectF()

    private val treasures = listOf(
        "🦴" to "a dinosaur bone", "💎" to "a shiny gem", "🐛" to "a wiggly worm",
        "🥕" to "a carrot", "🐚" to "a sea shell", "👑" to "a golden crown",
    )
    private val truckColors = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#1E88E5"), Color.parseColor("#43A047"),
        Color.parseColor("#8E24AA"), Color.parseColor("#FB8C00"),
    )

    // Layout, refreshed in onSizeChanged.
    private var u = 1f
    private var ground = 0f
    private var baseX = 0f
    private var shoulderX = 0f
    private var shoulderY = 0f
    private var boom = 0f
    private var dipper = 0f
    private var pileX = 0f
    private var pileHalf = 0f
    private var bedW = 0f
    private var truckParkX = 0f

    // State.
    private var bucketX = 0f
    private var bucketY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var bucketFull = false
    private var pile = PILE_MAX
    private var truckX = 0f
    private var truckState = TruckState.ARRIVING
    private var truckLoad = 0
    private var truckColor = truckColors[0]
    private var trucksFilled = 0
    private var treasureCount = 0
    private var firstScoop = true
    private var firstDump = true
    private val dirt = mutableListOf<Dirt>()
    private val found = mutableListOf<Treasure>()
    private var time = 0f
    private var cloudShift = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var laidOut = false

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        engine.start()
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
        engine.stop()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        u = min(h / 100f, w / 190f)
        ground = h * 0.82f
        baseX = w * 0.04f + 8 * u
        shoulderX = baseX + 56 * u
        shoulderY = ground - 30 * u
        val room = w - shoulderX
        pileX = shoulderX + room * 0.3f
        pileHalf = room * 0.17f
        bedW = room * 0.26f
        truckParkX = shoulderX + room * 0.56f
        val reach = room * 0.86f
        boom = reach * 0.54f
        dipper = reach * 0.46f
        if (!laidOut) {
            laidOut = true
            bucketX = shoulderX + boom * 0.6f
            bucketY = ground - 60 * u
            targetX = bucketX
            targetY = bucketY
            truckX = w + 20 * u
            listener.say("Drag the digger arm into the dirt to scoop it up!")
        }
    }

    // ---- loop -----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else min(0.05f, (now - lastFrameNanos) / 1e9f)
        lastFrameNanos = now
        if (running && laidOut) update(dt)
        render(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        time += dt
        cloudShift += dt * 6 * u

        // The bucket glides toward the finger like a hydraulic arm.
        val dx = targetX - bucketX
        val dy = targetY - bucketY
        val dist = hypot(dx, dy)
        val step = min(dist, 140 * u * dt)
        if (dist > 0.01f) {
            bucketX += dx / dist * step
            bucketY += dy / dist * step
        }
        engine.speedFraction = 0.1f + 0.7f * (step / max(0.001f, 140 * u * dt))
        clampBucket()

        if (!bucketFull && pile > 0 && insidePile(bucketX, bucketY + 4 * u)) scoop()
        if (bucketFull && truckState == TruckState.WAITING &&
            bucketX in truckX + 2 * u..truckX + bedW - 2 * u && bucketY < ground - 38 * u
        ) dump()

        when (truckState) {
            TruckState.ARRIVING -> {
                truckX = max(truckParkX, truckX - 90 * u * dt)
                if (truckX <= truckParkX) truckState = TruckState.WAITING
            }
            TruckState.LEAVING -> {
                truckX += 110 * u * dt
                if (truckX > width + 20 * u) {
                    truckLoad = 0
                    truckColor = truckColors[trucksFilled % truckColors.size]
                    truckState = TruckState.ARRIVING
                }
            }
            TruckState.WAITING -> Unit
        }
        // The pile slowly grows back so there is always something to dig.
        if (pile < PILE_MAX && truckState != TruckState.WAITING) pile = min(PILE_MAX, pile + dt * 1.5f)

        val g = 300 * u
        val it = dirt.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.vy += g * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.life -= dt
            if (d.life <= 0f || d.y > ground) it.remove()
        }
        val ft = found.iterator()
        while (ft.hasNext()) {
            val t = ft.next()
            t.y -= 30 * u * dt
            t.life -= dt
            if (t.life <= 0f) ft.remove()
        }
    }

    private fun clampBucket() {
        val maxReach = boom + dipper - u
        val dx = bucketX - shoulderX
        val dy = bucketY - shoulderY
        val d = hypot(dx, dy)
        if (d > maxReach) {
            bucketX = shoulderX + dx / d * maxReach
            bucketY = shoulderY + dy / d * maxReach
        }
        bucketX = max(bucketX, shoulderX + 10 * u)
        bucketY = min(bucketY, ground - 3 * u)
    }

    private fun pileHeightAt(x: Float): Float {
        val t = (x - pileX) / pileHalf
        if (abs(t) >= 1f) return 0f
        return (6 * u + pile / PILE_MAX * 32 * u) * (1 - t * t)
    }

    private fun insidePile(x: Float, y: Float) = y > ground - pileHeightAt(x) && abs(x - pileX) < pileHalf

    private fun scoop() {
        bucketFull = true
        pile = max(0f, pile - 1f)
        engine.bump()
        repeat(14) { spray(bucketX, bucketY) }
        if (random.nextFloat() < 0.3f) {
            val (emoji, name) = treasures.random(random)
            treasureCount++
            found += Treasure(emoji, bucketX, bucketY - 16 * u, 1.8f)
            listener.say("Wow! You found $name!")
        } else if (firstScoop) {
            listener.say("Great scoop! Now take it to the truck!")
        }
        firstScoop = false
    }

    private fun dump() {
        bucketFull = false
        truckLoad++
        engine.chime()
        repeat(24) { spray(bucketX, bucketY + 6 * u) }
        if (truckLoad >= LOADS_PER_TRUCK) {
            trucksFilled++
            truckState = TruckState.LEAVING
            listener.onTruckFull(trucksFilled)
        } else if (firstDump) {
            listener.say("Dump it! ${LOADS_PER_TRUCK - truckLoad} more scoops to fill the truck!")
        } else {
            listener.say("Dump!")
        }
        firstDump = false
    }

    private fun spray(x: Float, y: Float) {
        dirt += Dirt(x + (random.nextFloat() - 0.5f) * 10 * u, y, (random.nextFloat() - 0.5f) * 60 * u,
            -random.nextFloat() * 50 * u, 1.2f)
        if (dirt.size > 200) dirt.removeAt(0)
    }

    // ---- input ----------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN && e.x < shoulderX && e.y > ground - 50 * u) {
                    engine.horn()
                } else {
                    targetX = e.x
                    targetY = e.y
                }
            }
        }
        return true
    }

    // ---- drawing --------------------------------------------------------------------------

    private fun render(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.parseColor("#81D4FA"))
        circle(c, w * 0.88f, h * 0.14f, 12 * u, Color.parseColor("#FFEE58"))
        for (k in 0 until 3) {
            val cx = ((k * w * 0.4f + cloudShift) % (w + 60 * u)) - 30 * u
            val cy = h * (0.12f + k * 0.07f)
            circle(c, cx, cy, 7 * u, Color.WHITE)
            circle(c, cx + 8 * u, cy - 3 * u, 9 * u, Color.WHITE)
            circle(c, cx + 17 * u, cy, 7 * u, Color.WHITE)
        }

        paint.color = Color.parseColor("#8D6E63")
        c.drawRect(0f, ground, w, h, paint)
        paint.color = Color.parseColor("#7CB342")
        c.drawRect(0f, ground, w, ground + 3 * u, paint)

        drawPile(c)
        drawTruck(c)
        drawJcb(c)

        for (d in dirt) circle(c, d.x, d.y, 1.6f * u, Color.parseColor("#6D4C41"))
        for (t in found) {
            text.textSize = 22 * u
            text.alpha = (255 * min(1f, t.life)).toInt()
            c.drawText(t.emoji, t.x, t.y, text)
            text.alpha = 255
        }

        // Score boxes.
        text.textSize = 9 * u
        text.color = Color.parseColor("#263238")
        text.textAlign = Paint.Align.LEFT
        c.drawText("🚚 $trucksFilled   🎁 $treasureCount", 4 * u, 12 * u, text)
        text.textAlign = Paint.Align.CENTER
        if (truckState == TruckState.WAITING) {
            val bx = truckX + bedW / 2
            for (i in 0 until LOADS_PER_TRUCK) {
                circle(c, bx + (i - 1) * 8 * u, ground - 48 * u, 3 * u,
                    if (i < truckLoad) Color.parseColor("#795548") else Color.argb(120, 255, 255, 255))
            }
        }
    }

    private fun drawPile(c: Canvas) {
        path.reset()
        path.moveTo(pileX - pileHalf, ground)
        val steps = 24
        for (i in 0..steps) {
            val x = pileX - pileHalf + 2 * pileHalf * i / steps
            path.lineTo(x, ground - pileHeightAt(x))
        }
        path.close()
        paint.color = Color.parseColor("#6D4C41")
        c.drawPath(path, paint)
        // Pebbles on the pile.
        for (k in 0 until 7) {
            val x = pileX - pileHalf * 0.7f + k * pileHalf * 0.23f
            val top = ground - pileHeightAt(x)
            if (top < ground - 4 * u) circle(c, x, top + (ground - top) * 0.45f, 1.5f * u, Color.parseColor("#4E342E"))
        }
    }

    private fun drawTruck(c: Canvas) {
        val x = truckX
        val bedTop = ground - 34 * u
        val bedBottom = ground - 14 * u
        // Dirt heaped in the bed.
        if (truckLoad > 0) {
            val fill = truckLoad / LOADS_PER_TRUCK.toFloat()
            rect.set(x + 2 * u, bedTop - fill * 10 * u, x + bedW - 2 * u, bedBottom)
            paint.color = Color.parseColor("#6D4C41")
            c.drawRoundRect(rect, 8 * u, 8 * u, paint)
        }
        path.reset()
        path.moveTo(x, bedTop)
        path.lineTo(x + bedW, bedTop)
        path.lineTo(x + bedW - 3 * u, bedBottom)
        path.lineTo(x + 3 * u, bedBottom)
        path.close()
        paint.color = truckColor
        c.drawPath(path, paint)
        paint.color = VehiclePainter.shade(truckColor, 0.75f)
        c.drawRect(x + 4 * u, bedTop + 6 * u, x + bedW - 4 * u, bedTop + 8 * u, paint)
        paint.color = Color.parseColor("#455A64")
        c.drawRect(x, bedBottom, x + bedW + 24 * u, bedBottom + 4 * u, paint)
        // Cab facing left toward the digger.
        rect.set(x + bedW + 2 * u, ground - 38 * u, x + bedW + 22 * u, bedBottom + 2 * u)
        paint.color = truckColor
        c.drawRoundRect(rect, 3 * u, 3 * u, paint)
        rect.set(x + bedW + 5 * u, ground - 35 * u, x + bedW + 15 * u, ground - 26 * u)
        paint.color = Color.parseColor("#B3E5FC")
        c.drawRoundRect(rect, 2 * u, 2 * u, paint)
        circle(c, x + bedW + 21 * u, ground - 18 * u, 1.6f * u, Color.parseColor("#FFF59D"))
        for (wx in floatArrayOf(x + 9 * u, x + bedW - 7 * u, x + bedW + 14 * u)) wheel(c, wx, ground - 7 * u, 7 * u, -truckX)
    }

    private fun drawJcb(c: Canvas) {
        val yellow = Color.parseColor("#FFC107")
        val dark = Color.parseColor("#263238")
        val bx = baseX

        // Front loader bucket on the far side.
        line(c, bx + 12 * u, ground - 20 * u, bx - 2 * u, ground - 14 * u, 3 * u, VehiclePainter.shade(yellow, 0.8f))
        rect.set(bx - 10 * u, ground - 18 * u, bx, ground - 6 * u)
        paint.color = Color.parseColor("#9E9E9E")
        c.drawRoundRect(rect, 2 * u, 2 * u, paint)

        // Chassis, engine bonnet and cab.
        rect.set(bx + 2 * u, ground - 26 * u, bx + 58 * u, ground - 10 * u)
        paint.color = yellow
        c.drawRoundRect(rect, 3 * u, 3 * u, paint)
        rect.set(bx + 2 * u, ground - 34 * u, bx + 18 * u, ground - 24 * u)
        c.drawRoundRect(rect, 3 * u, 3 * u, paint)
        line(c, bx + 6 * u, ground - 33 * u, bx + 6 * u, ground - 41 * u, 2 * u, dark)
        rect.set(bx + 18 * u, ground - 56 * u, bx + 44 * u, ground - 24 * u)
        paint.color = dark
        c.drawRoundRect(rect, 3 * u, 3 * u, paint)
        rect.set(bx + 21 * u, ground - 53 * u, bx + 41 * u, ground - 28 * u)
        paint.color = Color.parseColor("#B3E5FC")
        c.drawRect(rect, paint)
        // Driver in a hard hat.
        circle(c, bx + 31 * u, ground - 38 * u, 4 * u, Color.parseColor("#FFCC80"))
        rect.set(bx + 26.5f * u, ground - 44 * u, bx + 35.5f * u, ground - 39 * u)
        paint.color = Color.parseColor("#FF9800")
        c.drawRoundRect(rect, 3 * u, 3 * u, paint)
        rect.set(bx + 16 * u, ground - 59 * u, bx + 46 * u, ground - 55 * u)
        paint.color = yellow
        c.drawRoundRect(rect, 2 * u, 2 * u, paint)
        val beacon = if ((time * 3).toInt() % 2 == 0) Color.parseColor("#FF6D00") else Color.parseColor("#FFE0B2")
        circle(c, bx + 31 * u, ground - 61 * u, 2.5f * u, beacon)
        line(c, bx + 2 * u, ground - 18 * u, bx + 58 * u, ground - 18 * u, 1.2f * u, dark)
        // Stabiliser leg on the digging side.
        line(c, bx + 56 * u, ground - 14 * u, bx + 62 * u, ground - 1 * u, 2.5f * u, dark)

        wheel(c, bx + 16 * u, ground - 10 * u, 10 * u, 0f)
        wheel(c, bx + 50 * u, ground - 8 * u, 8 * u, 0f)

        // Inverse kinematics for the two-part arm (elbow up).
        val dx = bucketX - shoulderX
        val dy = bucketY - shoulderY
        val d = hypot(dx, dy).coerceIn(abs(boom - dipper) + 1f, boom + dipper - 0.01f)
        val base = atan2(dy, dx)
        val cosA = ((boom * boom + d * d - dipper * dipper) / (2 * boom * d)).coerceIn(-1f, 1f)
        val shoulderAngle = base - acos(cosA)
        val elbowX = shoulderX + cos(shoulderAngle) * boom
        val elbowY = shoulderY + sin(shoulderAngle) * boom

        // Hydraulic rams, then the boom and dipper.
        val ramBaseX = bx + 50 * u
        val ramBaseY = ground - 26 * u
        line(c, ramBaseX, ramBaseY, (shoulderX + elbowX) / 2, (shoulderY + elbowY) / 2, 2.2f * u, Color.parseColor("#B0BEC5"))
        line(c, shoulderX, shoulderY, elbowX, elbowY, 7 * u, yellow)
        line(c, shoulderX, shoulderY, elbowX, elbowY, 1.2f * u, dark)
        line(c, elbowX, elbowY, bucketX, bucketY, 5 * u, VehiclePainter.shade(yellow, 0.9f))
        circle(c, shoulderX, shoulderY, 3.5f * u, dark)
        circle(c, elbowX, elbowY, 3 * u, dark)

        // Bucket: open while empty, curled up and heaped with dirt when full.
        val dipperAngle = Math.toDegrees(atan2((bucketY - elbowY).toDouble(), (bucketX - elbowX).toDouble())).toFloat()
        c.save()
        c.translate(bucketX, bucketY)
        c.rotate(if (bucketFull) dipperAngle - 120f else dipperAngle - 30f)
        path.reset()
        path.moveTo(-6 * u, -5 * u)
        path.lineTo(7 * u, -5 * u)
        path.lineTo(9 * u, 4 * u)
        path.quadTo(0f, 10 * u, -6 * u, 4 * u)
        path.close()
        paint.color = Color.parseColor("#757575")
        c.drawPath(path, paint)
        for (k in 0 until 3) {
            path.reset()
            val tx = -4 * u + k * 5 * u
            path.moveTo(tx, 6 * u)
            path.lineTo(tx + 2 * u, 9.5f * u)
            path.lineTo(tx + 3.5f * u, 6 * u)
            path.close()
            paint.color = Color.parseColor("#424242")
            c.drawPath(path, paint)
        }
        if (bucketFull) circle(c, 1.5f * u, -5 * u, 6 * u, Color.parseColor("#6D4C41"))
        c.restore()
    }

    private fun wheel(c: Canvas, x: Float, y: Float, r: Float, roll: Float) {
        circle(c, x, y, r, Color.parseColor("#212121"))
        circle(c, x, y, r * 0.5f, Color.parseColor("#FFC107"))
        val a = roll / r
        for (k in 0 until 4) {
            val ang = a + k * 1.5708f
            line(c, x, y, x + cos(ang) * r * 0.45f, y + sin(ang) * r * 0.45f, r * 0.12f, Color.parseColor("#F57F17"))
        }
        circle(c, x, y, r * 0.15f, Color.parseColor("#424242"))
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(x, y, r, paint)
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = color
        c.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }

    private companion object {
        const val PILE_MAX = 6f
        const val LOADS_PER_TRUCK = 3
    }
}
