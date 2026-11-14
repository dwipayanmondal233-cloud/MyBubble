package com.example.mybubble

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SoundFx {
    private const val RATE = 22050

    // Each segment: start pitch, end pitch, length in ms
    private fun play(segments: List<Triple<Double, Double, Int>>) {
        Thread {
            try {
                var total = 0
                var totalMs = 0
                for (s in segments) {
                    total += s.third * RATE / 1000
                    totalMs += s.third
                }
                val buf = ShortArray(total)
                var idx = 0
                var phase = 0.0
                for ((f0, f1, ms) in segments) {
                    val n = ms * RATE / 1000
                    for (i in 0 until n) {
                        val t = i.toDouble() / n
                        val f = f0 + (f1 - f0) * t
                        phase += 2 * PI * f / RATE
                        val attack = minOf(1.0, i / 150.0)
                        val env = (1.0 - t) * attack
                        buf[idx++] = (sin(phase) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(totalMs + 100L)
                track.release()
            } catch (e: Exception) {
            }
        }.start()
    }

    fun press(enabled: Boolean) {
        if (enabled) play(listOf(Triple(520.0, 400.0, 70)))
    }

    fun pop(enabled: Boolean) {
        if (enabled) play(listOf(Triple(700.0, 1100.0, 90), Triple(1320.0, 1320.0, 160)))
    }
}