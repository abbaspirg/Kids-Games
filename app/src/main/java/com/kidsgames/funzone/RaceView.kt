package com.kidsgames.funzone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A top-down, three-lane race. The world is measured in metres: x runs across
 * the road (0 = centre) and dist runs along it. The camera follows the player.
 *
 * Controls are made for small hands: the vehicle drives itself forward and
 * follows the child's finger left and right (or the phone's tilt, when
 * [tiltSteering] is on). The big button honks the horn.
 */
@SuppressLint("ViewConstructor")
class RaceView(
    context: Context,
    private val vehicle: VehicleType,
    private val track: Track,
    private val listener: Listener,
    private val playerColor: Int = vehicle.color,
    private val tiltSteering: Boolean = false,
) : View(context), SensorEventListener {

    interface Listener {
        fun say(text: String)
        fun onRaceFinished(position: Int, coins: Int)
    }

    private class Racer(val type: VehicleType, var x: Float, var dist: Float) {
        var speed = 0f
        var vx = 0f
        var targetX = x
        var pace = 1f
        var spin = 0f
        var boost = 0f
        var slow = 0f
        var finished = false
    }

    private class Obstacle(val kind: ObstacleKind, val x: Float, val dist: Float) {
        var gone = false
        var touched = false
    }

    private class Particle(
        var x: Float, var dist: Float, val vx: Float, val vd: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float,
    )

    private enum class Phase { COUNTDOWN, RACING, FINISHED }

    private val random = Random(System.nanoTime())
    private val raceLength = vehicle.topSpeed * 40f
    private val me = Racer(vehicle, 0f, 0f)
    private val rivals: List<Racer>
    private val obstacles = mutableListOf<Obstacle>()
    private val particles = mutableListOf<Particle>()
    private val engine = EngineSound(vehicle.enginePitch, vehicle.heavy)

    private var phase = Phase.COUNTDOWN
    private var countdown = 3f
    private var lastCount = 4
    private var goFlash = 0f
    private var finishedCount = 0
    private var myPlace = 0
    private var coins = 0
    private var shake = 0f
    private var time = 0f
    private var raceTime = 0f
    private var bumpTalkCooldown = 0f
    private var hornGlow = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var steeringPointer = -1
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private var smoothTilt = 0f

    // Screen mapping, refreshed in onSizeChanged.
    private var ppm = 1f
    private var playerY = 0f
    private var hornX = 0f
    private var hornY = 0f
    private var hornR = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private val path = Path()

    init {
        val others = VehicleType.entries.filter { it != vehicle }.shuffled(random).take(3)
        val grid = listOf(-LANE_W to 7f, LANE_W to 7f, 0f to 15f)
        rivals = others.mapIndexed { i, type ->
            Racer(type, grid[i].first, grid[i].second).apply { pace = 0.9f + random.nextFloat() * 0.08f }
        }

        var d = 70f
        while (d < raceLength - 40f) {
            val lane = (random.nextInt(3) - 1) * LANE_W
            val roll = random.nextFloat()
            when {
                roll < 0.35f -> repeat(3) { obstacles += Obstacle(ObstacleKind.COIN, lane, d + it * 3f) }
                roll < 0.47f -> obstacles += Obstacle(ObstacleKind.BOOST, lane, d)
                else -> obstacles += Obstacle(track.hazards.random(random), lane, d)
            }
            d += 16f + random.nextFloat() * 14f
        }
    }

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        engine.start()
        if (tiltSteering) {
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
        engine.stop()
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // In portrait, tilting the right edge down makes x negative.
        smoothTilt += (-event.values[0] - smoothTilt) * 0.25f
        if (steeringPointer < 0) me.targetX = (smoothTilt / 4.5f) * ROAD_HALF
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val roadPx = min(w * 0.62f, h * 0.5f)
        ppm = roadPx / (ROAD_HALF * 2)
        playerY = h * 0.72f
        hornR = context.dp(44).toFloat()
        hornX = w - hornR - context.dp(16)
        hornY = h - hornR - context.dp(20)
    }

    // ---- game loop ------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else min(0.05f, (now - lastFrameNanos) / 1e9f)
        lastFrameNanos = now
        if (running) update(dt)
        render(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        time += dt
        goFlash = max(0f, goFlash - dt)
        shake = max(0f, shake - dt)
        hornGlow = max(0f, hornGlow - dt)
        bumpTalkCooldown = max(0f, bumpTalkCooldown - dt)

        if (phase == Phase.COUNTDOWN) {
            countdown -= dt
            val n = ceil(countdown).toInt()
            if (n != lastCount) {
                lastCount = n
                listener.say(if (n > 0) "$n" else "Go!")
            }
            if (countdown <= 0f) {
                phase = Phase.RACING
                goFlash = 1f
            }
        } else {
            if (phase == Phase.RACING) raceTime += dt
            updatePlayer(dt)
            rivals.forEach { updateRival(it, dt) }
            checkCollisions(dt)
        }
        updateParticles(dt)
        engine.speedFraction = me.speed / vehicle.topSpeed
    }

    private fun updatePlayer(dt: Float) {
        val top = vehicle.topSpeed
        var target = if (phase == Phase.FINISHED) top * 0.3f else top
        if (me.boost > 0f) {
            target *= 1.35f
            me.boost -= dt
            if (random.nextFloat() < 0.6f) emitBehind(me, Color.parseColor("#FFAB00"), 0.35f)
        }
        if (me.slow > 0f) {
            target *= 0.55f
            me.slow -= dt
        }
        me.speed = approach(me.speed, target, if (me.speed < target) vehicle.accel * dt else 14f * dt)

        val maxX = ROAD_HALF - vehicle.widthM / 2
        me.targetX = me.targetX.coerceIn(-maxX, maxX)
        val desired = ((me.targetX - me.x) * 4f).coerceIn(-vehicle.steer, vehicle.steer)
        me.vx += (desired - me.vx) * min(1f, track.grip * 10f * dt)
        if (me.spin > 0f) {
            me.spin -= dt
            me.vx += (random.nextFloat() - 0.5f) * 40f * dt
        }
        me.x = (me.x + me.vx * dt).coerceIn(-maxX, maxX)
        me.dist += me.speed * dt

        if (track.dirt && me.speed > 6f && random.nextFloat() < 0.5f) {
            emitBehind(me, VehiclePainter.shade(track.roadColor, 0.85f), 0.6f)
        }
        if (vehicle.heavy && random.nextFloat() < 0.08f) emitBehind(me, Color.argb(150, 90, 90, 90), 0.5f)

        if (!me.finished && me.dist >= raceLength) {
            me.finished = true
            finishedCount++
            myPlace = finishedCount
            phase = Phase.FINISHED
            listener.onRaceFinished(myPlace, coins)
        }
    }

    private fun updateRival(r: Racer, dt: Float) {
        val top = vehicle.topSpeed
        val gap = r.dist - me.dist
        // Rubber-banding keeps the race close and exciting.
        val band = when {
            gap > 30f -> 0.8f
            gap > 12f -> 0.92f
            gap < -30f -> 1.2f
            gap < -12f -> 1.08f
            else -> 1f
        }
        val target = if (r.finished) top * 0.3f else top * r.pace * band
        r.speed = approach(r.speed, target, if (r.speed < target) vehicle.accel * 0.9f * dt else 10f * dt)

        if (dangerAhead(r.targetX, r.dist) || random.nextFloat() < 0.002f) {
            val lanes = listOf(-LANE_W, 0f, LANE_W).filter { !dangerAhead(it, r.dist) }
            lanes.minByOrNull { abs(it - r.x) + random.nextFloat() }?.let { r.targetX = it }
        }
        r.vx = ((r.targetX - r.x) * 3f).coerceIn(-4f, 4f)
        r.x += r.vx * dt
        r.dist += r.speed * dt
        if (!r.finished && r.dist >= raceLength) {
            r.finished = true
            finishedCount++
        }
    }

    private fun dangerAhead(x: Float, dist: Float) = obstacles.any {
        !it.gone && it.kind.hazard && abs(it.x - x) < 1.8f && it.dist - dist in 0f..28f
    }

    private fun checkCollisions(dt: Float) {
        val halfL = vehicle.lengthM / 2
        val halfW = vehicle.widthM / 2
        for (o in obstacles) {
            if (o.gone) continue
            val dd = o.dist - me.dist
            if (dd > 15f || dd < -15f) continue
            if (abs(dd) < halfL + o.kind.radiusM && abs(o.x - me.x) < halfW + o.kind.radiusM * 0.8f) hit(o)
        }
        for (r in rivals) {
            val dd = r.dist - me.dist
            val dx = r.x - me.x
            if (abs(dd) < (vehicle.lengthM + r.type.lengthM) * 0.45f && abs(dx) < (vehicle.widthM + r.type.widthM) * 0.45f) {
                // Can't drive through other racers: get nudged sideways and held behind them.
                me.vx = if (dx >= 0f) -3f else 3f
                if (dd > 0f) me.speed = min(me.speed, r.speed * 0.95f)
                shake = max(shake, 0.1f)
            }
        }
        if (me.spin > 0f) me.speed = max(0f, me.speed - 4f * dt)
    }

    private fun hit(o: Obstacle) {
        val first = !o.touched
        o.touched = true
        when (o.kind) {
            ObstacleKind.COIN -> {
                o.gone = true
                coins++
                engine.chime()
                burst(o.x, o.dist, Color.parseColor("#FFD600"), 10)
                if (coins % 10 == 0) listener.say("$coins stars!")
            }
            ObstacleKind.BOOST -> {
                if (first) listener.say("Zoom!")
                me.boost = 1.6f
            }
            ObstacleKind.CONE -> {
                o.gone = true
                bump(if (vehicle.heavy) 0.85f else 0.7f)
                burst(o.x, o.dist, Color.parseColor("#FF6D00"), 12)
            }
            ObstacleKind.ROCK -> if (vehicle.offroad) {
                if (first) shake = 0.15f
            } else {
                o.gone = true
                bump(if (vehicle.heavy) 0.75f else 0.55f)
                burst(o.x, o.dist, Color.parseColor("#757575"), 12)
            }
            ObstacleKind.MUD, ObstacleKind.SANDPIT -> {
                val sand = o.kind == ObstacleKind.SANDPIT
                if (!vehicle.offroad) me.slow = 0.9f
                if (random.nextFloat() < 0.4f) {
                    burst(me.x, me.dist, Color.parseColor(if (sand) "#E0C080" else "#5D4037"), 2)
                }
                if (first && !vehicle.offroad) listener.say(if (sand) "Sandy! Keep going!" else "Splat! Muddy!")
            }
            ObstacleKind.PUDDLE -> {
                me.slow = max(me.slow, 0.35f)
                if (random.nextFloat() < 0.5f) burst(me.x, me.dist, Color.parseColor("#64B5F6"), 3)
                if (first) listener.say("Splash!")
            }
            ObstacleKind.OIL, ObstacleKind.ICE -> if (first && me.spin <= 0f) {
                me.spin = 0.9f
                listener.say("Whoa! Slippery!")
            }
        }
    }

    private fun bump(keep: Float) {
        me.speed *= keep
        shake = 0.3f
        engine.bump()
        if (bumpTalkCooldown <= 0f) {
            listener.say("Bump!")
            bumpTalkCooldown = 4f
        }
    }

    private fun burst(x: Float, dist: Float, color: Int, count: Int) {
        repeat(count) {
            val a = random.nextFloat() * 6.283f
            val s = 2f + random.nextFloat() * 5f
            particles += Particle(x, dist, cos(a) * s, sin(a) * s + me.speed * 0.6f,
                0.6f, 0.6f, color, 0.25f + random.nextFloat() * 0.3f)
        }
    }

    private fun emitBehind(r: Racer, color: Int, size: Float) {
        particles += Particle(
            r.x + (random.nextFloat() - 0.5f) * r.type.widthM * 0.8f,
            r.dist - r.type.lengthM / 2, (random.nextFloat() - 0.5f) * 2f, r.speed * 0.5f,
            0.5f, 0.5f, color, size + random.nextFloat() * size,
        )
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.dist += p.vd * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
        if (particles.size > 300) particles.subList(0, particles.size - 300).clear()
    }

    // ---- input ----------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                if (hypot(e.getX(i) - hornX, e.getY(i) - hornY) < hornR * 1.2f) {
                    honk()
                } else {
                    steeringPointer = e.getPointerId(i)
                    steerTo(e.getX(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(steeringPointer)
                if (i >= 0) steerTo(e.getX(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == steeringPointer) steeringPointer = -1
            }
            MotionEvent.ACTION_CANCEL -> steeringPointer = -1
        }
        return true
    }

    private fun steerTo(px: Float) {
        me.targetX = (px - width / 2f) / ppm
    }

    private fun honk() {
        hornGlow = 0.3f
        if (vehicle.hasSiren) engine.siren() else engine.horn()
    }

    // ---- rendering ------------------------------------------------------------------------

    private fun sx(x: Float) = width / 2f + x * ppm
    private fun sy(dist: Float) = playerY - (dist - me.dist) * ppm

    private fun render(c: Canvas) {
        c.save()
        if (shake > 0f) {
            val s = shake * context.dp(24)
            c.translate((random.nextFloat() - 0.5f) * s, (random.nextFloat() - 0.5f) * s)
        }
        val fromDist = me.dist - (height - playerY) / ppm - 12f
        val toDist = me.dist + playerY / ppm + 12f
        drawGround(c, fromDist, toDist)
        drawScenery(c, fromDist, toDist)
        drawObstacles(c, fromDist, toDist)
        drawParticles(c)
        (rivals + me).sortedByDescending { it.dist }.forEach { drawRacer(c, it) }
        if (track.night) drawNightLights(c, fromDist, toDist)
        c.restore()
        drawHud(c)
    }

    private fun drawGround(c: Canvas, from: Float, to: Float) {
        c.drawColor(track.sideColor)
        // Mowing / field stripes on the verges.
        val band = 6f
        var d = (from / band).toInt() * band
        paint.color = VehiclePainter.shade(track.sideColor, 0.94f)
        while (d < to) {
            if (((d / band).toInt() and 1) == 0) c.drawRect(0f, sy(d + band), width.toFloat(), sy(d), paint)
            d += band
        }

        val left = sx(-ROAD_HALF)
        val right = sx(ROAD_HALF)
        paint.color = track.shoulderColor
        c.drawRect(left - 0.6f * ppm, 0f, right + 0.6f * ppm, height.toFloat(), paint)
        paint.color = track.roadColor
        c.drawRect(left, 0f, right, height.toFloat(), paint)

        if (track.dirt) {
            // Speckles of gravel / snow that scroll with the road.
            var seg = (from / 2f).toInt()
            while (seg * 2f < to) {
                for (k in 0 until 3) {
                    val h = hash(seg, k)
                    val x = -ROAD_HALF + (h and 0xFFFF) / 65535f * ROAD_HALF * 2
                    val dd = seg * 2f + ((h ushr 16) and 0xFF) / 255f * 2f
                    paint.color = VehiclePainter.shade(track.roadColor, if (k == 0) 1.12f else 0.86f)
                    c.drawCircle(sx(x), sy(dd), ppm * 0.12f, paint)
                }
                seg++
            }
        }

        if (track.curbs) {
            val curb = 2f
            var cd = (from / curb).toInt() * curb
            while (cd < to) {
                paint.color = if (((cd / curb).toInt() and 1) == 0) Color.parseColor("#E53935") else Color.WHITE
                c.drawRect(left - 0.6f * ppm, sy(cd + curb), left, sy(cd), paint)
                c.drawRect(right, sy(cd + curb), right + 0.6f * ppm, sy(cd), paint)
                cd += curb
            }
        }

        if (Color.alpha(track.laneLineColor) > 0) {
            paint.color = track.laneLineColor
            val lw = 0.15f * ppm
            c.drawRect(left + 0.2f * ppm, 0f, left + 0.2f * ppm + lw, height.toFloat(), paint)
            c.drawRect(right - 0.2f * ppm - lw, 0f, right - 0.2f * ppm, height.toFloat(), paint)
            var dash = (from / 8f).toInt() * 8f
            while (dash < to) {
                for (lx in floatArrayOf(-LANE_W / 2, LANE_W / 2)) {
                    c.drawRect(sx(lx) - lw / 2, sy(dash + 3f), sx(lx) + lw / 2, sy(dash), paint)
                }
                dash += 8f
            }
        }

        // Start line and chequered finish line with a banner.
        paint.color = Color.WHITE
        c.drawRect(left, sy(0.4f), right, sy(0f), paint)
        if (raceLength in from..to) {
            val cells = 12
            val cw = (right - left) / cells
            for (row in 0 until 2) for (i in 0 until cells) {
                paint.color = if ((i + row) % 2 == 0) Color.BLACK else Color.WHITE
                val top = sy(raceLength + (row + 1) * (cw / ppm))
                c.drawRect(left + i * cw, top, left + (i + 1) * cw, top + cw, paint)
            }
            val by = sy(raceLength + 6f)
            paint.color = Color.parseColor("#455A64")
            c.drawCircle(left - ppm, by, ppm * 0.4f, paint)
            c.drawCircle(right + ppm, by, ppm * 0.4f, paint)
            paint.color = Color.parseColor("#E53935")
            rect.set(left - ppm, by - ppm * 0.8f, right + ppm, by + ppm * 0.8f)
            c.drawRoundRect(rect, ppm * 0.3f, ppm * 0.3f, paint)
            text.color = Color.WHITE
            text.textSize = ppm * 1.2f
            c.drawText("FINISH", width / 2f, by + ppm * 0.42f, text)
        }
    }

    private fun drawScenery(c: Canvas, from: Float, to: Float) {
        val seg = 9f
        var i = (from / seg).toInt() - 1
        val sideRoom = (width / 2f) / ppm - ROAD_HALF - 0.6f
        while (i * seg < to + seg) {
            for (side in intArrayOf(-1, 1)) {
                val h = hash(i, side + 7)
                if ((h and 0xFF) > 190) continue
                val kind = track.scenery[((h ushr 8) and 0xFF) % track.scenery.size]
                val near = kind == Scenery.LAMP || kind == Scenery.BARRIER
                val offset = if (near) 0.9f else 1.8f + ((h ushr 16) and 0xFF) / 255f * max(1f, sideRoom)
                val x = side * (ROAD_HALF + 0.6f + offset)
                val d = i * seg + ((h ushr 24) and 0x7F) / 127f * 4f
                drawSceneryItem(c, kind, sx(x), sy(d), h)
            }
            i++
        }
    }

    private fun drawSceneryItem(c: Canvas, kind: Scenery, x: Float, y: Float, h: Int) {
        val m = ppm
        val shadow = Color.argb(60, 0, 0, 0)
        when (kind) {
            Scenery.TREE -> {
                circle(c, x + m * 0.4f, y + m * 0.4f, m * 1.7f, shadow)
                circle(c, x, y, m * 1.7f, Color.parseColor("#2E7D32"))
                circle(c, x - m * 0.45f, y - m * 0.45f, m * 1.0f, Color.parseColor("#43A047"))
            }
            Scenery.PINE -> {
                circle(c, x + m * 0.4f, y + m * 0.4f, m * 1.5f, shadow)
                circle(c, x, y, m * 1.5f, Color.parseColor("#1B5E20"))
                circle(c, x, y, m * 1.0f, Color.parseColor("#2E7D32"))
                circle(c, x, y, m * 0.5f, Color.parseColor("#388E3C"))
                circle(c, x - m * 0.6f, y - m * 0.5f, m * 0.3f, Color.WHITE)
                circle(c, x + m * 0.4f, y + m * 0.7f, m * 0.25f, Color.WHITE)
            }
            Scenery.BUSH -> {
                val g = Color.parseColor("#558B2F")
                circle(c, x - m * 0.5f, y, m * 0.7f, g)
                circle(c, x + m * 0.5f, y, m * 0.7f, g)
                circle(c, x, y - m * 0.4f, m * 0.7f, Color.parseColor("#689F38"))
            }
            Scenery.BUILDING -> {
                val roofs = intArrayOf(
                    Color.parseColor("#8D6E63"), Color.parseColor("#78909C"),
                    Color.parseColor("#A1887F"), Color.parseColor("#90A4AE"),
                )
                val roof = roofs[(h ushr 4) and 3]
                rect.set(x - m * 3f + m * 0.5f, y - m * 3.5f + m * 0.5f, x + m * 3f + m * 0.5f, y + m * 3.5f + m * 0.5f)
                paint.color = shadow
                c.drawRect(rect, paint)
                rect.set(x - m * 3f, y - m * 3.5f, x + m * 3f, y + m * 3.5f)
                paint.color = VehiclePainter.shade(roof, 0.8f)
                c.drawRect(rect, paint)
                rect.inset(m * 0.3f, m * 0.3f)
                paint.color = roof
                c.drawRect(rect, paint)
                if (!track.night) {
                    paint.color = Color.parseColor("#CFD8DC")
                    c.drawRect(x - m * 1.2f, y - m * 1.5f, x - m * 0.2f, y - m * 0.5f, paint)
                    c.drawRect(x + m * 0.5f, y + m * 0.8f, x + m * 1.5f, y + m * 1.8f, paint)
                }
            }
            Scenery.HOUSE -> {
                circle(c, x + m * 0.4f, y + m * 0.4f, m * 2.2f, shadow)
                paint.color = Color.parseColor("#C62828")
                c.drawRect(x - m * 2f, y - m * 2f, x, y + m * 2f, paint)
                paint.color = Color.parseColor("#8E0000")
                c.drawRect(x, y - m * 2f, x + m * 2f, y + m * 2f, paint)
                paint.color = Color.parseColor("#795548")
                c.drawRect(x + m * 0.6f, y - m * 1.4f, x + m * 1.2f, y - m * 0.8f, paint)
            }
            Scenery.LAMP -> {
                circle(c, x, y, m * 0.8f, Color.argb(70, 255, 235, 59))
                circle(c, x, y, m * 0.28f, Color.parseColor("#607D8B"))
            }
            Scenery.HAY -> {
                circle(c, x + m * 0.3f, y + m * 0.3f, m * 1.1f, shadow)
                circle(c, x, y, m * 1.1f, Color.parseColor("#FBC02D"))
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = m * 0.12f
                paint.color = Color.parseColor("#F57F17")
                c.drawCircle(x, y, m * 0.7f, paint)
                c.drawCircle(x, y, m * 0.35f, paint)
                paint.style = Paint.Style.FILL
            }
            Scenery.SAND -> {
                circle(c, x, y, m * 2f, Color.parseColor("#E6A15A"))
                circle(c, x - m * 0.3f, y - m * 0.3f, m * 1.3f, Color.parseColor("#F0B470"))
                circle(c, x - m * 0.5f, y - m * 0.5f, m * 0.6f, Color.parseColor("#F8CB8E"))
            }
            Scenery.BARRIER -> {
                for (k in 0 until 4) {
                    paint.color = if (k % 2 == 0) Color.parseColor("#FF6D00") else Color.WHITE
                    c.drawRect(x - m * 0.25f, y - m * 2f + k * m, x + m * 0.25f, y - m + k * m, paint)
                }
            }
            Scenery.CACTUS -> {
                circle(c, x + m * 0.3f, y + m * 0.3f, m * 0.7f, shadow)
                val g = Color.parseColor("#558B2F")
                circle(c, x - m * 0.75f, y + m * 0.2f, m * 0.35f, g)
                circle(c, x + m * 0.7f, y - m * 0.3f, m * 0.35f, g)
                circle(c, x, y, m * 0.6f, g)
                circle(c, x - m * 0.15f, y - m * 0.15f, m * 0.3f, Color.parseColor("#7CB342"))
                circle(c, x + m * 0.1f, y - m * 0.5f, m * 0.12f, Color.parseColor("#F06292"))
            }
            Scenery.DUNE -> {
                oval(c, x, y, m * 3f, m * 2f, Color.parseColor("#E9C37E"))
                oval(c, x - m * 0.6f, y - m * 0.4f, m * 2f, m * 1.2f, Color.parseColor("#F7DCA6"))
            }
            Scenery.BOULDERS -> {
                circle(c, x + m * 0.3f, y + m * 0.3f, m * 1.1f, shadow)
                circle(c, x, y, m * 1.1f, Color.parseColor("#A1887F"))
                circle(c, x + m * 1.1f, y + m * 0.6f, m * 0.6f, Color.parseColor("#8D6E63"))
                circle(c, x - m * 0.3f, y - m * 0.3f, m * 0.5f, Color.parseColor("#BCAAA4"))
            }
            Scenery.GRANDSTAND -> {
                paint.color = Color.parseColor("#78909C")
                c.drawRect(x - m * 2.5f, y - m * 4.5f, x + m * 2.5f, y + m * 4.5f, paint)
                val crowd = intArrayOf(
                    Color.parseColor("#E53935"), Color.parseColor("#FFEB3B"), Color.parseColor("#1E88E5"),
                    Color.WHITE, Color.parseColor("#43A047"), Color.parseColor("#FF9800"),
                )
                for (row in 0 until 4) for (seat in 0 until 8) {
                    val hh = hash(h + row, seat)
                    // Fans bounce up and down as they cheer.
                    val bob = sin(time * 6f + row + seat) * m * 0.08f
                    circle(c, x - m * 1.9f + row * m * 1.25f, y - m * 3.9f + seat * m * 1.1f + bob, m * 0.3f,
                        crowd[((hh ushr 3) and 0x7FFF) % crowd.size])
                }
            }
            Scenery.TYRES -> {
                for (k in 0 until 3) {
                    circle(c, x, y - m + k * m, m * 0.5f, Color.parseColor("#212121"))
                    circle(c, x, y - m + k * m, m * 0.22f, Color.parseColor("#616161"))
                }
            }
            Scenery.FLAG -> {
                val wave = sin(time * 5f + h) * m * 0.15f
                for (row in 0 until 2) for (col in 0 until 3) {
                    paint.color = if ((row + col) % 2 == 0) Color.BLACK else Color.WHITE
                    c.drawRect(x + col * m * 0.5f, y + row * m * 0.5f + wave * col,
                        x + (col + 1) * m * 0.5f, y + (row + 1) * m * 0.5f + wave * col, paint)
                }
                circle(c, x, y, m * 0.15f, Color.parseColor("#9E9E9E"))
            }
            Scenery.SNOWMAN -> {
                circle(c, x + m * 0.3f, y + m * 0.3f, m * 0.9f, shadow)
                circle(c, x, y, m * 0.9f, Color.WHITE)
                circle(c, x, y, m * 0.6f, Color.parseColor("#F5F5F5"))
                circle(c, x, y, m * 0.38f, Color.WHITE)
                circle(c, x, y - m * 0.32f, m * 0.1f, Color.parseColor("#FF6D00"))
            }
        }
    }

    private fun drawObstacles(c: Canvas, from: Float, to: Float) {
        val m = ppm
        for (o in obstacles) {
            if (o.gone || o.dist < from || o.dist > to) continue
            val x = sx(o.x)
            val y = sy(o.dist)
            when (o.kind) {
                ObstacleKind.CONE -> {
                    paint.color = Color.parseColor("#E65100")
                    c.drawRect(x - m * 0.5f, y - m * 0.5f, x + m * 0.5f, y + m * 0.5f, paint)
                    circle(c, x, y, m * 0.42f, Color.parseColor("#FF6D00"))
                    circle(c, x, y, m * 0.27f, Color.WHITE)
                    circle(c, x, y, m * 0.15f, Color.parseColor("#FF9100"))
                }
                ObstacleKind.PUDDLE -> {
                    oval(c, x, y, m * 1.3f, m * 1.0f, Color.argb(170, 100, 181, 246))
                    oval(c, x - m * 0.3f, y - m * 0.2f, m * 0.5f, m * 0.3f, Color.argb(120, 255, 255, 255))
                }
                ObstacleKind.OIL -> {
                    oval(c, x, y, m * 1.2f, m * 0.9f, Color.argb(220, 33, 33, 33))
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = m * 0.08f
                    paint.color = Color.argb(160, 186, 104, 200)
                    c.drawCircle(x, y, m * 0.5f, paint)
                    paint.style = Paint.Style.FILL
                }
                ObstacleKind.ROCK -> {
                    circle(c, x + m * 0.2f, y + m * 0.2f, m * 0.9f, Color.argb(60, 0, 0, 0))
                    circle(c, x, y, m * 0.9f, Color.parseColor("#757575"))
                    circle(c, x - m * 0.25f, y - m * 0.25f, m * 0.45f, Color.parseColor("#9E9E9E"))
                }
                ObstacleKind.MUD -> {
                    val mud = Color.parseColor("#5D4037")
                    oval(c, x, y, m * 1.4f, m * 1.1f, mud)
                    circle(c, x + m * 1.1f, y - m * 0.6f, m * 0.45f, mud)
                    circle(c, x - m * 1.0f, y + m * 0.7f, m * 0.4f, mud)
                    circle(c, x - m * 0.3f, y - m * 0.3f, m * 0.3f, Color.parseColor("#795548"))
                }
                ObstacleKind.SANDPIT -> {
                    oval(c, x, y, m * 1.6f, m * 1.2f, Color.parseColor("#E8C98A"))
                    oval(c, x + m * 0.2f, y + m * 0.15f, m * 1.1f, m * 0.8f, Color.parseColor("#D9B370"))
                    circle(c, x - m * 0.5f, y - m * 0.3f, m * 0.15f, Color.parseColor("#BF9A55"))
                    circle(c, x + m * 0.6f, y + m * 0.4f, m * 0.12f, Color.parseColor("#BF9A55"))
                }
                ObstacleKind.ICE -> {
                    oval(c, x, y, m * 1.5f, m * 1.1f, Color.argb(200, 179, 229, 252))
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = m * 0.08f
                    paint.color = Color.WHITE
                    c.drawLine(x - m * 0.8f, y - m * 0.3f, x + m * 0.2f, y - m * 0.6f, paint)
                    c.drawLine(x - m * 0.2f, y + m * 0.4f, x + m * 0.9f, y + m * 0.1f, paint)
                    paint.style = Paint.Style.FILL
                }
                ObstacleKind.BOOST -> {
                    val pulse = 0.75f + 0.25f * sin(time * 8f)
                    paint.color = Color.argb((255 * pulse).toInt(), 255, 171, 0)
                    rect.set(x - m * 1.3f, y - m * 1.4f, x + m * 1.3f, y + m * 1.4f)
                    c.drawRoundRect(rect, m * 0.4f, m * 0.4f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = m * 0.25f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = Color.WHITE
                    for (k in 0 until 3) {
                        val cy = y + m * 0.7f - k * m * 0.75f
                        path.reset()
                        path.moveTo(x - m * 0.7f, cy + m * 0.35f)
                        path.lineTo(x, cy - m * 0.3f)
                        path.lineTo(x + m * 0.7f, cy + m * 0.35f)
                        c.drawPath(path, paint)
                    }
                    paint.style = Paint.Style.FILL
                }
                ObstacleKind.COIN -> {
                    val spin = abs(cos(time * 4f + o.dist))
                    oval(c, x, y, m * 0.55f * max(0.15f, spin), m * 0.55f, Color.parseColor("#FFB300"))
                    oval(c, x, y, m * 0.4f * max(0.1f, spin), m * 0.4f, Color.parseColor("#FFD600"))
                    if (spin > 0.5f) {
                        text.color = Color.WHITE
                        text.textSize = m * 0.6f
                        c.drawText("★", x, y + m * 0.2f, text)
                    }
                }
            }
        }
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = Color.argb((Color.alpha(p.color) * a).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            c.drawCircle(sx(p.x), sy(p.dist), p.size * ppm * (1.5f - a * 0.5f), paint)
        }
    }

    private fun drawRacer(c: Canvas, r: Racer) {
        val y = sy(r.dist)
        val halfLen = r.type.lengthM * ppm
        if (y < -halfLen || y > height + halfLen) return
        c.save()
        c.translate(sx(r.x), y)
        var angle = Math.toDegrees(atan2(r.vx.toDouble(), max(r.speed, 4f).toDouble())).toFloat()
        if (r.spin > 0f) angle += sin(r.spin * 18f) * 25f
        c.rotate(angle)
        if (r === me && me.boost > 0f) {
            val flame = r.type.lengthM * ppm / 2
            path.reset()
            path.moveTo(-r.type.widthM * ppm * 0.3f, flame)
            path.lineTo(0f, flame + ppm * (1.5f + random.nextFloat()))
            path.lineTo(r.type.widthM * ppm * 0.3f, flame)
            path.close()
            paint.color = Color.parseColor("#FF6D00")
            c.drawPath(path, paint)
        }
        VehiclePainter.draw(c, r.type, r.type.widthM * ppm, r.type.lengthM * ppm, r.dist * ppm, time,
            if (r === me) playerColor else r.type.color)
        c.restore()
    }

    /** Darkens the world, then paints lamp glows, lit windows and headlight beams on top. */
    private fun drawNightLights(c: Canvas, from: Float, to: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 5, 10, 40)
        c.drawRect(-width.toFloat(), -height.toFloat(), width * 2f, height * 2f, paint)
        val m = ppm
        val seg = 9f
        var i = (from / seg).toInt() - 1
        val sideRoom = (width / 2f) / ppm - ROAD_HALF - 0.6f
        while (i * seg < to + seg) {
            for (side in intArrayOf(-1, 1)) {
                // Same placement maths as drawScenery so the lights line up with the objects.
                val h = hash(i, side + 7)
                if ((h and 0xFF) > 190) continue
                val kind = track.scenery[((h ushr 8) and 0xFF) % track.scenery.size]
                val near = kind == Scenery.LAMP || kind == Scenery.BARRIER
                val offset = if (near) 0.9f else 1.8f + ((h ushr 16) and 0xFF) / 255f * max(1f, sideRoom)
                val x = sx(side * (ROAD_HALF + 0.6f + offset))
                val y = sy(i * seg + ((h ushr 24) and 0x7F) / 127f * 4f)
                if (kind == Scenery.LAMP) {
                    circle(c, x, y, m * 3f, Color.argb(60, 255, 236, 140))
                    circle(c, x, y, m * 1.4f, Color.argb(90, 255, 241, 170))
                    circle(c, x, y, m * 0.3f, Color.parseColor("#FFF59D"))
                } else if (kind == Scenery.BUILDING) {
                    for (row in 0 until 4) for (col in 0 until 3) {
                        if ((hash(h, row * 3 + col) and 3) == 0) continue
                        paint.color = Color.argb(230, 255, 224, 130)
                        val wx = x - m * 2.2f + col * m * 1.6f
                        val wy = y - m * 2.8f + row * m * 1.6f
                        c.drawRect(wx, wy, wx + m * 0.9f, wy + m * 0.9f, paint)
                    }
                }
            }
            i++
        }
        for (r in rivals + me) {
            val y = sy(r.dist)
            if (y < -height || y > height * 2) continue
            c.save()
            c.translate(sx(r.x), y)
            c.rotate(Math.toDegrees(atan2(r.vx.toDouble(), max(r.speed, 4f).toDouble())).toFloat())
            val front = -r.type.lengthM * m / 2
            val hw = r.type.widthM * m / 2
            path.reset()
            path.moveTo(-hw * 0.8f, front)
            path.lineTo(hw * 0.8f, front)
            path.lineTo(hw * 2.2f, front - m * 10f)
            path.lineTo(-hw * 2.2f, front - m * 10f)
            path.close()
            paint.color = Color.argb(80, 255, 248, 200)
            c.drawPath(path, paint)
            circle(c, -hw * 0.6f, front, m * 0.25f, Color.parseColor("#FFFDE7"))
            circle(c, hw * 0.6f, front, m * 0.25f, Color.parseColor("#FFFDE7"))
            circle(c, -hw * 0.6f, -front, m * 0.35f, Color.argb(160, 255, 23, 68))
            circle(c, hw * 0.6f, -front, m * 0.35f, Color.argb(160, 255, 23, 68))
            c.restore()
        }
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val dp = context.resources.displayMetrics.density

        // Progress bar with a dot for each racer.
        val barLeft = 24 * dp
        val barRight = w - 48 * dp
        val barY = 26 * dp
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 8 * dp
        paint.color = Color.argb(130, 0, 0, 0)
        c.drawLine(barLeft, barY, barRight, barY, paint)
        paint.style = Paint.Style.FILL
        for (k in 0 until 4) for (row in 0 until 2) {
            paint.color = if ((k + row) % 2 == 0) Color.BLACK else Color.WHITE
            val cx = barRight + 10 * dp + k * 6 * dp
            c.drawRect(cx, barY - 6 * dp + row * 6 * dp, cx + 6 * dp, barY + row * 6 * dp, paint)
        }
        for (r in rivals + me) {
            val px = barLeft + (r.dist / raceLength).coerceIn(0f, 1f) * (barRight - barLeft)
            val rad = if (r === me) 11 * dp else 8 * dp
            circle(c, px, barY, rad + 2 * dp, if (r === me) Color.parseColor("#FFEB3B") else Color.WHITE)
            val dot = if (r === me) playerColor else r.type.color
            circle(c, px, barY, rad, if (dot == Color.WHITE) Color.parseColor("#90A4AE") else dot)
        }

        // Position and coins.
        val place = if (phase == Phase.FINISHED) myPlace else 1 + rivals.count { it.dist > me.dist }
        outlinedText(c, ordinal(place), 24 * dp + 30 * dp, 84 * dp, 44 * dp, Color.WHITE)
        circle(c, w - 70 * dp, 70 * dp, 15 * dp, Color.parseColor("#FFB300"))
        text.color = Color.WHITE
        text.textSize = 18 * dp
        c.drawText("★", w - 70 * dp, 76 * dp, text)
        outlinedText(c, "$coins", w - 34 * dp, 82 * dp, 30 * dp, Color.WHITE)

        // Speedometer.
        val gx = 70 * dp
        val gy = height - 70 * dp
        val gr = 54 * dp
        circle(c, gx, gy, gr, Color.argb(170, 20, 20, 30))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6 * dp
        paint.color = Color.WHITE
        rect.set(gx - gr * 0.78f, gy - gr * 0.78f, gx + gr * 0.78f, gy + gr * 0.78f)
        c.drawArc(rect, 150f, 240f, false, paint)
        paint.color = Color.parseColor("#FF1744")
        c.drawArc(rect, 330f, 60f, false, paint)
        val frac = (me.speed / (vehicle.topSpeed * 1.35f)).coerceIn(0f, 1f)
        val needle = Math.toRadians((150 + 240 * frac).toDouble())
        paint.strokeWidth = 4 * dp
        paint.color = Color.parseColor("#FF9100")
        c.drawLine(gx, gy, gx + (cos(needle) * gr * 0.7f).toFloat(), gy + (sin(needle) * gr * 0.7f).toFloat(), paint)
        paint.style = Paint.Style.FILL
        circle(c, gx, gy, 6 * dp, Color.WHITE)
        text.color = Color.WHITE
        text.textSize = 20 * dp
        c.drawText("${(me.speed * 3.6f).toInt()}", gx, gy + 30 * dp, text)
        text.textSize = 10 * dp
        c.drawText("km/h", gx, gy + 44 * dp, text)

        // Horn / siren button.
        val glow = hornGlow / 0.3f
        circle(c, hornX, hornY, hornR * (1f + 0.15f * glow), if (vehicle.hasSiren) Color.parseColor("#1565C0") else Color.parseColor("#FF6D00"))
        circle(c, hornX, hornY, hornR * 0.82f, Color.argb(60, 255, 255, 255))
        text.color = Color.WHITE
        text.textSize = if (vehicle.hasSiren) 36 * dp else 20 * dp
        c.drawText(if (vehicle.hasSiren) "🚨" else "BEEP!", hornX, hornY + text.textSize * 0.35f, text)

        // Countdown, GO!, steering tip and finish banner.
        if (phase == Phase.COUNTDOWN) {
            val n = ceil(countdown).toInt().coerceAtLeast(1)
            circle(c, w / 2, height * 0.4f, 70 * dp, Color.argb(200, 0, 0, 0))
            outlinedText(c, "$n", w / 2, height * 0.4f + 34 * dp, 96 * dp,
                when (n) { 3 -> Color.parseColor("#FF1744"); 2 -> Color.parseColor("#FFC400"); else -> Color.parseColor("#00E676") })
            val tip = if (tiltSteering) "Tilt your phone to steer ↔" else "Drag your finger to steer ↔"
            outlinedText(c, tip, w / 2, height * 0.58f, 22 * dp, Color.WHITE)
        } else if (goFlash > 0f) {
            outlinedText(c, "GO!", w / 2, height * 0.42f, 110 * dp * (1.4f - goFlash * 0.4f), Color.parseColor("#00E676"))
        }
        if (phase == Phase.FINISHED) {
            outlinedText(c, "FINISH!", w / 2, height * 0.35f, 64 * dp, Color.parseColor("#FFEB3B"))
        }
    }

    private fun outlinedText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        text.textSize = size
        text.style = Paint.Style.STROKE
        text.strokeWidth = size * 0.12f
        text.strokeJoin = Paint.Join.ROUND
        text.color = Color.argb(200, 0, 0, 0)
        c.drawText(s, x, y, text)
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(s, x, y, text)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(x, y, r, paint)
    }

    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(x - rx, y - ry, x + rx, y + ry)
        c.drawOval(rect, paint)
    }

    private companion object {
        const val LANE_W = 3.5f
        const val ROAD_HALF = 5.25f

        fun approach(value: Float, target: Float, step: Float) =
            if (value < target) min(target, value + step) else max(target, value - step)

        fun hash(a: Int, b: Int): Int {
            var x = a * 374761393 + b * 668265263
            x = (x xor (x ushr 13)) * 1274126177
            return x xor (x ushr 16)
        }

        fun ordinal(n: Int) = when (n) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            else -> "${n}th"
        }
    }
}
