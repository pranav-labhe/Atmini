package com.pranav.atmini.feature.voice.wake

class ZeroCrossingDetector {

    fun calculate(
        samples: ShortArray
    ): Int {

        var crossings = 0

        for (i in 1 until samples.size) {

            val prev = samples[i - 1]
            val current = samples[i]

            val crossed =
                (prev > 0 && current < 0) ||
                        (prev < 0 && current > 0)

            if (crossed) {
                crossings++
            }
        }

        return crossings
    }
}