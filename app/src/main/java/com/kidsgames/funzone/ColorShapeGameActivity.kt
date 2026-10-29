package com.kidsgames.funzone

import android.widget.LinearLayout
import kotlin.random.Random

/** "Find the red one!" / "Find the star!" — learn colours and shapes. */
class ColorShapeGameActivity : BaseGameActivity() {
    override val gameTitle = "Colors & Shapes"
    override val themeColor = KidColors.PURPLE

    override fun startRound() {
        playArea.removeAllViews()
        val askColor = Random.nextBoolean()

        val items: List<ShapeView>
        val target: ShapeView
        val prompt: String
        if (askColor) {
            val colors = KidColors.named.shuffled().take(4)
            items = colors.map { ShapeView(this, KidShape.entries.random(), it.second) }
            target = items.random()
            prompt = "Find the ${KidColors.nameOf(target.color)} one!"
        } else {
            val shapes = KidShape.entries.shuffled().take(4)
            items = shapes.map { ShapeView(this, it, KidColors.named.random().second) }
            target = items.random()
            prompt = "Find the ${target.shape.spokenName}!"
        }

        items.forEach { item ->
            item.background = roundedBackground(0xFFFFFFFF.toInt(), dp(24).toFloat())
            item.setOnClickListener { v ->
                if (item === target) onCorrect(v, "Yes! That's the ${describe(item, askColor)}!")
                else onWrong(v, "That's the ${describe(item, askColor)}. Try again!")
            }
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(prompt, 32f), wrapWidth())
        column.addView(gridOf(items, columns = 2), weighted(1f))
        playArea.addView(column, matchParent())
        speak(prompt)
    }

    private fun describe(item: ShapeView, askColor: Boolean) =
        if (askColor) "${KidColors.nameOf(item.color)} one" else item.shape.spokenName
}
