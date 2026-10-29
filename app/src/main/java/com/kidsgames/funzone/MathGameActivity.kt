package com.kidsgames.funzone

import android.widget.LinearLayout
import kotlin.random.Random

/** Picture addition: 🍎🍎 + 🍎 = ? Totals grow from 5 up to 10. */
class MathGameActivity : BaseGameActivity() {
    override val gameTitle = "Add It Up"
    override val themeColor = KidColors.ORANGE

    private val things = listOf("🍎", "🍓", "🐞", "🧁", "🍪", "🐠", "⚽")
    private val buttonColors = listOf(KidColors.BLUE, KidColors.GREEN, KidColors.PINK)

    override fun startRound() {
        playArea.removeAllViews()
        val maxTotal = if (stars < 5) 5 else 10
        val a = Random.nextInt(1, maxTotal)
        val b = Random.nextInt(1, maxTotal - a + 1)
        val answer = a + b
        val emoji = things.random()

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(emoji.repeat(a), 36f).apply { setSingleLine(false) }, weighted(1f))
        column.addView(label("+", 44f, KidColors.ORANGE), wrapWidth())
        column.addView(label(emoji.repeat(b), 36f).apply { setSingleLine(false) }, weighted(1f))
        column.addView(label("$a + $b = ?", 40f), wrapWidth())

        val options = (setOf(answer) + generateSequence { Random.nextInt(2, maxTotal + 2) }
            .filter { it != answer }.distinct().take(2)).shuffled()
        val buttons = options.mapIndexed { i, n ->
            bigButton(n.toString(), buttonColors[i], 48f) { v ->
                if (n == answer) onCorrect(v, "$a plus $b is $answer! Great job!")
                else onWrong(v, "Count them all together. Try again!")
            }
        }
        column.addView(gridOf(buttons, columns = 3), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
        playArea.addView(column, matchParent())
        speak("$a plus $b. How many altogether?")
    }
}
