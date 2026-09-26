package com.pranav.atmini.feature.voice.wake

import kotlin.math.abs

class FeatureExtractor {

    private var lastEnergy: Int? = null

    fun extract(frames: List<WakeAudioFrame>): WakeFeatures {
        lastEnergy = null

        val energyList = ArrayList<Int>(frames.size)
        val zcrList = ArrayList<Int>(frames.size)
        val deltaList = ArrayList<Int>(frames.size)

        val zcd = ZeroCrossingDetector()
        for (f in frames) {
            val energy = f.samples.sumOf { abs(it.toInt()) }
            val zcr = zcd.calculate(f.samples)

            energyList.add(energy)
            zcrList.add(zcr)

            if (lastEnergy != null) {
                deltaList.add(energy - lastEnergy!!)
            } else {
                deltaList.add(0)
            }

            lastEnergy = energy
        }

        return WakeFeatures(
            energyProfile = energyList,
            zcrProfile = zcrList,
            energyDeltaProfile = deltaList,
            deltaNormalized = normalizeDelta(deltaList)
        )
    }

    fun reset() {
        lastEnergy = null
    }

    private fun normalizeDelta(list: List<Int>): List<Double> {
        if (list.isEmpty()) return emptyList()
        val maxAbs = list.maxOf { abs(it).toDouble() }.takeIf { it != 0.0 } ?: 1.0
        return list.map { abs(it) / maxAbs }
    }
}
