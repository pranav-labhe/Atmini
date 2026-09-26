package com.pranav.atmini.feature.voice.wake

data class WakeEvent(
    val timestamp: Long,
    val confidence: Float,
    val source: String
)