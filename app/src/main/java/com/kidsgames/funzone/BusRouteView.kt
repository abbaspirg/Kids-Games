package com.kidsgames.funzone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drive the city bus along its route. The bus drives itself; the child holds
 * the big STOP pedal to brake at bus stops (passengers get off and on) and at
 * red traffic lights. Oncoming traffic passes in the other lane.
 *
 * World units are metres: x across the road (0 = centre line), dist along it.
 */
@SuppressLint("ViewConstructor")
class BusRouteView(
    context: Context,
    private val listener: Listener,
    private val busColor: Int,
) : View(context) {

    interface Listener {
        fun say(text: String)
        fun onRouteFinished(passengers: Int, stopsServed: Int, totalStops: Int)
    }

    private class Person(var x: Float, var dist: Float, val shirt: Int, val skin: Int) {
        var walking = false
        var leaving = false
        var alpha = 1f
    }

    private class Stop(val dist: Float) {
        val waiting = mutableListOf<Person>()
        var served = false
        var missed = false
        var announced = false
    }

    private enum class Light { GREEN, YELLOW, RED }

    private class TrafficLight(val dist: Float) {
        var state = Light.GREEN
        var timer = 0f
        var warned = false
        var passed = false
    }

    private class Car(val type: VehicleType, var dist: Float, val speed: Float)

    private enum class Phase { DRIVING, BOARDING, DONE }

    private val random = Random(System.nanoTime())
    private val bus = VehicleType.BUS
    private val engine = EngineSound(bus.enginePitch, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()

    private val shirts = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#1E88E5"), Color.parseColor("#43A047"),
        Color.parseColor("#FDD835"), Color.parseColor("#8E24AA"), Color.parseColor("#FB8C00"),
        Color.parseColor("#EC407A"), Color.parseColor("#00ACC1"),
    )
    private val skins = intArrayOf(
        Color.parseColor("#FFCC80"), Color.parseColor("#C68642"), Color.parseColor("#8D5524"),
        Color.parseColor("#F1C27D"), Color.parseColor("#E0AC69"),
    )

    private val stops = mutableListOf<Stop>()
    private val lights = mutableListOf<TrafficLight>()
    private val cars = mutableListOf<Car>()
    private val walkers = mutableListOf<Person>()
    private val routeLength: Float

    private var busDist = 0f
    private var speed = 0f
    private var braking = false
    private var pedalPointer = -1
    private var passengers = 0
    private var served = 0
    private var phase = Phase.DRIVING
    private var boardingStop: Stop? = null
    private var boardDelay = 0f
    private var nextCar = 2f
    private var time = 0f
    private var hornGlow = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var started = false

    // Screen mapping.
    private var ppm = 1f
    private var busY = 0f
    private var pedalX = 0f
    private var pedalY = 0f
    private var pedalR = 0f
    private var hornX = 0f
    private var hornY = 0f
    private var hornR = 0f

    init {
        var d = 70f
        repeat(TOTAL_STOPS) { i ->
            val stop = Stop(d)
            repeat(2 + random.nextInt(3)) { k ->
                stop.waiting += Person(5.2f + (k % 2) * 0.8f, d - 1.5f + k * 1.1f, shirts.random(random), skins.random(random))
            }
            stops += stop
            if (i < TOTAL_STOPS - 1) lights += TrafficLight(d + 45f + random.nextFloat() * 20f)
            d += 110f + random.nextFloat() * 30f
        }
        routeLength = d - 40f
    }

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        engine.start()
        if (!started) {
            started = true
            listener.say("Let's drive the bus! Hold the red stop button to stop at the bus stops.")
        }
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
        ppm = w / 17f
        busY = h * 0.66f
        pedalR = context.dp(56).toFloat()
        pedalX = w - pedalR - context.dp(14)
        pedalY = h - pedalR - context.dp(18)
        hornR = context.dp(36).toFloat()
        hornX = hornR + context.dp(14)
        hornY = h - hornR - context.dp(28)
    }

    // ---- loop -----------------------------------------------------------------------------

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
        hornGlow = max(0f, hornGlow - dt)

        val target = when {
            phase == Phase.BOARDING || phase == Phase.DONE -> 0f
            braking -> 0f
            else -> CRUISE
        }
        speed = if (speed < target) min(target, speed + 2.5f * dt) else max(target, speed - (if (braking) 7f else 3f) * dt)
        busDist += speed * dt
        engine.speedFraction = speed / CRUISE * 0.8f

        if (phase == Phase.DRIVING) {
            checkStops()
            checkLights()
            if (busDist >= routeLength) {
                phase = Phase.DONE
                listener.say("We made it to the bus depot!")
                listener.onRouteFinished(passengers, served, TOTAL_STOPS)
            }
        } else if (phase == Phase.BOARDING) {
            updateBoarding(dt)
        }
        lights.forEach { updateLightTimer(it, dt) }
        updateTraffic(dt)
        updateWalkers(dt)
    }

    private fun checkStops() {
        for (stop in stops) {
            if (stop.served || stop.missed) continue
            val gap = stop.dist - busDist
            if (!stop.announced && gap < 45f) {
                stop.announced = true
                listener.say("Bus stop coming! Get ready to stop!")
            }
            if (speed < 0.6f && gap in -4f..12f) {
                startBoarding(stop)
                return
            }
            if (gap < -4f) {
                stop.missed = true
                listener.say("Oops! We missed the bus stop. Let's stop at the next one!")
            }
        }
    }

    private fun startBoarding(stop: Stop) {
        phase = Phase.BOARDING
        boardingStop = stop
        boardDelay = 0.5f
        // A few riders get off first.
        val off = if (passengers == 0) 0 else random.nextInt(min(2, passengers) + 1)
        repeat(off) { k ->
            passengers--
            walkers += Person(DOOR_X, busDist + DOOR_OFFSET - k * 0.8f, shirts.random(random), skins.random(random)).apply {
                walking = true
                leaving = true
            }
        }
        listener.say(if (off > 0) "Ding ding! Doors open. Bye bye!" else "Ding ding! Doors open.")
    }

    private fun updateBoarding(dt: Float) {
        val stop = boardingStop ?: return
        if (walkers.any { it.leaving && it.x < 5f }) return
        boardDelay -= dt
        if (boardDelay > 0f) return
        if (walkers.none { !it.leaving }) {
            val next = stop.waiting.firstOrNull()
            if (next != null) {
                stop.waiting.remove(next)
                next.walking = true
                walkers += next
                boardDelay = 0.2f
            } else {
                stop.served = true
                served++
                phase = Phase.DRIVING
                boardingStop = null
                listener.say(if (braking) "All aboard! Let go of stop to drive." else "All aboard! Off we go!")
            }
        }
    }

    private fun updateWalkers(dt: Float) {
        val doorDist = busDist + DOOR_OFFSET
        val it = walkers.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (p.leaving) {
                // Step off to the pavement, then stroll away and fade.
                if (p.x < 5.4f) p.x += 2.4f * dt else {
                    p.dist -= 1.4f * dt
                    p.alpha -= dt * 0.5f
                }
                if (p.alpha <= 0f) it.remove()
            } else {
                val dx = DOOR_X - p.x
                val dd = doorDist - p.dist
                val d = hypot(dx, dd)
                val step = 2.6f * dt
                if (d <= step) {
                    it.remove()
                    passengers++
                    engine.chime()
                    listener.say(numberWord(passengers))
                    boardDelay = 0.35f
                } else {
                    p.x += dx / d * step
                    p.dist += dd / d * step
                }
            }
        }
    }

    private fun checkLights() {
        for (light in lights) {
            if (light.passed) continue
            val gap = light.dist - (busDist + bus.lengthM / 2)
            if (gap < 60f && light.state == Light.GREEN && light.timer == 0f && !light.warned) {
                light.state = Light.YELLOW
                light.timer = 1.6f
            }
            if (light.state == Light.RED && !light.warned && gap < 40f) {
                light.warned = true
                listener.say("Red light! Stop the bus!")
            }
            if (gap < 0f) {
                light.passed = true
                if (light.state == Light.RED) listener.say("Uh oh, that was a red light. Red means stop!")
            }
        }
        // Waiting at a red light makes it turn green.
        if (speed < 0.6f) {
            lights.firstOrNull { !it.passed && it.state == Light.RED && it.dist - busDist < 25f }?.let {
                if (it.timer > 2.5f) it.timer = 2.5f
            }
        }
    }

    private fun updateLightTimer(light: TrafficLight, dt: Float) {
        if (light.timer <= 0f) return
        light.timer -= dt
        if (light.timer > 0f) return
        when (light.state) {
            Light.YELLOW -> {
                light.state = Light.RED
                light.timer = 8f
            }
            Light.RED -> {
                light.state = Light.GREEN
                light.timer = -1f
                if (!light.passed) listener.say("Green light! Green means go!")
            }
            Light.GREEN -> Unit
        }
    }

    private fun updateTraffic(dt: Float) {
        nextCar -= dt
        if (nextCar <= 0f) {
            nextCar = 2.5f + random.nextFloat() * 3f
            val types = listOf(VehicleType.CAR, VehicleType.POLICE, VehicleType.TRUCK, VehicleType.MOTORBIKE,
                VehicleType.TRACTOR, VehicleType.FIRE_TRUCK, VehicleType.RACE_CAR)
            cars += Car(types.random(random), busDist + 45f, 9f + random.nextFloat() * 4f)
        }
        val it = cars.iterator()
        while (it.hasNext()) {
            val car = it.next()
            car.dist -= car.speed * dt
            if (car.dist < busDist - 30f) it.remove()
        }
    }

    // ---- input ----------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (hypot(x - hornX, y - hornY) < hornR * 1.3f) {
                    engine.horn()
                    hornGlow = 0.3f
                } else if (hypot(x - pedalX, y - pedalY) < pedalR * 1.3f || y > height * 0.5f) {
                    pedalPointer = e.getPointerId(i)
                    braking = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == pedalPointer) {
                    pedalPointer = -1
                    braking = false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pedalPointer = -1
                braking = false
            }
        }
        return true
    }

    // ---- drawing --------------------------------------------------------------------------

    private fun sx(x: Float) = width / 2f + x * ppm
    private fun sy(dist: Float) = busY - (dist - busDist) * ppm

    private fun render(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val from = busDist - (h - busY) / ppm - 15f
        val to = busDist + busY / ppm + 15f

        c.drawColor(Color.parseColor("#7CB342"))
        // Pavements and road.
        paint.color = Color.parseColor("#BDBDBD")
        c.drawRect(sx(-7f), 0f, sx(7f), h, paint)
        paint.color = Color.parseColor("#9E9E9E")
        var slab = (from / 2f).toInt() * 2f
        while (slab < to) {
            c.drawRect(sx(-7f), sy(slab), sx(-3.6f), sy(slab) + ppm * 0.06f, paint)
            c.drawRect(sx(3.6f), sy(slab), sx(7f), sy(slab) + ppm * 0.06f, paint)
            slab += 2f
        }
        paint.color = Color.parseColor("#546E7A")
        c.drawRect(sx(-3.6f), 0f, sx(3.6f), h, paint)
        paint.color = Color.WHITE
        c.drawRect(sx(-3.45f), 0f, sx(-3.3f), h, paint)
        c.drawRect(sx(3.3f), 0f, sx(3.45f), h, paint)
        paint.color = Color.parseColor("#FFD600")
        var dash = (from / 6f).toInt() * 6f
        while (dash < to) {
            c.drawRect(sx(-0.08f), sy(dash + 3f), sx(0.08f), sy(dash), paint)
            dash += 6f
        }

        drawBuildings(c, from, to)
        stops.forEach { if (it.dist in from..to) drawStop(c, it) }
        lights.forEach { if (it.dist in from..to) drawLight(c, it) }
        drawDepot(c, from, to)

        for (car in cars) {
            c.save()
            c.translate(sx(-1.75f), sy(car.dist))
            c.rotate(180f)
            VehiclePainter.draw(c, car.type, car.type.widthM * ppm, car.type.lengthM * ppm, car.dist * ppm, time)
            c.restore()
        }
        walkers.forEach { drawPerson(c, it) }

        c.save()
        c.translate(sx(1.75f), sy(busDist))
        VehiclePainter.draw(c, bus, bus.widthM * ppm, bus.lengthM * ppm, busDist * ppm, time, busColor)
        if (braking || phase == Phase.BOARDING) {
            paint.color = Color.argb(200, 255, 23, 68)
            c.drawCircle(-bus.widthM * ppm * 0.35f, bus.lengthM * ppm / 2, ppm * 0.35f, paint)
            c.drawCircle(bus.widthM * ppm * 0.35f, bus.lengthM * ppm / 2, ppm * 0.35f, paint)
        }
        if (phase == Phase.BOARDING) {
            // Open door on the pavement side.
            paint.color = Color.parseColor("#263238")
            c.drawRect(bus.widthM * ppm * 0.4f, -bus.lengthM * ppm * 0.42f, bus.widthM * ppm * 0.55f,
                -bus.lengthM * ppm * 0.3f, paint)
        }
        c.restore()

        drawHud(c)
    }

    private fun drawBuildings(c: Canvas, from: Float, to: Float) {
        val seg = 12f
        var i = (from / seg).toInt() - 1
        val roofs = intArrayOf(
            Color.parseColor("#8D6E63"), Color.parseColor("#78909C"), Color.parseColor("#A1887F"),
            Color.parseColor("#B0BEC5"), Color.parseColor("#D7CCC8"),
        )
        while (i * seg < to + seg) {
            for (side in intArrayOf(-1, 1)) {
                val h = (i * 73856093) xor (side * 19349663)
                val y = sy(i * seg + seg / 2)
                val left = if (side < 0) 0f else sx(7.4f)
                val right = if (side < 0) sx(-7.4f) else width.toFloat()
                if ((h and 3) == 0) {
                    paint.color = Color.parseColor("#2E7D32")
                    c.drawCircle((left + right) / 2, y, ppm * 2f, paint)
                    paint.color = Color.parseColor("#43A047")
                    c.drawCircle((left + right) / 2 - ppm * 0.5f, y - ppm * 0.5f, ppm * 1.2f, paint)
                } else {
                    paint.color = roofs[((h ushr 2) and 0x7FFF) % roofs.size]
                    c.drawRect(left, y - seg * ppm * 0.45f, right, y + seg * ppm * 0.45f, paint)
                    paint.color = Color.argb(40, 0, 0, 0)
                    c.drawRect(left, y - seg * ppm * 0.45f, right, y - seg * ppm * 0.4f, paint)
                }
            }
            i++
        }
    }

    private fun drawStop(c: Canvas, stop: Stop) {
        val y = sy(stop.dist)
        // Yellow bus-stop box painted in the bus lane.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ppm * 0.15f
        paint.color = Color.parseColor("#FFD600")
        rect.set(sx(0.3f), sy(stop.dist + 7f), sx(3.2f), sy(stop.dist - 6f))
        c.drawRect(rect, paint)
        paint.style = Paint.Style.FILL
        text.color = Color.parseColor("#FFD600")
        text.textSize = ppm * 0.9f
        c.save()
        c.translate(sx(1.75f), y)
        c.rotate(-90f)
        c.drawText("BUS STOP", 0f, ppm * 0.3f, text)
        c.restore()
        // Shelter and sign.
        paint.color = Color.parseColor("#1565C0")
        rect.set(sx(5.8f), y - ppm * 2.5f, sx(7f), y + ppm * 2.5f)
        c.drawRoundRect(rect, ppm * 0.2f, ppm * 0.2f, paint)
        paint.color = Color.parseColor("#64B5F6")
        rect.inset(ppm * 0.15f, ppm * 0.15f)
        c.drawRect(rect, paint)
        paint.color = Color.parseColor("#E53935")
        c.drawCircle(sx(4.2f), y - ppm * 3f, ppm * 0.5f, paint)
        text.color = Color.WHITE
        text.textSize = ppm * 0.45f
        c.drawText("BUS", sx(4.2f), y - ppm * 2.85f, text)
        stop.waiting.forEach { drawPerson(c, it, waving = stop.missed) }
    }

    private fun drawLight(c: Canvas, light: TrafficLight) {
        val y = sy(light.dist)
        paint.color = Color.WHITE
        c.drawRect(sx(0.2f), y - ppm * 0.25f, sx(3.3f), y, paint)
        paint.color = Color.parseColor("#263238")
        rect.set(sx(3.9f), y - ppm * 2.8f, sx(5.1f), y + ppm * 0.2f)
        c.drawRoundRect(rect, ppm * 0.3f, ppm * 0.3f, paint)
        val lamps = listOf(Light.RED to Color.parseColor("#FF1744"), Light.YELLOW to Color.parseColor("#FFEA00"),
            Light.GREEN to Color.parseColor("#00E676"))
        lamps.forEachIndexed { k, (state, on) ->
            val ly = y - ppm * 2.3f + k * ppm * 1f
            if (light.state == state) {
                paint.color = Color.argb(90, Color.red(on), Color.green(on), Color.blue(on))
                c.drawCircle(sx(4.5f), ly, ppm * 0.75f, paint)
                paint.color = on
            } else {
                paint.color = Color.parseColor("#455A64")
            }
            c.drawCircle(sx(4.5f), ly, ppm * 0.38f, paint)
        }
    }

    private fun drawDepot(c: Canvas, from: Float, to: Float) {
        val d = routeLength + 10f
        if (d - 12f > to || d + 12f < from) return
        val y = sy(d)
        paint.color = Color.parseColor("#37474F")
        c.drawRect(sx(-3.6f), y - ppm * 10f, sx(7f), y + ppm * 8f, paint)
        paint.color = Color.parseColor("#FFB300")
        for (k in 0 until 3) c.drawRect(sx(0.3f + k * 2.2f), y - ppm * 6f, sx(1.9f + k * 2.2f), y + ppm * 6f, paint)
        text.color = Color.WHITE
        text.textSize = ppm * 1.6f
        c.drawText("BUS DEPOT", sx(1.7f), y - ppm * 7.5f, text)
    }

    private fun drawPerson(c: Canvas, p: Person, waving: Boolean = false) {
        val x = sx(p.x)
        val y = sy(p.dist)
        val a = (p.alpha.coerceIn(0f, 1f) * 255).toInt()
        val bob = if (p.walking) sin(time * 14f + p.dist) * ppm * 0.06f else 0f
        paint.color = Color.argb(a / 4, 0, 0, 0)
        c.drawCircle(x + ppm * 0.1f, y + ppm * 0.1f, ppm * 0.42f, paint)
        paint.color = withAlpha(p.shirt, a)
        rect.set(x - ppm * 0.4f, y - ppm * 0.25f + bob, x + ppm * 0.4f, y + ppm * 0.25f + bob)
        c.drawOval(rect, paint)
        if (waving) {
            val wave = sin(time * 10f) * ppm * 0.2f
            paint.strokeWidth = ppm * 0.14f
            paint.style = Paint.Style.STROKE
            c.drawLine(x - ppm * 0.35f, y, x - ppm * 0.6f - wave, y - ppm * 0.5f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = withAlpha(p.skin, a)
        c.drawCircle(x, y + bob, ppm * 0.2f, paint)
        paint.color = withAlpha(Color.parseColor("#3E2723"), a)
        c.drawCircle(x, y - ppm * 0.06f + bob, ppm * 0.13f, paint)
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val dp = context.resources.displayMetrics.density
        val nextStop = stops.indexOfFirst { !it.served && !it.missed }
        val stopLabel = if (nextStop >= 0) "🚏 Stop ${nextStop + 1} of $TOTAL_STOPS" else "🏁 Depot"
        panelText(c, stopLabel, 16 * dp, 40 * dp, Paint.Align.LEFT)
        panelText(c, "👥 $passengers", w - 16 * dp, 40 * dp, Paint.Align.RIGHT)
        panelText(c, "${(speed * 3.6f).toInt()} km/h", w / 2, 84 * dp, Paint.Align.CENTER)

        // STOP NOW prompt when the bus is at a stop or a red light.
        val atStop = stops.any { !it.served && !it.missed && it.dist - busDist in -4f..14f }
        val atRed = lights.any { !it.passed && it.state != Light.GREEN && it.dist - busDist in 5f..30f }
        if (phase == Phase.DRIVING && speed > 0.6f && (atStop || atRed)) {
            val pulse = 1f + 0.08f * sin(time * 10f)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 40 * dp * pulse
            text.style = Paint.Style.STROKE
            text.strokeWidth = 6 * dp
            text.color = Color.BLACK
            c.drawText("STOP NOW!", w / 2, height * 0.3f, text)
            text.style = Paint.Style.FILL
            text.color = Color.parseColor("#FF1744")
            c.drawText("STOP NOW!", w / 2, height * 0.3f, text)
        }

        // Brake pedal.
        val pressed = braking
        paint.color = if (pressed) Color.parseColor("#B71C1C") else Color.parseColor("#E53935")
        c.drawCircle(pedalX, pedalY, pedalR * (if (pressed) 0.94f else 1f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5 * dp
        paint.color = Color.WHITE
        c.drawCircle(pedalX, pedalY, pedalR * 0.82f, paint)
        paint.style = Paint.Style.FILL
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 26 * dp
        c.drawText("STOP", pedalX, pedalY + 9 * dp, text)
        text.textSize = 13 * dp
        c.drawText("hold", pedalX, pedalY + 28 * dp, text)

        // Horn.
        paint.color = Color.parseColor("#FF6D00")
        c.drawCircle(hornX, hornY, hornR * (1f + hornGlow * 0.5f), paint)
        text.textSize = 16 * dp
        c.drawText("BEEP!", hornX, hornY + 6 * dp, text)
    }

    private fun panelText(c: Canvas, s: String, x: Float, y: Float, align: Paint.Align) {
        val dp = context.resources.displayMetrics.density
        text.textAlign = align
        text.textSize = 22 * dp
        val tw = text.measureText(s)
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.RIGHT -> x - tw
            else -> x - tw / 2
        }
        paint.color = Color.argb(170, 0, 0, 0)
        rect.set(left - 10 * dp, y - 26 * dp, left + tw + 10 * dp, y + 8 * dp)
        c.drawRoundRect(rect, 12 * dp, 12 * dp, paint)
        text.color = Color.WHITE
        c.drawText(s, x, y, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun numberWord(n: Int) = listOf(
        "zero", "One!", "Two!", "Three!", "Four!", "Five!", "Six!", "Seven!", "Eight!", "Nine!", "Ten!",
    ).getOrElse(n) { "$n!" }

    private companion object {
        const val TOTAL_STOPS = 6
        const val CRUISE = 11f
        const val DOOR_X = 3.05f
        const val DOOR_OFFSET = 4f
    }
}
