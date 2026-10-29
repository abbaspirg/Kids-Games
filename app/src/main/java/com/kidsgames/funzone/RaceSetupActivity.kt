package com.kidsgames.funzone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/** Step 1: pick a vehicle. Step 2: paint it in the garage. Step 3: pick where to race. */
class RaceSetupActivity : Activity() {

    private lateinit var speaker: Speaker
    private lateinit var root: LinearLayout
    private var chosen: VehicleType? = null
    private enum class Step { VEHICLES, GARAGE, TRACKS }
    private var step = Step.VEHICLES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF8E1"))
        }
        setContentView(root)
        showVehicles()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val type = chosen
        when {
            step == Step.TRACKS && type != null -> showGarage(type)
            step == Step.GARAGE -> showVehicles()
            else -> super.onBackPressed()
        }
    }

    private fun header(title: String, onBack: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#FF5722"))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(bigButton("⬅", Color.argb(60, 0, 0, 0), 26f) { onBack() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        addView(label(title, 26f, Color.WHITE).apply { setTypeface(typeface, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun showVehicles() {
        step = Step.VEHICLES
        chosen = null
        root.removeAllViews()
        root.addView(header("Pick your vehicle!") { finish() }, wrapWidth())
        val tiles = VehicleType.entries.map { type -> vehicleTile(type) }
        root.addView(scrollGrid(tiles, rowHeightDp = 170), weighted(1f))
        speaker.say("Pick your vehicle!")
    }

    private fun vehicleTile(type: VehicleType): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedBackground(Color.WHITE, dp(24).toFloat(), dp(4), type.color.let {
            if (it == Color.WHITE) Color.parseColor("#1565C0") else it
        })
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(VehicleView(context, type, Garage.colorOf(context, type)), weighted(1f))
        addView(label(type.title, 20f).apply { setTypeface(typeface, Typeface.BOLD) }, wrapWidth())
        val badges = "⚡".repeat(type.speedStars) + if (type.offroad) "  💪 Off-road" else ""
        addView(label(badges, 14f, Color.parseColor("#E65100")), wrapWidth())
        isClickable = true
        setOnClickListener { v ->
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            speaker.say("${type.title}! ${type.cheer}")
            showGarage(type)
        }
    }

    private fun showGarage(type: VehicleType) {
        step = Step.GARAGE
        chosen = type
        root.removeAllViews()
        root.addView(header("🔧 Garage: paint it!") { showVehicles() }, wrapWidth())

        val preview = VehicleView(this, type, Garage.colorOf(this, type)).apply {
            background = roundedBackground(Color.WHITE, dp(28).toFloat(), dp(4), Color.parseColor("#FF5722"))
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(16), dp(16), dp(16), dp(8))
        })

        val swatches = Garage.paints.map { (name, color) ->
            bigButton("", color) {
                preview.color = color
                Garage.setColor(this, type, color)
                speaker.say(name)
            }.apply {
                background = roundedBackground(color, dp(40).toFloat(), dp(4), Color.parseColor("#BDBDBD"))
            }
        } + bigButton("↺", Color.parseColor("#9E9E9E"), 30f) {
            preview.color = type.color
            Garage.setColor(this, type, type.color)
            speaker.say("Back to the original colour")
        }
        root.addView(gridOf(swatches, columns = 5, gapDp = 6), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(150)))

        root.addView(bigButton("Next ➡", KidColors.GREEN, 30f) { showTracks(type) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88)).apply {
                setMargins(dp(16), dp(8), dp(16), dp(16))
            })
        speaker.say("Pick a colour for your ${type.title}!")
    }

    private fun showTracks(type: VehicleType) {
        step = Step.TRACKS
        chosen = type
        root.removeAllViews()
        root.addView(header("Where to race?") { showGarage(type) }, wrapWidth())
        val tiles = Track.entries.map { track ->
            bigButton("${track.emoji}\n${track.title}", track.themeColor, 24f) {
                speaker.say("${track.title}. Ready, set...")
                startActivity(Intent(this, RaceActivity::class.java)
                    .putExtra(RaceActivity.EXTRA_VEHICLE, type.name)
                    .putExtra(RaceActivity.EXTRA_TRACK, track.name))
            }
        }
        root.addView(scrollGrid(tiles, rowHeightDp = 150), weighted(1f))

        lateinit var tiltButton: android.widget.TextView
        fun tiltLabel() = if (Garage.tiltSteering(this)) "📱 Tilt steering: ON" else "👆 Finger steering"
        tiltButton = bigButton(tiltLabel(), Color.parseColor("#546E7A"), 22f) {
            val on = !Garage.tiltSteering(this)
            Garage.setTiltSteering(this, on)
            tiltButton.text = tiltLabel()
            speaker.say(if (on) "Tilt your phone to steer!" else "Drag your finger to steer!")
        }
        root.addView(tiltButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply {
            setMargins(dp(16), dp(4), dp(16), dp(12))
        })
        speaker.say("Where do you want to race?")
    }

    /** Two-column grid of fixed-height rows inside a scroll view. */
    private fun scrollGrid(views: List<View>, rowHeightDp: Int): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        views.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { v ->
                row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(8), dp(8), dp(8), dp(8))
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(rowHeightDp)))
        }
        return ScrollView(this).apply { addView(column) }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
