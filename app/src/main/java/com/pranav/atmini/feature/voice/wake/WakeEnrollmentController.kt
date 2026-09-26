package com.pranav.atmini.feature.voice.wake

import WakeSignatureStore
import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.ArrayDeque
import kotlin.math.abs

class WakeEnrollmentController(
    private val store: WakeSignatureStore
) {

    private val audioCapture = AudioCapture()
    private val vad = VoiceActivityDetector()
    private val extractor = FeatureExtractor()

    private var active = false

    private val energyList = mutableListOf<Int>()
    private val zcrList = mutableListOf<Int>()
    private val deltaList = mutableListOf<Int>()
    private val deltaNormalizedList = mutableListOf<Double>()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {

        active = true

        energyList.clear()
        zcrList.clear()
        deltaList.clear()

        Log.d("Atmini-SIG", "ENROLLMENT STARTED")

        audioCapture.start { frame ->

            if (!active) return@start

            val speech = vad.isSpeech(listOf(frame) as ArrayDeque<WakeAudioFrame>)

            if (!speech) return@start

            val features = extractor.extract(listOf(frame))

            energyList.add(features.energyProfile.firstOrNull() ?: 0)
            zcrList.add(features.zcrProfile.firstOrNull() ?: 0)
            var prev: Int? = null
            val e = features.energyProfile.firstOrNull() ?: 0
            val d = if (prev == null) 0 else abs(e - prev!!)
            prev = e
            deltaList.add(d)

            Log.d(
                "Atmini-SIG",
                "RECORDED e=${energyList.last()} z=${zcrList.last()}"
            )
        }
    }

    fun stop() {
        active = false
        audioCapture.stop()
        Log.d("Atmini-SIG", "ENROLLMENT STOPPED")
    }

    fun save() {
        active = false

        Log.d("Atmini-SIG", "SAVE TRIGGERED")

        if (energyList.size < 10) {
            Log.d("Atmini-SIG", "NOT ENOUGH DATA")
            return
        }

        val signature = WakeSignature(
            energyPattern = energyList.toList(),
            zcrPattern = zcrList.toList(),
            energyDeltaPattern = deltaList.toList(),
            delta_normalized = deltaNormalizedList.toList()
        )

        store.save(signature)

        Log.d("Atmini-SIG", "SAVE COMPLETED")
    }
}