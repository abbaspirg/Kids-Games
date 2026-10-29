package com.kidsgames.funzone

import android.graphics.Color
import android.widget.LinearLayout

/** "A is for Apple" — see a letter, tap the picture that starts with it. */
class AbcGameActivity : BaseGameActivity() {
    override val gameTitle = "ABC Fun"
    override val themeColor = KidColors.RED

    private data class Entry(val letter: Char, val emoji: String, val word: String)

    private val alphabet = listOf(
        Entry('A', "🍎", "Apple"), Entry('B', "⚽", "Ball"), Entry('C', "🐱", "Cat"),
        Entry('D', "🐶", "Dog"), Entry('E', "🐘", "Elephant"), Entry('F', "🐟", "Fish"),
        Entry('G', "🍇", "Grapes"), Entry('H', "🏠", "House"), Entry('I', "🍦", "Ice cream"),
        Entry('J', "👖", "Jeans"), Entry('K', "🔑", "Key"), Entry('L', "🦁", "Lion"),
        Entry('M', "🌙", "Moon"), Entry('N', "👃", "Nose"), Entry('O', "🍊", "Orange"),
        Entry('P', "🐷", "Pig"), Entry('Q', "👸", "Queen"), Entry('R', "🐰", "Rabbit"),
        Entry('S', "☀️", "Sun"), Entry('T', "🌳", "Tree"), Entry('U', "☂️", "Umbrella"),
        Entry('V', "🎻", "Violin"), Entry('W', "🍉", "Watermelon"), Entry('Y', "🧶", "Yarn"),
        Entry('Z', "🦓", "Zebra"),
    )
    private val letterColors = listOf(KidColors.RED, KidColors.BLUE, KidColors.GREEN, KidColors.PURPLE, KidColors.ORANGE)
    private var next = 0

    override fun startRound() {
        playArea.removeAllViews()
        // Walk through the alphabet in order so kids hear it as a sequence.
        val target = alphabet[next % alphabet.size]
        next++
        val options = (listOf(target) + (alphabet - target).shuffled().take(2)).shuffled()

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label("${target.letter} ${target.letter.lowercaseChar()}", 110f, letterColors.random()).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, weighted(1.2f))

        val buttons = options.map { entry ->
            label(entry.emoji, 64f).apply {
                background = roundedBackground(Color.WHITE, dp(24).toFloat(), dp(3), themeColor)
                setOnClickListener { v ->
                    if (entry == target) onCorrect(v, "${target.letter} is for ${target.word}!")
                    else onWrong(v, "That's ${entry.word}. ${entry.word} starts with ${entry.letter}. Try again!")
                }
            }
        }
        column.addView(gridOf(buttons, columns = 3), weighted(1f))
        playArea.addView(column, matchParent())
        speak("${target.letter}. Which picture starts with ${target.letter}?")
    }
}
