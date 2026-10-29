package com.kidsgames.funzone

import android.content.Context
import android.graphics.Color

/** Remembers each vehicle's paint job and the tilt-steering setting. */
object Garage {
    private const val PREFS = "kids_fun_zone_garage"
    private const val KEY_TILT = "tilt_steering"

    val paints = listOf(
        "red" to Color.parseColor("#E53935"), "orange" to Color.parseColor("#FB8C00"),
        "yellow" to Color.parseColor("#FFC107"), "green" to Color.parseColor("#43A047"),
        "blue" to Color.parseColor("#1E88E5"), "purple" to Color.parseColor("#8E24AA"),
        "pink" to Color.parseColor("#EC407A"), "white" to Color.WHITE, "black" to Color.parseColor("#212121"),
    )

    fun colorOf(context: Context, type: VehicleType): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("color_${type.name}", type.color)

    fun setColor(context: Context, type: VehicleType, color: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("color_${type.name}", color).apply()
    }

    fun tiltSteering(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TILT, false)

    fun setTiltSteering(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TILT, on).apply()
    }
}
