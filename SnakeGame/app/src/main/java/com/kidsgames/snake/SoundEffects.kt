package com.kidsgames.snake

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Cartoon sound effects synthesized at startup, so the app ships no audio files.
 * Each sound is rendered to a small WAV in the cache directory and played through SoundPool.
 */
class SoundEffects(context: Context) {

    enum class Sound { START, EAT, TURN, GAME_OVER, WIN }

    var muted = false

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = IntArray(Sound.values().size)

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        for (sound in Sound.values()) {
            val file = File(dir, "${sound.name.lowercase()}_v1.wav")
            if (!file.exists()) writeWav(file, render(sound))
            soundIds[sound.ordinal] = pool.load(file.path, 1)
        }
    }

    /** [rate] shifts the pitch (0.5–2.0); used to make each treat sound a little higher. */
    fun play(sound: Sound, rate: Float = 1f) {
        if (muted) return
        val volume = if (sound == Sound.TURN) 0.35f else 0.9f
        pool.play(soundIds[sound.ordinal], volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    // ---------------------------------------------------------------------------------------------
    // Synthesis

    private fun render(sound: Sound): FloatArray = when (sound) {
        // Cheerful rising arpeggio: C5 E5 G5 C6.
        Sound.START -> concat(
            tone(523f, 523f, 80), tone(659f, 659f, 80), tone(784f, 784f, 80), tone(1047f, 1047f, 180)
        )
        // Quick upward "bloop".
        Sound.EAT -> concat(tone(520f, 1040f, 70, harmonics = 0.15f), tone(1040f, 1250f, 60, harmonics = 0.1f))
        // Tiny soft tick.
        Sound.TURN -> tone(900f, 760f, 30, harmonics = 0f)
        // Sad cartoon "wah wah wahhh".
        Sound.GAME_OVER -> concat(
            tone(392f, 370f, 220, harmonics = 0.35f, vibrato = 0f),
            silence(40),
            tone(349f, 330f, 220, harmonics = 0.35f, vibrato = 0f),
            silence(40),
            tone(311f, 233f, 650, harmonics = 0.35f, vibrato = 6f)
        )
        // Little victory fanfare.
        Sound.WIN -> concat(
            tone(523f, 523f, 110), tone(659f, 659f, 110), tone(784f, 784f, 110),
            tone(659f, 659f, 90), tone(784f, 784f, 90), tone(1047f, 1047f, 420, vibrato = 5f)
        )
    }

    private fun tone(
        startHz: Float,
        endHz: Float,
        durationMs: Int,
        harmonics: Float = 0.2f,
        vibrato: Float = 0f
    ): FloatArray {
        val count = SAMPLE_RATE * durationMs / 1000
        val out = FloatArray(count)
        var phase = 0.0
        val attack = min(count / 4, SAMPLE_RATE * 6 / 1000)
        for (i in 0 until count) {
            val t = i / count.toFloat()
            val seconds = i / SAMPLE_RATE.toFloat()
            var hz = startHz + (endHz - startHz) * t
            if (vibrato > 0f) hz *= 1f + 0.03f * sin(2 * PI * vibrato * seconds).toFloat()
            phase += 2 * PI * hz / SAMPLE_RATE
            val wave = sin(phase) + harmonics * sin(3 * phase) / 3
            val envelope = if (i < attack) i / attack.toFloat() else (1f - t) * (1f - t) * 0.6f + (1f - t) * 0.4f
            out[i] = (wave * envelope * 0.6f).toFloat()
        }
        return out
    }

    private fun silence(durationMs: Int) = FloatArray(SAMPLE_RATE * durationMs / 1000)

    private fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        for (sample in samples) buffer.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
    }
}
