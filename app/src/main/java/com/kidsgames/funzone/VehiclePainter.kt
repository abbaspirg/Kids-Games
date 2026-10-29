package com.kidsgames.funzone

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Draws vehicles from above, facing up, centred on the canvas origin.
 * [w] and [l] are the vehicle's width and length in pixels.
 */
object VehiclePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val TIRE = Color.parseColor("#212121")
    private val TREAD = Color.parseColor("#424242")
    private val GLASS = Color.parseColor("#90CAF9")
    private val GLASS_DARK = Color.parseColor("#546E7A")
    private val SHADOW = Color.argb(70, 0, 0, 0)
    private val HEADLIGHT = Color.parseColor("#FFF9C4")
    private val TAILLIGHT = Color.parseColor("#FF1744")
    private val STEEL = Color.parseColor("#9E9E9E")
    private val SILVER = Color.parseColor("#E0E0E0")
    private val DARK = Color.parseColor("#263238")

    /**
     * @param turn how far the wheels have rolled, in pixels (animates the tyre treads).
     * @param time seconds, used for blinking lights.
     * @param color body paint; defaults to the vehicle's standard colour.
     */
    fun draw(c: Canvas, type: VehicleType, w: Float, l: Float, turn: Float, time: Float, color: Int = type.color) {
        rr(c, -w / 2 + w * 0.1f, -l / 2 + l * 0.05f, w / 2 + w * 0.1f, l / 2 + l * 0.05f, w * 0.25f, SHADOW)
        when (type) {
            VehicleType.RACE_CAR -> raceCar(c, w, l, turn, color)
            VehicleType.CAR -> car(c, w, l, turn, color, police = false, time)
            VehicleType.POLICE -> car(c, w, l, turn, color, police = true, time)
            VehicleType.MOTORBIKE -> motorbike(c, w, l, turn, color)
            VehicleType.BUS -> bus(c, w, l, turn, color)
            VehicleType.TRUCK -> truck(c, w, l, turn, color)
            VehicleType.FIRE_TRUCK -> fireTruck(c, w, l, turn, color, time)
            VehicleType.JCB -> jcb(c, w, l, turn, color, time)
            VehicleType.TRACTOR -> tractor(c, w, l, turn, color)
            VehicleType.MONSTER_TRUCK -> monsterTruck(c, w, l, turn, color)
        }
    }

    // ---- vehicles -------------------------------------------------------------------------

    private fun car(c: Canvas, w: Float, l: Float, turn: Float, color: Int, police: Boolean, time: Float) {
        val hw = w / 2
        val hl = l / 2
        val ww = w * 0.2f
        val wl = l * 0.18f
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.3f, 0.3f)) {
            wheel(c, sx * (hw - ww * 0.35f), y * l, ww, wl, turn)
        }
        rr(c, -hw * 0.92f, -hl, hw * 0.92f, hl, w * 0.32f, color)
        if (police) {
            // Black bonnet and boot, white middle.
            c.save()
            c.clipRect(-hw, -hl, hw, -l * 0.24f)
            rr(c, -hw * 0.92f, -hl, hw * 0.92f, hl, w * 0.32f, DARK)
            c.restore()
            c.save()
            c.clipRect(-hw, l * 0.34f, hw, hl)
            rr(c, -hw * 0.92f, -hl, hw * 0.92f, hl, w * 0.32f, DARK)
            c.restore()
        } else {
            line(c, 0f, -hl * 0.85f, 0f, -l * 0.24f, w * 0.05f, shade(color, 1.25f))
        }
        quad(c, -hw * 0.72f, -l * 0.2f, hw * 0.72f, -l * 0.2f, hw * 0.62f, -l * 0.05f, -hw * 0.62f, -l * 0.05f, GLASS)
        rr(c, -hw * 0.64f, -l * 0.05f, hw * 0.64f, l * 0.2f, w * 0.12f, if (police) Color.WHITE else shade(color, 0.85f))
        quad(c, -hw * 0.62f, l * 0.2f, hw * 0.62f, l * 0.2f, hw * 0.7f, l * 0.32f, -hw * 0.7f, l * 0.32f, GLASS_DARK)
        circle(c, -hw * 0.6f, -hl + l * 0.05f, w * 0.09f, HEADLIGHT)
        circle(c, hw * 0.6f, -hl + l * 0.05f, w * 0.09f, HEADLIGHT)
        rr(c, -hw * 0.8f, hl - l * 0.05f, -hw * 0.4f, hl - l * 0.015f, w * 0.03f, TAILLIGHT)
        rr(c, hw * 0.4f, hl - l * 0.05f, hw * 0.8f, hl - l * 0.015f, w * 0.03f, TAILLIGHT)
        if (police) {
            val blink = (time * 4).toInt() % 2 == 0
            rr(c, -hw * 0.55f, l * 0.03f, 0f, l * 0.1f, w * 0.04f, if (blink) Color.RED else Color.parseColor("#7F0000"))
            rr(c, 0f, l * 0.03f, hw * 0.55f, l * 0.1f, w * 0.04f, if (blink) Color.parseColor("#0D47A1") else Color.parseColor("#2979FF"))
        }
    }

    private fun raceCar(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        val hw = w / 2
        val hl = l / 2
        val ww = w * 0.24f
        val wl = l * 0.17f
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.28f, 0.3f)) {
            wheel(c, sx * (hw - ww / 2), y * l, ww, wl, turn)
        }
        val wing = shade(color, 0.6f)
        rr(c, -hw * 0.95f, -hl, hw * 0.95f, -hl + l * 0.07f, w * 0.04f, wing)
        rr(c, -hw, l * 0.39f, hw, hl, w * 0.04f, wing)
        path.reset()
        path.moveTo(-w * 0.1f, -l * 0.45f)
        path.lineTo(w * 0.1f, -l * 0.45f)
        path.lineTo(w * 0.3f, -l * 0.05f)
        path.lineTo(w * 0.32f, l * 0.35f)
        path.lineTo(w * 0.2f, l * 0.42f)
        path.lineTo(-w * 0.2f, l * 0.42f)
        path.lineTo(-w * 0.32f, l * 0.35f)
        path.lineTo(-w * 0.3f, -l * 0.05f)
        path.close()
        fill(c, color)
        line(c, 0f, -l * 0.43f, 0f, l * 0.4f, w * 0.07f, Color.WHITE)
        rr(c, -w * 0.14f, -l * 0.03f, w * 0.14f, l * 0.18f, w * 0.1f, DARK)
        circle(c, 0f, l * 0.05f, w * 0.1f, Color.parseColor("#FFEB3B"))
        circle(c, 0f, l * 0.25f, w * 0.1f, Color.WHITE)
    }

    private fun motorbike(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        wheel(c, 0f, l * 0.33f, w * 0.36f, l * 0.3f, turn)
        wheel(c, 0f, -l * 0.33f, w * 0.32f, l * 0.28f, turn)
        rr(c, -w * 0.25f, -l * 0.25f, w * 0.25f, l * 0.3f, w * 0.2f, color)
        line(c, -w * 0.5f, -l * 0.2f, w * 0.5f, -l * 0.2f, w * 0.1f, STEEL)
        circle(c, 0f, -l * 0.27f, w * 0.12f, HEADLIGHT)
        // Rider: jacket, arms on the handlebars, helmet.
        val jacket = Color.parseColor("#E53935")
        line(c, -w * 0.36f, -l * 0.02f, -w * 0.46f, -l * 0.19f, w * 0.15f, jacket)
        line(c, w * 0.36f, -l * 0.02f, w * 0.46f, -l * 0.19f, w * 0.15f, jacket)
        rr(c, -w * 0.45f, -l * 0.08f, w * 0.45f, l * 0.16f, w * 0.3f, jacket)
        circle(c, 0f, -l * 0.02f, w * 0.28f, Color.WHITE)
        line(c, 0f, -l * 0.12f, 0f, l * 0.08f, w * 0.1f, Color.parseColor("#1E88E5"))
    }

    private fun bus(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        val hw = w / 2
        val hl = l / 2
        val ww = w * 0.16f
        val wl = l * 0.09f
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.32f, 0.3f)) {
            wheel(c, sx * (hw - ww * 0.3f), y * l, ww, wl, turn)
        }
        rr(c, -hw * 0.95f, -hl, hw * 0.95f, hl, w * 0.12f, color)
        rr(c, -hw * 0.75f, -hl + l * 0.06f, hw * 0.75f, hl - l * 0.03f, w * 0.08f, shade(color, 1.12f))
        val windows = 6
        val top = -l * 0.4f
        val seg = (l * 0.8f) / windows
        for (i in 0 until windows) {
            val y = top + i * seg
            rr(c, -hw * 0.93f, y, -hw * 0.93f + w * 0.1f, y + seg * 0.82f, w * 0.02f, GLASS)
            rr(c, hw * 0.93f - w * 0.1f, y, hw * 0.93f, y + seg * 0.82f, w * 0.02f, GLASS)
        }
        rr(c, -hw * 0.8f, -hl + l * 0.01f, hw * 0.8f, -hl + l * 0.05f, w * 0.03f, GLASS)
        rr(c, -hw * 0.35f, -l * 0.1f, hw * 0.35f, l * 0.08f, w * 0.05f, SILVER)
        for (i in 1..4) {
            val y = -l * 0.1f + i * l * 0.036f
            line(c, -hw * 0.25f, y, hw * 0.25f, y, w * 0.02f, STEEL)
        }
        circle(c, -hw * 0.65f, -hl + l * 0.015f, w * 0.07f, HEADLIGHT)
        circle(c, hw * 0.65f, -hl + l * 0.015f, w * 0.07f, HEADLIGHT)
        rr(c, -hw * 0.85f, hl - l * 0.015f, -hw * 0.5f, hl, w * 0.02f, TAILLIGHT)
        rr(c, hw * 0.5f, hl - l * 0.015f, hw * 0.85f, hl, w * 0.02f, TAILLIGHT)
    }

    private fun truck(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        val hw = w / 2
        val hl = l / 2
        val ww = w * 0.17f
        val wl = l * 0.09f
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.33f, 0.22f, 0.36f)) {
            wheel(c, sx * (hw - ww * 0.3f), y * l, ww, wl, turn)
        }
        rr(c, -hw * 0.96f, -l * 0.22f, hw * 0.96f, hl, w * 0.05f, Color.parseColor("#ECEFF1"))
        for (i in 1..6) {
            val y = -l * 0.22f + i * l * 0.1f
            line(c, -hw * 0.9f, y, hw * 0.9f, y, w * 0.02f, SILVER)
        }
        rr(c, -hw * 0.96f, l * 0.04f, hw * 0.96f, l * 0.12f, 0f, color)
        rr(c, -hw * 0.92f, -hl, hw * 0.92f, -l * 0.25f, w * 0.15f, color)
        rr(c, -hw * 0.75f, -hl + l * 0.02f, hw * 0.75f, -hl + l * 0.07f, w * 0.03f, GLASS)
        rr(c, -hw * 0.7f, -l * 0.32f, hw * 0.7f, -l * 0.26f, w * 0.05f, shade(color, 0.75f))
        circle(c, -hw * 0.65f, -hl + l * 0.012f, w * 0.07f, HEADLIGHT)
        circle(c, hw * 0.65f, -hl + l * 0.012f, w * 0.07f, HEADLIGHT)
    }

    private fun fireTruck(c: Canvas, w: Float, l: Float, turn: Float, color: Int, time: Float) {
        val hw = w / 2
        val hl = l / 2
        val ww = w * 0.17f
        val wl = l * 0.1f
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.32f, 0.22f, 0.35f)) {
            wheel(c, sx * (hw - ww * 0.3f), y * l, ww, wl, turn)
        }
        rr(c, -hw * 0.95f, -hl, hw * 0.95f, hl, w * 0.1f, color)
        rr(c, -hw * 0.95f, -l * 0.25f, -hw * 0.8f, l * 0.47f, 0f, Color.WHITE)
        rr(c, hw * 0.8f, -l * 0.25f, hw * 0.95f, l * 0.47f, 0f, Color.WHITE)
        rr(c, -hw * 0.78f, -hl + l * 0.015f, hw * 0.78f, -hl + l * 0.07f, w * 0.03f, GLASS)
        val blink = (time * 5).toInt() % 2 == 0
        rr(c, -hw * 0.6f, -hl + l * 0.085f, 0f, -hl + l * 0.12f, w * 0.03f, if (blink) Color.RED else Color.parseColor("#FFCDD2"))
        rr(c, 0f, -hl + l * 0.085f, hw * 0.6f, -hl + l * 0.12f, w * 0.03f, if (blink) Color.parseColor("#2979FF") else Color.parseColor("#BBDEFB"))
        // Ladder on the roof.
        line(c, -hw * 0.35f, -l * 0.28f, -hw * 0.35f, l * 0.48f, w * 0.05f, SILVER)
        line(c, hw * 0.35f, -l * 0.28f, hw * 0.35f, l * 0.48f, w * 0.05f, SILVER)
        var y = -l * 0.26f
        while (y < l * 0.47f) {
            line(c, -hw * 0.35f, y, hw * 0.35f, y, w * 0.035f, SILVER)
            y += l * 0.06f
        }
        circle(c, 0f, l * 0.4f, w * 0.12f, Color.parseColor("#FFEB3B"))
    }

    private fun jcb(c: Canvas, w: Float, l: Float, turn: Float, color: Int, time: Float) {
        val hw = w / 2
        val hl = l / 2
        val arm = shade(color, 0.82f)
        // Big rear wheels, smaller front ones.
        for (sx in floatArrayOf(-1f, 1f)) {
            wheel(c, sx * (hw - w * 0.12f), l * 0.12f, w * 0.24f, l * 0.2f, turn)
            wheel(c, sx * (hw - w * 0.14f), -l * 0.22f, w * 0.2f, l * 0.15f, turn)
        }
        // Back-hoe stabiliser legs.
        rr(c, -hw * 0.95f, l * 0.28f, -hw * 0.6f, l * 0.34f, w * 0.02f, DARK)
        rr(c, hw * 0.6f, l * 0.28f, hw * 0.95f, l * 0.34f, w * 0.02f, DARK)
        rr(c, -hw * 0.72f, -l * 0.35f, hw * 0.72f, l * 0.3f, w * 0.08f, color)
        rr(c, -hw * 0.5f, -l * 0.35f, hw * 0.5f, -l * 0.08f, w * 0.08f, shade(color, 1.08f))
        for (i in 0..3) {
            val y = -l * 0.33f + i * l * 0.025f
            line(c, -hw * 0.3f, y, hw * 0.3f, y, w * 0.025f, DARK)
        }
        // Front loader arms and bucket with teeth.
        line(c, -hw * 0.66f, -l * 0.08f, -hw * 0.66f, -l * 0.42f, w * 0.09f, arm)
        line(c, hw * 0.66f, -l * 0.08f, hw * 0.66f, -l * 0.42f, w * 0.09f, arm)
        rr(c, -hw, -hl + l * 0.03f, hw, -l * 0.4f, w * 0.04f, STEEL)
        line(c, -hw * 0.9f, -l * 0.42f, hw * 0.9f, -l * 0.42f, w * 0.03f, Color.parseColor("#757575"))
        var x = -hw * 0.85f
        while (x <= hw * 0.86f) {
            rr(c, x - w * 0.025f, -hl, x + w * 0.025f, -hl + l * 0.035f, w * 0.01f, Color.parseColor("#616161"))
            x += w * 0.17f
        }
        // Cab: dark frame, glass front, yellow roof, flashing beacon.
        rr(c, -hw * 0.56f, -l * 0.06f, hw * 0.56f, l * 0.28f, w * 0.06f, DARK)
        rr(c, -hw * 0.5f, -l * 0.05f, hw * 0.5f, -l * 0.0f, w * 0.02f, GLASS)
        rr(c, -hw * 0.48f, l * 0.01f, hw * 0.48f, l * 0.26f, w * 0.05f, color)
        val beacon = if ((time * 3).toInt() % 2 == 0) Color.parseColor("#FF6D00") else Color.parseColor("#FFE0B2")
        circle(c, 0f, l * 0.13f, w * 0.07f, beacon)
        // Back-hoe boom, dipper and bucket.
        rr(c, -w * 0.08f, l * 0.27f, w * 0.08f, l * 0.44f, w * 0.04f, color)
        line(c, 0f, l * 0.28f, 0f, l * 0.43f, w * 0.03f, DARK)
        rr(c, -w * 0.15f, l * 0.43f, w * 0.15f, hl, w * 0.03f, STEEL)
    }

    private fun tractor(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        val hw = w / 2
        val hl = l / 2
        for (sx in floatArrayOf(-1f, 1f)) {
            wheel(c, sx * (hw - w * 0.15f), l * 0.2f, w * 0.3f, l * 0.36f, turn)
            wheel(c, sx * hw * 0.62f, -l * 0.3f, w * 0.16f, l * 0.17f, turn)
        }
        rr(c, -hw * 0.36f, -hl, hw * 0.36f, l * 0.02f, w * 0.12f, color)
        for (i in 0..3) {
            val y = -hl + l * 0.03f + i * l * 0.025f
            line(c, -hw * 0.22f, y, hw * 0.22f, y, w * 0.025f, Color.parseColor("#FFEB3B"))
        }
        circle(c, hw * 0.18f, -l * 0.14f, w * 0.06f, DARK)
        val fender = shade(color, 0.85f)
        rr(c, -hw * 0.72f, l * 0.03f, -hw * 0.38f, l * 0.37f, w * 0.06f, fender)
        rr(c, hw * 0.38f, l * 0.03f, hw * 0.72f, l * 0.37f, w * 0.06f, fender)
        rr(c, -hw * 0.38f, l * 0.02f, hw * 0.38f, l * 0.42f, w * 0.08f, shade(color, 0.7f))
        rr(c, -hw * 0.2f, l * 0.2f, hw * 0.2f, l * 0.34f, w * 0.05f, DARK)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.035f
        paint.color = DARK
        c.drawCircle(0f, l * 0.1f, w * 0.1f, paint)
        paint.style = Paint.Style.FILL
        rr(c, -w * 0.06f, l * 0.42f, w * 0.06f, hl, w * 0.02f, STEEL)
    }

    private fun monsterTruck(c: Canvas, w: Float, l: Float, turn: Float, color: Int) {
        val hw = w / 2
        val hl = l / 2
        for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-0.3f, 0.3f)) {
            wheel(c, sx * (hw - w * 0.15f), y * l, w * 0.3f, l * 0.3f, turn)
        }
        rr(c, -hw * 0.62f, -hl * 0.86f, hw * 0.62f, hl * 0.86f, w * 0.15f, color)
        // Flames on the bonnet.
        path.reset()
        path.moveTo(-hw * 0.5f, -l * 0.18f)
        path.lineTo(-hw * 0.3f, -l * 0.38f)
        path.lineTo(-hw * 0.15f, -l * 0.22f)
        path.lineTo(0f, -l * 0.41f)
        path.lineTo(hw * 0.15f, -l * 0.22f)
        path.lineTo(hw * 0.3f, -l * 0.38f)
        path.lineTo(hw * 0.5f, -l * 0.18f)
        path.close()
        fill(c, Color.parseColor("#FF9800"))
        quad(c, -hw * 0.5f, -l * 0.16f, hw * 0.5f, -l * 0.16f, hw * 0.44f, -l * 0.04f, -hw * 0.44f, -l * 0.04f, GLASS)
        rr(c, -hw * 0.46f, -l * 0.04f, hw * 0.46f, l * 0.2f, w * 0.08f, shade(color, 0.8f))
        line(c, -hw * 0.5f, l * 0.08f, hw * 0.5f, l * 0.08f, w * 0.05f, SILVER)
        rr(c, -hw * 0.5f, l * 0.22f, hw * 0.5f, l * 0.38f, w * 0.04f, DARK)
    }

    // ---- primitives -----------------------------------------------------------------------

    private fun wheel(c: Canvas, cx: Float, cy: Float, ww: Float, wl: Float, turn: Float) {
        rr(c, cx - ww / 2, cy - wl / 2, cx + ww / 2, cy + wl / 2, ww * 0.3f, TIRE)
        val treads = 4
        val spacing = wl / treads
        val offset = ((turn % spacing) + spacing) % spacing
        for (i in 0 until treads) {
            val y = cy - wl / 2 + (i * spacing + offset) % wl
            line(c, cx - ww * 0.32f, y, cx + ww * 0.32f, y, ww * 0.12f, TREAD)
        }
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, paint)
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

    private fun quad(
        c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float, color: Int,
    ) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        fill(c, color)
    }

    private fun fill(c: Canvas, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
    }

    /** Multiplies a colour's brightness: below 1 darkens, above 1 lightens. */
    fun shade(color: Int, factor: Float): Int {
        fun ch(v: Int) = (v * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }
}
