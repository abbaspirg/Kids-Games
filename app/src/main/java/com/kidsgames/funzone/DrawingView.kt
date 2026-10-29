package com.kidsgames.funzone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** A simple finger-painting canvas. */
class DrawingView(context: Context) : View(context) {
    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    var brushColor: Int = KidColors.RED
    var brushSizePx: Float = context.dp(14).toFloat()

    init {
        setBackgroundColor(Color.WHITE)
    }

    fun clear() {
        strokes.clear()
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        strokes.forEach { canvas.drawPath(it.path, it.paint) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = brushColor
                    style = Paint.Style.STROKE
                    strokeWidth = brushSizePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                // A tiny segment so a single tap leaves a dot.
                val path = Path().apply {
                    moveTo(event.x, event.y)
                    lineTo(event.x + 0.1f, event.y)
                }
                strokes += Stroke(path, paint)
            }
            MotionEvent.ACTION_MOVE -> strokes.lastOrNull()?.path?.lineTo(event.x, event.y)
        }
        invalidate()
        return true
    }
}
