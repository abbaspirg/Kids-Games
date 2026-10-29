package com.kidsgames.funzone

import android.content.Context

/** Keeps the total number of stars earned across all games on this device. */
object StarStore {
    private const val PREFS = "kids_fun_zone"
    private const val KEY_TOTAL = "total_stars"

    fun total(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TOTAL, 0)

    fun add(context: Context, amount: Int = 1) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + amount).apply()
    }
}
