package com.kidsgames.funzone

import android.content.Context
import android.graphics.Canvas
import android.view.View
import kotlin.math.min

/** Shows one vehicle side-on to the screen (driving right), scaled to fit. */
class VehicleView(context: Context, private val type: VehicleType, color: Int = type.color) : View(context) {
    var color: Int = color
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val availLength = width * 0.85f
        val availWidth = height * 0.8f
        val scale = min(availLength / type.lengthM, availWidth / type.widthM)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(90f)
        VehiclePainter.draw(canvas, type, type.widthM * scale, type.lengthM * scale, 0f, 0f, color)
        canvas.restore()
    }
}
