package com.kidsgames.funzone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class KidShape(val spokenName: String) {
    CIRCLE("circle"), SQUARE("square"), TRIANGLE("triangle"), STAR("star"), HEART("heart"),
}

/** Draws a single filled shape, centred and scaled to fit the view. */
class ShapeView(context: Context, val shape: KidShape, val color: Int) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@ShapeView.color }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dp(4).toFloat()
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.4f
        path.reset()
        when (shape) {
            KidShape.CIRCLE -> path.addCircle(cx, cy, r, Path.Direction.CW)
            KidShape.SQUARE -> path.addRoundRect(cx - r, cy - r, cx + r, cy + r, r * 0.12f, r * 0.12f, Path.Direction.CW)
            KidShape.TRIANGLE -> {
                path.moveTo(cx, cy - r)
                path.lineTo(cx + r, cy + r * 0.8f)
                path.lineTo(cx - r, cy + r * 0.8f)
                path.close()
            }
            KidShape.STAR -> {
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) r else r * 0.45f
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val x = cx + (radius * cos(angle)).toFloat()
                    val y = cy + (radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            KidShape.HEART -> {
                path.moveTo(cx, cy + r * 0.9f)
                path.cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.6f, cy - r * 1.3f, cx, cy - r * 0.45f)
                path.cubicTo(cx + r * 0.6f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
                path.close()
            }
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, outline)
    }
}
