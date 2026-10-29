package com.kidsgames.funzone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The home screen: vehicle games first, then the learning games. */
class MainActivity : Activity() {

    private data class Game(val emoji: String, val title: String, val color: Int, val activity: Class<out Activity>)

    private val vehicleGames = listOf(
        Game("🚜", "JCB Dig & Dump", KidColors.ORANGE, DiggingGameActivity::class.java),
        Game("🚌", "Bus Driver", Color.parseColor("#FFB300"), BusDriverActivity::class.java),
    )

    private val learningGames = listOf(
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

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        column.addView(label("Kids Fun Zone", 36f, Color.parseColor("#FF7043")).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, wrapWidth())
        starsView = label("", 24f)
        column.addView(starsView, wrapWidth())

        column.addView(sectionTitle("🚗 Vehicle Games"), wrapWidth())
        column.addView(bigButton("🏁 Vehicle Racing\n🏎️ 🚌 🚜 🚒 🏍️", Color.parseColor("#FF5722"), 28f) {
            startActivity(Intent(this, RaceSetupActivity::class.java))
        }, fixedHeight(150))
        column.addView(tileRow(vehicleGames), fixedHeight(140))

        column.addView(sectionTitle("🎓 Learning Games"), wrapWidth())
        learningGames.chunked(2).forEach { column.addView(tileRow(it), fixedHeight(140)) }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#FFF8E1"))
            addView(column)
        })
    }

    private fun sectionTitle(text: String) = label(text, 24f, Color.parseColor("#5D4037")).apply {
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun tileRow(games: List<Game>): View {
        val tiles = games.map { game ->
            bigButton("${game.emoji}\n${game.title}", game.color, 22f) {
                startActivity(Intent(this, game.activity))
            }
        }
        return gridOf(tiles, columns = 2)
    }

    private fun fixedHeight(heightDp: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))

    override fun onResume() {
        super.onResume()
        starsView.text = "⭐ ${StarStore.total(this)} stars"
    }
}
