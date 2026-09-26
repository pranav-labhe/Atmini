package com.pranav.atmini.feature.voice.wake

data class WakeSignature(

    val energyPattern: List<Int>,
    val zcrPattern: List<Int>,
    val energyDeltaPattern: List<Int>,
    val delta_normalized: List<Double>

)