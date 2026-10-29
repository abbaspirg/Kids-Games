package com.kidsgames.funzone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.random.Random

/** Tap floating balloons to pop them; each pop says its colour. 10 pops earns a star. */
class BalloonGameActivity : BaseGameActivity() {
    override val gameTitle = "Pop the Balloons"
    override val themeColor = KidColors.PINK

    private var popped = 0
    private var running = false

    private val spawner = object : Runnable {
        override fun run() {
            if (!running) return
            spawnBalloon()
            handler.postDelayed(this, 900)
        }
    }

    override fun startRound() {
        playArea.removeAllViews()
        speak("Pop the balloons!")
    }

    override fun onResume() {
        super.onResume()
        running = true
        handler.post(spawner)
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(spawner)
        super.onPause()
    }

    private fun spawnBalloon() {
        val width = playArea.width
        val height = playArea.height
        if (width == 0 || height == 0) return
        val size = dp(Random.nextInt(80, 120))
        val color = KidColors.named.random().second
        val balloon = ShapeView(this, KidShape.CIRCLE, color)
        val params = FrameLayout.LayoutParams(size, (size * 1.2f).toInt())
        params.leftMargin = Random.nextInt(0, maxOf(1, width - size))
        balloon.translationY = height.toFloat()
        playArea.addView(balloon, params)

        balloon.animate()
            .translationY(-size * 1.5f)
            .setDuration(Random.nextLong(3500, 6000))
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    playArea.removeView(balloon)
                }
            })
            .start()

        balloon.setOnClickListener { pop(it, color) }
    }

    private fun pop(balloon: View, color: Int) {
        balloon.setOnClickListener(null)
        balloon.animate().setListener(null).cancel()
        balloon.animate().scaleX(1.6f).scaleY(1.6f).alpha(0f).setDuration(150)
            .withEndAction { playArea.removeView(balloon) }.start()
        popped++
        if (popped % 10 == 0) addStar("$popped balloons! Wow!")
        else speak(KidColors.nameOf(color))
    }
}
