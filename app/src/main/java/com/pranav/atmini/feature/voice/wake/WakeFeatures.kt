package com.pranav.atmini.feature.voice.wake

data class WakeFeatures(
    val energyProfile: List<Int>,
    val zcrProfile: List<Int>,
    val energyDeltaProfile: List<Int>,
    val deltaNormalized: List<Double>
)