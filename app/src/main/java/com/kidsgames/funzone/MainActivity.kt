package com.kidsgames.funzone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/** The home screen: big colourful tiles, one per game. */
class MainActivity : Activity() {

    private data class Game(val emoji: String, val title: String, val color: Int, val activity: Class<out Activity>)

    private val games = listOf(
        Game("🔢", "Count It!", KidColors.BLUE, CountingGameActivity::class.java),
        Game("🎨", "Colors & Shapes", KidColors.PURPLE, ColorShapeGameActivity::class.java),
        Game("🐶", "Memory Match", KidColors.GREEN, MemoryGameActivity::class.java),
        Game("🔤", "ABC Fun", KidColors.RED, AbcGameActivity::class.java),
        Game("➕", "Add It Up", KidColors.ORANGE, MathGameActivity::class.java),
        Game("🎈", "Pop Balloons", KidColors.PINK, BalloonGameActivity::class.java),
        Game("🖍️", "Paint Time", KidColors.BROWN, DrawingActivity::class.java),
    )

    private lateinit var starsView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF8E1"))
            setPadding(dp(8), dp(16), dp(8), dp(8))
        }
        root.addView(label("Kids Fun Zone", 36f, Color.parseColor("#FF7043")).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, wrapWidth())
        starsView = label("", 24f)
        root.addView(starsView, wrapWidth())

        root.addView(bigButton("🏁 Vehicle Racing\n🏎️ 🚌 🚜 🚒 🏍️", Color.parseColor("#FF5722"), 28f) {
            startActivity(Intent(this, RaceSetupActivity::class.java))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)).apply {
            setMargins(dp(8), dp(12), dp(8), dp(4))
        })

        val tiles = games.map { game ->
            bigButton("${game.emoji}\n${game.title}", game.color, 24f) {
                startActivity(Intent(this, game.activity))
            }
        }
        root.addView(gridOf(tiles, columns = 2), weighted(1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        starsView.text = "⭐ ${StarStore.total(this)} stars"
    }
}
