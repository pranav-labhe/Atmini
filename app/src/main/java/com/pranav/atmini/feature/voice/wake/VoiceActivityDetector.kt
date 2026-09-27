package com.pranav.atmini.feature.voice.wake

import android.util.Log
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

class VoiceActivityDetector(
    private val threshold: Int = 350000,
    private val zcrThreshold: Int = 120
) {

    private val zcd = ZeroCrossingDetector()
    private val transientDetector = TransientNoiseDetector()
    private val variationDetector = EnergyVariationDetector(variationThreshold = 50000)

    /**
     * Fast low-power RMS Energy Calculation on single audio frame.
     */
    fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (i in samples.indices) {
            val sample = samples[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / samples.size)
    }

    fun isSpeech(
        frames: ArrayDeque<WakeAudioFrame>
    ): Boolean {
        if (frames.isEmpty()) {
            return false
        }

        var totalEnergy = 0L
        var totalZcr = 0.0

        for (frame in frames) {
            val energy = frame.samples.sumOf {
                abs(it.toInt())
            }
            totalEnergy += energy
            totalZcr += zcd.calculate(frame.samples)
        }

        val avgEnergy = totalEnergy / frames.size
        val avgZcr = totalZcr / frames.size

        val transient = transientDetector.isTransientInFrames(frames)
        val variation = variationDetector.hasVariation(avgEnergy.toInt())

        val isSpeechDetected = avgEnergy > 50000 &&
                !transient

        if (isSpeechDetected) {
            Log.d("AtminiVAD", "🗣️ Speech/Wake Candidate Detected! avgEnergy=$avgEnergy, avgZcr=$avgZcr")
        }

        return isSpeechDetected
    }
}
