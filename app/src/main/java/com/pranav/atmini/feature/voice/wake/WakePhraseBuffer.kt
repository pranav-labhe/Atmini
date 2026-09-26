package com.pranav.atmini.feature.voice.wake

import android.util.Log

class WakePhraseBuffer(

    private val maxWindowMs: Long = 1500,
    private val silenceGapMs: Long = 400

) {

    private val buffer = mutableListOf<WakeAudioFrame>()

    private var lastSpeechTimestamp: Long = 0L

    private var windowStartTimestamp: Long = 0L

    fun addFrame(frame: WakeAudioFrame, isSpeech: Boolean) {

        val now = frame.timestamp

        // start window
        if (buffer.isEmpty()) {
            windowStartTimestamp = now
        }

        if (isSpeech) {

            buffer.add(frame)
            lastSpeechTimestamp = now

        } else {

            // keep short tail for continuity
            if (now - lastSpeechTimestamp < silenceGapMs) {
                buffer.add(frame)
            }
        }

        trimOldFrames(now)
    }

    /**
     * L0 meaning:
     * "Is this memory window complete and stable?"
     */
    fun isWindowStable(now: Long): Boolean {

        val hasData = buffer.isNotEmpty()

        val silenceElapsed = now - lastSpeechTimestamp

        val windowDuration = now - windowStartTimestamp
        Log.d("AtminiWake-BUFFER",
            "buffer=${buffer.size} " +
                    "silence=${now - lastSpeechTimestamp} " +
                    "duration=${now - windowStartTimestamp}"
        )
        return hasData &&
                silenceElapsed >= silenceGapMs &&
                windowDuration <= maxWindowMs
    }

    /**
     * L0 operation:
     * Export current memory window
     */
    fun flush(): List<WakeAudioFrame> {

        val out = buffer.toList()

        buffer.clear()

        lastSpeechTimestamp = 0L
        windowStartTimestamp = 0L

        return out
    }
    fun getFrames(): List<WakeAudioFrame> = buffer.toList()
    private fun trimOldFrames(now: Long) {

        val cutoff = now - maxWindowMs

        buffer.removeAll { it.timestamp < cutoff }
    }
    fun getFramesSnapshot(): List<WakeAudioFrame> {
        return buffer.toList()
    }
}