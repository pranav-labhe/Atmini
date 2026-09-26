package com.pranav.atmini.feature.voice.wake

class WakeEventEmitter {

    fun emit(
        gateAllowed: Boolean,
        timestamp: Long,
        onWake: (WakeEvent) -> Unit
    ) {

        if (!gateAllowed) return

        onWake(
            WakeEvent(
                timestamp = timestamp,
                confidence = 1.0f,
                source = "voice"
            )
        )
    }
}