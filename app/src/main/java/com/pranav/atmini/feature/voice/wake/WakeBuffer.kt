package com.pranav.atmini.feature.voice.wake

import java.util.ArrayDeque

class WakeBuffer {

    private val frames = ArrayDeque<WakeAudioFrame>()
    private val maxSize = 25

    fun add(frame: WakeAudioFrame) {
        frames.add(frame)
        if (frames.size > maxSize) {
            frames.removeFirst()
        }
    }

    fun isReady(): Boolean = frames.size == maxSize

    fun getWindow(): ArrayDeque<WakeAudioFrame> = frames

    fun reset() {
        frames.clear()
    }
    val size: Int
        get() = frames.size
}