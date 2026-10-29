package com.kidsgames.funzone

import android.graphics.Color
import android.widget.LinearLayout

/** Free finger painting — no right or wrong answers. */
class DrawingActivity : BaseGameActivity() {
    override val gameTitle = "Paint Time"
    override val themeColor = KidColors.BROWN

    override fun startRound() {
        playArea.removeAllViews()
        val canvasView = DrawingView(this)

        val palette = (KidColors.named.map { it.second } + KidColors.BLACK).map { color ->
            bigButton("", color) {
                canvasView.brushColor = color
                speak(KidColors.nameOf(color).ifEmpty { "black" })
            }
        }
        val tools = listOf(
            bigButton("↩", Color.GRAY, 28f) { canvasView.undo() },
            bigButton("🗑", Color.GRAY, 28f) {
                canvasView.clear()
                speak("All clean!")
            },
        )

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(canvasView, weighted(1f))
        column.addView(gridOf(palette + tools, columns = 6, gapDp = 4),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
        playArea.addView(column, matchParent())
        speak("Let's paint! Pick a color.")
    }
}
