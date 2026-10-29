package com.kidsgames.funzone

/** JCB digger: scoop dirt, find treasure and fill the dump trucks. */
class DiggingGameActivity : BaseGameActivity(), DiggingView.Listener {
    override val gameTitle = "🚜 JCB Dig & Dump"
    override val themeColor = KidColors.ORANGE

    private var diggingView: DiggingView? = null
    private var resumed = false

    override fun startRound() {
        playArea.removeAllViews()
        playArea.setPadding(0, 0, 0, 0)
        val view = DiggingView(this, this)
        diggingView = view
        playArea.addView(view, matchParent())
        if (resumed) view.resume()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        diggingView?.resume()
    }

    override fun onPause() {
        resumed = false
        diggingView?.pause()
        super.onPause()
    }

    override fun say(text: String) = speak(text)

    override fun onTruckFull(trucks: Int) {
        addStar(if (trucks == 1) "The truck is full! Off it goes! Beep beep!" else "$trucks trucks filled! Super digger!")
    }
}
