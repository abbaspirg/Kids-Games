package com.kidsgames.funzone

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Bus driver: stop at every bus stop, pick up passengers and obey traffic lights. */
class BusDriverActivity : BaseGameActivity(), BusRouteView.Listener {
    override val gameTitle = "🚌 Bus Driver"
    override val themeColor = KidColors.ORANGE

    private var routeView: BusRouteView? = null
    private var resumed = false

    override fun startRound() {
        routeView?.pause()
        playArea.removeAllViews()
        playArea.setPadding(0, 0, 0, 0)
        val view = BusRouteView(this, this, Garage.colorOf(this, VehicleType.BUS))
        routeView = view
        playArea.addView(view, matchParent())
        if (resumed) view.resume()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        routeView?.resume()
    }

    override fun onPause() {
        resumed = false
        routeView?.pause()
        super.onPause()
    }

    override fun say(text: String) = speak(text)

    override fun onRouteFinished(passengers: Int, stopsServed: Int, totalStops: Int) {
        handler.postDelayed({ showResults(passengers, stopsServed, totalStops) }, 1800)
    }

    private fun showResults(passengers: Int, stopsServed: Int, totalStops: Int) {
        val perfect = stopsServed == totalStops
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.argb(235, 255, 255, 255), dp(28).toFloat(), dp(4), themeColor)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(label(if (perfect) "🏆" else "🚌", 96f), wrapWidth())
        card.addView(label("Route complete!", 32f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapWidth())
        card.addView(label("🚏 $stopsServed of $totalStops stops", 22f), wrapWidth())
        card.addView(label("👥 $passengers passengers on board", 22f, Color.parseColor("#FF8F00")), wrapWidth())
        val again = bigButton("🔁\nDrive again", KidColors.GREEN, 22f) { startRound() }
        val home = bigButton("🏠\nHome", KidColors.BLUE, 22f) { finish() }
        card.addView(gridOf(listOf(again, home), columns = 2), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)))
        playArea.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(24), 0, dp(24), 0)
        })
        addStar(if (perfect) "Perfect! You stopped at every bus stop!" else "Great driving, bus driver!")
    }
}
