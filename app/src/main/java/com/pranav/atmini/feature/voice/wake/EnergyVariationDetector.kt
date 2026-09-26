package com.pranav.atmini.feature.voice.wake

import kotlin.math.abs

class EnergyVariationDetector(

    private val variationThreshold: Int = 40000

) {

    private var previousEnergy = 0

    fun hasVariation(
        currentEnergy: Int
    ): Boolean {

        val delta =
            abs(currentEnergy - previousEnergy)

        previousEnergy = currentEnergy

        return delta > variationThreshold
    }
}