package com.pranav.atmini.feature.voice.wake

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.pranav.atmini.core.VoiceCommandEngine
import com.pranav.atmini.core.l0.ai.FastRuleIntentModel
import com.pranav.atmini.feature.voice.overlay.FloatingAtminiService
import com.pranav.atmini.feature.voice.speech.SpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.ArrayDeque

class WakeService : Service() {

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var speechEngine: SpeechEngine? = null
    private val audioCapture = AudioCapture()
    private val vad = VoiceActivityDetector()
    private val wakeBuffer = WakeBuffer()
    private val commandEngine by lazy { VoiceCommandEngine(applicationContext) }
    private val intentModel by lazy { FastRuleIntentModel() }

    private var isListeningSession = false
    private var isResuming = false

    private val overlayClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ATMINI_OVERLAY_CLOSED") {
                Log.d("AtminiWake", "🔄 Overlay closed. Resuming Tier 1 passive listening.")
                resumePassiveListening()
            }
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate() {
        if (!hasMicPermission()) {
            Log.e("AtminiWake", "Mic permission missing. Stopping service.")
            stopSelf()
            return
        }
        super.onCreate()

        val filter = IntentFilter("ATMINI_OVERLAY_CLOSED")
        ContextCompat.registerReceiver(this, overlayClosedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        Log.d("AtminiWake", "WakeService running as pure silent background service (no notifications)")
        startPassiveAudioLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Tier 1: Low-Power Passive Audio Monitoring (Consumes < 0.1% CPU/Battery).
     */
    @SuppressLint("MissingPermission")
    private fun startPassiveAudioLoop() {
        if (!hasMicPermission() || isListeningSession) return

        audioCapture.start { frame ->
            if (isListeningSession) return@start

            val rms = vad.calculateRms(frame.samples)
            if (rms < 5.0) return@start

            wakeBuffer.add(frame)
            val speechDetected = vad.isSpeech(wakeBuffer.getWindow())

            if (speechDetected && !isListeningSession) {
                isListeningSession = true
                wakeBuffer.reset()
                Log.d("AtminiWake", "⚡ Tier 1 Sound Triggered -> Activating Tier 2 On-Demand STT Verification")

                audioCapture.stop()
                triggerTier2SttVerification()
            }
        }
    }

    /**
     * Tier 2: On-Demand STT Verification (Runs only when speech energy is present, then shuts down).
     */
    private fun triggerTier2SttVerification() {
        mainScope.launch {
            try {
                speechEngine?.destroy()
                speechEngine = null
            } catch (_: Exception) {}

            speechEngine = SpeechEngine(
                context = applicationContext,
                onResult = { recognizedText ->
                    Log.d("AtminiSpeech", "🎙️ TRANSCRIBED: '$recognizedText'")

                    if (!recognizedText.isNullOrBlank()) {
                        val lower = recognizedText.lowercase().trim()
                        val isWakeWord = runBlocking { intentModel.isWakeWord(recognizedText) }

                        if (isWakeWord) {
                            Log.d("AtminiWake", "✅ WAKE WORD CONFIRMED ('$recognizedText') -> LAUNCHING OVERLAY")

                            try {
                                val overlayIntent = Intent(applicationContext, FloatingAtminiService::class.java)
                                applicationContext.startService(overlayIntent)
                            } catch (e: Exception) {
                                Log.e("AtminiWake", "Failed to start FloatingAtminiService", e)
                            }

                            val heardBroadcast = Intent("ATMINI_VOICE_HEARD").apply {
                                putExtra("voice_text", recognizedText)
                                setPackage(packageName)
                            }
                            sendBroadcast(heardBroadcast)

                            val cleanedCommand = lower
                                .replace("atmini", "")
                                .replace("mini", "")
                                .replace("atmani", "")
                                .replace("at mini", "")
                                .replace("admin", "")
                                .replace("at me", "")
                                .replace("atm", "")
                                .trim()

                            if (cleanedCommand.isNotBlank()) {
                                commandEngine.processTranscript(cleanedCommand) { statusMsg ->
                                    Log.d("AtminiSpeech", "Command Status: $statusMsg")
                                }
                            }
                        } else {
                            Log.d("AtminiWake", "❌ Ignored: Speech '$recognizedText' does not contain wake word.")
                        }
                    }
                    resumePassiveListening()
                },
                onError = { errCode ->
                    Log.d("AtminiSpeech", "Tier 2 STT ended/timeout code: $errCode (Returning to Tier 1 passive sleep)")
                    resumePassiveListening()
                }
            )
            speechEngine?.startListening()
        }
    }

    private fun resumePassiveListening() {
        if (isResuming) return
        isResuming = true

        mainScope.launch {
            try {
                speechEngine?.destroy()
                speechEngine = null
            } catch (_: Exception) {}

            isListeningSession = false
            delay(350)

            try {
                if (hasMicPermission()) {
                    audioCapture.stop()
                    startPassiveAudioLoop()
                    Log.d("AtminiWake", "💤 Resumed Tier 1 passive background sleep mode.")
                }
            } catch (e: Exception) {
                Log.e("AtminiWake", "Failed to resume passive audio capture", e)
            } finally {
                isResuming = false
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(overlayClosedReceiver)
        } catch (_: Exception) {}

        audioCapture.stop()
        try {
            speechEngine?.destroy()
            speechEngine = null
        } catch (_: Exception) {}
        Log.d("AtminiWake", "WakeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
