package com.kidsgames.funzone

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Hosts a [RaceView] and shows the results card when the race ends. */
class RaceActivity : BaseGameActivity(), RaceView.Listener {

    private val vehicle by lazy {
        VehicleType.valueOf(intent.getStringExtra(EXTRA_VEHICLE) ?: VehicleType.CAR.name)
    }
    private val track by lazy {
        Track.valueOf(intent.getStringExtra(EXTRA_TRACK) ?: Track.CITY.name)
    }

    override val gameTitle: String get() = "${track.emoji} ${track.title}"
    override val themeColor: Int get() = track.themeColor

    private var raceView: RaceView? = null
    private var resumed = false

    override fun startRound() {
        raceView?.pause()
        playArea.removeAllViews()
        playArea.setPadding(0, 0, 0, 0)
        val view = RaceView(this, vehicle, track, this, Garage.colorOf(this, vehicle), Garage.tiltSteering(this))
        raceView = view
        playArea.addView(view, matchParent())
        if (resumed) view.resume()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        raceView?.resume()
    }

    override fun onPause() {
        resumed = false
        raceView?.pause()
        super.onPause()
    }

    override fun say(text: String) = speak(text)

    override fun onRaceFinished(position: Int, coins: Int) {
        handler.postDelayed({ showResults(position, coins) }, 1500)
    }

    private fun showResults(position: Int, coins: Int) {
        val (medal, words) = when (position) {
            1 -> "🏆" to "first"
            2 -> "🥈" to "second"
            3 -> "🥉" to "third"
            else -> "🏅" to "fourth"
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.argb(235, 255, 255, 255), dp(28).toFloat(), dp(4), themeColor)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(label(medal, 96f), wrapWidth())
        card.addView(label("You came $words!", 32f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapWidth())
        card.addView(label("★ $coins stars collected", 22f, Color.parseColor("#FF8F00")), wrapWidth())

        val again = bigButton("🔁\nRace again", KidColors.GREEN, 22f) { startRound() }
        val change = bigButton("🚗\nNew vehicle", KidColors.BLUE, 22f) { finish() }
        card.addView(gridOf(listOf(again, change), columns = 2), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)))

        playArea.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(24), 0, dp(24), 0)
        })
        addStar(if (position == 1) "Hooray! You won the race!" else "You came $words! Great driving!")
    }

    companion object {
        const val EXTRA_VEHICLE = "vehicle"
        const val EXTRA_TRACK = "track"
    }
}
