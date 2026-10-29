package com.kidsgames.funzone

import android.widget.LinearLayout
import kotlin.math.min
import kotlin.random.Random

/** Count the pictures and tap the right number (1–10, getting harder as stars grow). */
class CountingGameActivity : BaseGameActivity() {
    override val gameTitle = "Count It!"
    override val themeColor = KidColors.BLUE

    private val things = listOf(
        "🍎" to "apples", "🐶" to "puppies", "⭐" to "stars", "🚗" to "cars",
        "🐟" to "fish", "🌸" to "flowers", "🎈" to "balloons", "🐤" to "chicks",
        "🍌" to "bananas", "🦋" to "butterflies",
    )
    private val buttonColors = listOf(KidColors.ORANGE, KidColors.GREEN, KidColors.PURPLE)

    override fun startRound() {
        playArea.removeAllViews()
        val maxCount = min(10, 5 + stars / 3)
        val answer = Random.nextInt(1, maxCount + 1)
        val (emoji, name) = things.random()

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label("How many?", 32f), wrapWidth())

        val pictures = List(answer) { label(emoji, 44f) }
        column.addView(gridOf(pictures, columns = 5, gapDp = 2), weighted(1f))

        val options = (setOf(answer) + generateSequence { Random.nextInt(1, maxCount + 1) }
            .filter { it != answer }.distinct().take(2)).shuffled()
        val buttons = options.mapIndexed { i, number ->
            bigButton(number.toString(), buttonColors[i], 48f) { v ->
                if (number == answer) onCorrect(v, "$answer $name! Great counting!")
                else onWrong(v, "Let's count again!")
            }
        }
        column.addView(gridOf(buttons, columns = 3), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))

        playArea.addView(column, matchParent())
        speak("How many $name can you count?")
    }
}
