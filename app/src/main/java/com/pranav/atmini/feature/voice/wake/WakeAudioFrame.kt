package com.pranav.atmini.feature.voice.wake

data class WakeAudioFrame(
    val samples: ShortArray,
    val timestamp: Long
)