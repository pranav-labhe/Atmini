package com.pranav.atmini.feature.voice.wake

class SpeechWindowTracker(
    private val requiredFrames: Int = 4,
    private val cooldownFrames: Int = 3
) {

    private var speechFrames = 0
    private var silenceFrames = 0
    private var wasStable = false

    fun update(isSpeech: Boolean): Boolean {
        if (isSpeech) {
            speechFrames++
            silenceFrames = 0
        } else {
            silenceFrames++
            if (silenceFrames >= cooldownFrames) {
                speechFrames = 0
                wasStable = false
            }
        }

        val isStableNow = speechFrames >= requiredFrames
        val risingEdge = isStableNow && !wasStable
        wasStable = isStableNow

        return risingEdge
    }
}
