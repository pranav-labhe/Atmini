package com.pranav.atmini.feature.voice.wake

import kotlin.math.abs

class TransientNoiseDetector(
    private val spikeThreshold: Int = 18000
) {

    fun isTransient(samples: ShortArray): Boolean {
        for (i in 1 until samples.size) {
            val delta = abs(samples[i] - samples[i - 1])
            if (delta > spikeThreshold) {
                return true
            }
        }
        return false
    }

    fun isTransientInFrames(frames: Iterable<WakeAudioFrame>): Boolean {
        var prevSample: Short? = null
        for (frame in frames) {
            val samples = frame.samples
            for (i in samples.indices) {
                val current = samples[i]
                if (prevSample != null) {
                    val delta = abs(current - prevSample)
                    if (delta > spikeThreshold) {
                        return true
                    }
                }
                prevSample = current
            }
        }
        return false
    }
}
