package com.pranav.atmini.feature.voice.wake

class WakeGate(

    private val cooldownMs: Long = 1200

) {

    private var lastWakeTime: Long = 0

    fun allowWake(timestamp: Long): Boolean {

        val inCooldown =
            (timestamp - lastWakeTime) < cooldownMs

        if (!inCooldown) {
            lastWakeTime = timestamp
            return true
        }

        return false
    }
}