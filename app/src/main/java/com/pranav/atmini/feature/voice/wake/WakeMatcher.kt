package com.pranav.atmini.feature.voice.wake

import android.util.Log
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class WakeMatcher {

    fun match(
        features: WakeFeatures,
        signature: WakeSignature
    ): Boolean {
        val energyScore = bestAlignmentScore(
            normalize(features.energyProfile),
            normalize(signature.energyPattern)
        )

        val zcrScore = bestAlignmentScore(
            normalize(features.zcrProfile),
            normalize(signature.zcrPattern)
        )

        val deltaScore = bestAlignmentScore(
            normalizeDouble(features.deltaNormalized),
            normalizeDouble(signature.delta_normalized)
        )

        // Weighted alignment score
        val finalScore = (energyScore * 0.50) + (zcrScore * 0.15) + (deltaScore * 0.35)

        val isMatch = finalScore > 0.75

        Log.d("AtminiMatcher", "Wake Match Evaluation -> FinalScore=$finalScore (Threshold > 0.75) => Match=$isMatch [EnergyScore=$energyScore, ZcrScore=$zcrScore, DeltaScore=$deltaScore]")

        return isMatch
    }

    private fun bestAlignmentScore(short: List<Double>, long: List<Double>): Double {
        var best = 0.0
        val windowSize = short.size
        if (windowSize == 0 || long.size < windowSize) return 0.0

        for (offset in 0..(long.size - windowSize)) {
            val slice = long.subList(offset, offset + windowSize)
            val score = similarity(short, slice)
            if (score > best) best = score
        }

        return best
    }

    private fun normalizeDouble(list: List<Double>): List<Double> {
        if (list.isEmpty()) return emptyList()
        val mean = list.average()
        val std = sqrt(
            list.sumOf { (it - mean).pow(2) } / list.size
        ).takeIf { it > 0 } ?: 1.0

        return list.map { (it - mean) / std }
    }

    private fun normalize(list: List<Int>): List<Double> {
        if (list.isEmpty()) return emptyList()
        val mean = list.average()
        val std = sqrt(
            list.sumOf { (it - mean).pow(2) } / list.size
        ).takeIf { it > 0 } ?: 1.0

        return list.map { (it - mean) / std }
    }

    private fun similarity(a: List<Double>, b: List<Double>): Double {
        val size = min(a.size, b.size)
        if (size == 0) return 0.0

        var dot = 0.0
        var magA = 0.0
        var magB = 0.0

        for (i in 0 until size) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }

        return dot / (sqrt(magA) * sqrt(magB) + 1e-9)
    }
}
