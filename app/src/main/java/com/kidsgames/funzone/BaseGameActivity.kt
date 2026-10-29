package com.kidsgames.funzone

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shared frame for every game: a top bar (back, title, stars), a play area,
 * spoken instructions for kids who can't read yet, and a celebration overlay.
 */
abstract class BaseGameActivity : Activity() {

    abstract val gameTitle: String
    abstract val themeColor: Int

    /** Builds a fresh round inside [playArea]. */
    abstract fun startRound()

    protected lateinit var playArea: FrameLayout
    protected val handler = Handler(Looper.getMainLooper())

    /** True while a celebration is playing, so extra taps are ignored. */
    protected var busy = false
    protected var stars = 0
        private set

    private lateinit var starsView: TextView
    private lateinit var celebrationView: TextView
    private lateinit var speaker: Speaker

    private val praise = listOf("Great job!", "Awesome!", "You did it!", "Super star!", "Well done!", "Fantastic!")
    private val cheers = listOf("🎉", "🌟", "🏆", "🥳", "👏", "🌈")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF8E1"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(themeColor)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        topBar.addView(bigButton("⬅", Color.argb(60, 0, 0, 0), 26f) { finish() },
            LinearLayout.LayoutParams(dp(64), dp(56)))
        topBar.addView(label(gameTitle, 26f, Color.WHITE).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        starsView = label("⭐ 0", 26f, Color.WHITE)
        topBar.addView(starsView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(topBar, wrapWidth())

        val stage = FrameLayout(this)
        playArea = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        stage.addView(playArea, matchParent())
        celebrationView = label("", 140f).apply { visibility = View.GONE }
        stage.addView(celebrationView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(stage, weighted(1f))

        setContentView(root)
        startRound()
    }

    /** Says [text] out loud, replacing anything currently being spoken. */
    protected fun speak(text: String) = speaker.say(text)

    /** Awards a star and shows a celebration without changing the round. */
    protected fun addStar(message: String? = null) {
        stars++
        StarStore.add(this)
        starsView.text = "⭐ $stars"
        speak(message ?: praise.random())
        celebrationView.text = cheers.random()
        celebrationView.visibility = View.VISIBLE
        celebrationView.scaleX = 0.2f
        celebrationView.scaleY = 0.2f
        celebrationView.alpha = 1f
        celebrationView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(350).withEndAction {
            celebrationView.animate().alpha(0f).setStartDelay(600).setDuration(300).withEndAction {
                celebrationView.visibility = View.GONE
            }.start()
        }.start()
    }

    /** Correct answer: celebrate, then move on to the next round. */
    protected fun onCorrect(tapped: View?, message: String? = null) {
        if (busy) return
        busy = true
        tapped?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(200)?.start()
        addStar(message)
        handler.postDelayed({
            busy = false
            startRound()
        }, 1800)
    }

    /** Wrong answer: a gentle wiggle and encouragement, never a penalty. */
    protected fun onWrong(tapped: View?, hint: String = "Oops! Try again!") {
        if (busy) return
        if (tapped != null) {
            val shift = dp(12).toFloat()
            tapped.animate().translationX(shift).setDuration(60).withEndAction {
                tapped.animate().translationX(-shift).setDuration(60).withEndAction {
                    tapped.animate().translationX(0f).setDuration(60).start()
                }.start()
            }.start()
        }
        speak(hint)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        speaker.shutdown()
        super.onDestroy()
    }
}
