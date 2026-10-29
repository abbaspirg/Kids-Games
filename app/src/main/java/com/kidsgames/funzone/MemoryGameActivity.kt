package com.kidsgames.funzone

import android.graphics.Color
import android.widget.TextView

/** Flip the cards and find the matching animal pairs. */
class MemoryGameActivity : BaseGameActivity() {
    override val gameTitle = "Memory Match"
    override val themeColor = KidColors.GREEN

    private val animals = listOf("🐶", "🐱", "🐰", "🐻", "🐼", "🦁", "🐸", "🐵", "🐷", "🐮")
    private val faceDownColor = KidColors.GREEN
    private val faceUpColor = Color.WHITE

    private var cards = listOf<TextView>()
    private var faces = listOf<String>()
    private val matched = mutableSetOf<Int>()
    private var firstPick: Int? = null

    override fun startRound() {
        playArea.removeAllViews()
        matched.clear()
        firstPick = null
        // Start with 4 pairs and move up to 6 once the child has won a game.
        val pairs = if (stars == 0) 4 else 6
        faces = animals.shuffled().take(pairs).flatMap { listOf(it, it) }.shuffled()
        cards = faces.indices.map { index ->
            label("❓", 40f, Color.WHITE).apply {
                background = roundedBackground(faceDownColor, dp(20).toFloat(), dp(3))
                setOnClickListener { flip(index) }
            }
        }
        playArea.addView(gridOf(cards, columns = if (pairs == 4) 2 else 3), matchParent())
        speak("Find the matching pairs!")
    }

    private fun show(index: Int, faceUp: Boolean) {
        val card = cards[index]
        card.animate().rotationY(90f).setDuration(120).withEndAction {
            card.text = if (faceUp) faces[index] else "❓"
            card.textSize = if (faceUp) 56f else 40f
            card.background = roundedBackground(if (faceUp) faceUpColor else faceDownColor, dp(20).toFloat(), dp(3),
                if (faceUp) faceDownColor else Color.WHITE)
            card.rotationY = -90f
            card.animate().rotationY(0f).setDuration(120).start()
        }.start()
    }

    private fun flip(index: Int) {
        if (busy || index in matched || index == firstPick) return
        show(index, true)
        val first = firstPick
        if (first == null) {
            firstPick = index
            return
        }
        firstPick = null
        if (faces[first] == faces[index]) {
            matched += first
            matched += index
            if (matched.size == faces.size) {
                handler.postDelayed({ onCorrect(null, "You found them all! Amazing memory!") }, 400)
            } else {
                speak("Match!")
            }
        } else {
            busy = true
            handler.postDelayed({
                show(first, false)
                show(index, false)
                busy = false
            }, 900)
        }
    }
}
