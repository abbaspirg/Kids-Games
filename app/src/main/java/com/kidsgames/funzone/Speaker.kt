package com.kidsgames.funzone

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Friendly text-to-speech voice so kids who can't read yet still know what to do. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(0.9f)
        engine.setPitch(1.2f)
        ready = true
        pending?.let { say(it) }
        pending = null
    }

    /** Says [text], replacing anything currently being spoken. */
    fun say(text: String) {
        val engine = tts
        if (engine != null && ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids")
        else pending = text
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
