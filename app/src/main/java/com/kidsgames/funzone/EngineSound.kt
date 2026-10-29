package com.kidsgames.funzone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Synthesises the race sounds live (no audio files needed): an engine whose
 * pitch follows the speed, plus horn, siren, bump and coin chime effects.
 */
class EngineSound(private val pitch: Float, private val deepHorn: Boolean) {

    /** 0 = idle, 1 = top speed (can go a little higher while boosting). */
    @Volatile var speedFraction = 0f

    @Volatile private var hornLeft = 0
    @Volatile private var sirenLeft = 0
    @Volatile private var bumpLeft = 0
    @Volatile private var chimeLeft = 0
    @Volatile private var running = false
    private var thread: Thread? = null

    fun horn() { hornLeft = RATE * 6 / 10 }
    fun siren() { sirenLeft = RATE * 2 }
    fun bump() { bumpLeft = BUMP_SAMPLES }
    fun chime() { chimeLeft = CHIME_SAMPLES }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "engine-sound").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            running = false
            return
        }
        track.play()

        val buf = ShortArray(512)
        val noise = java.util.Random()
        val hornScale = if (deepHorn) 0.7 else 1.0
        var enginePhase = 0.0
        var subPhase = 0.0
        var hornA = 0.0
        var hornB = 0.0
        var sirenPhase = 0.0
        var sirenTime = 0.0
        var chimePhase = 0.0
        var smoothSpeed = 0f

        while (running) {
            for (i in buf.indices) {
                smoothSpeed += (speedFraction - smoothSpeed) * 0.0005f
                val f = 38.0 * pitch * (1.0 + 2.4 * smoothSpeed)
                enginePhase = (enginePhase + f / RATE) % 1.0
                subPhase = (subPhase + f * 0.5 / RATE) % 1.0
                var s = ((enginePhase * 2 - 1) * 0.6 + sin(2 * PI * subPhase) * 0.4) * (0.08 + 0.05 * smoothSpeed)

                if (hornLeft > 0) {
                    hornLeft--
                    hornA = (hornA + 349.0 * hornScale / RATE) % 1.0
                    hornB = (hornB + 440.0 * hornScale / RATE) % 1.0
                    s += (square(hornA) + square(hornB)) * 0.1
                }
                if (sirenLeft > 0) {
                    sirenLeft--
                    sirenTime += 1.0 / RATE
                    val sf = 750.0 + 350.0 * sin(2 * PI * sirenTime * 1.2)
                    sirenPhase = (sirenPhase + sf / RATE) % 1.0
                    s += sin(2 * PI * sirenPhase) * 0.2
                }
                if (bumpLeft > 0) {
                    s += (noise.nextFloat() * 2 - 1) * 0.35 * bumpLeft / BUMP_SAMPLES
                    bumpLeft--
                }
                if (chimeLeft > 0) {
                    val cf = if (chimeLeft > CHIME_SAMPLES / 2) 1320.0 else 1760.0
                    chimePhase = (chimePhase + cf / RATE) % 1.0
                    s += sin(2 * PI * chimePhase) * 0.22 * chimeLeft / CHIME_SAMPLES
                    chimeLeft--
                }
                buf[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop()
        track.release()
    }

    private fun square(phase: Double) = if (phase < 0.5) 1.0 else -1.0

    private companion object {
        const val RATE = 22050
        const val BUMP_SAMPLES = RATE / 6
        const val CHIME_SAMPLES = RATE / 5
    }
}
