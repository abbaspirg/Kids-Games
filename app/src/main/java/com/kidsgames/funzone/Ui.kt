package com.kidsgames.funzone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Bright, high-contrast colours that kids can name. */
object KidColors {
    val RED = Color.parseColor("#F44336")
    val ORANGE = Color.parseColor("#FF9800")
    val YELLOW = Color.parseColor("#FFEB3B")
    val GREEN = Color.parseColor("#4CAF50")
    val BLUE = Color.parseColor("#2196F3")
    val PURPLE = Color.parseColor("#9C27B0")
    val PINK = Color.parseColor("#FF80AB")
    val BROWN = Color.parseColor("#795548")
    val BLACK = Color.parseColor("#212121")

    val named = listOf(
        "red" to RED, "orange" to ORANGE, "yellow" to YELLOW, "green" to GREEN,
        "blue" to BLUE, "purple" to PURPLE, "pink" to PINK, "brown" to BROWN,
    )

    fun nameOf(color: Int): String = named.firstOrNull { it.second == color }?.first ?: ""
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun roundedBackground(color: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = Color.WHITE) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

/** A large, rounded, colourful tap target with a little bounce on press. */
fun Context.bigButton(
    text: String,
    color: Int,
    textSizeSp: Float = 36f,
    onClick: (View) -> Unit,
): TextView = TextView(this).apply {
    this.text = text
    textSize = textSizeSp
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    setShadowLayer(4f, 0f, 2f, Color.argb(90, 0, 0, 0))
    background = roundedBackground(color, dp(24).toFloat(), dp(3))
    val pad = dp(12)
    setPadding(pad, pad, pad, pad)
    isClickable = true
    setOnClickListener { view ->
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
        onClick(view)
    }
}

fun Context.label(text: String, sizeSp: Float, color: Int = KidColors.BLACK): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        gravity = Gravity.CENTER
        setTextColor(color)
    }

/** Lays views out in an evenly weighted grid (rows of [columns]) that fills its parent. */
fun Context.gridOf(views: List<View>, columns: Int, gapDp: Int = 8): LinearLayout {
    val gap = dp(gapDp)
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.chunked(columns).forEach { rowViews ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowViews.forEach { v ->
                row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(gap, gap, gap, gap)
                })
            }
            // Pad short rows so every cell keeps the same size.
            repeat(columns - rowViews.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}

fun matchParent() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

fun weighted(weight: Float) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight)

fun wrapWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
