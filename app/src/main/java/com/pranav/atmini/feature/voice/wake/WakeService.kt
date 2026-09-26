package com.pranav.atmini.feature.voice.wake

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pranav.atmini.core.VoiceCommandEngine
import com.pranav.atmini.feature.voice.speech.SpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WakeService : Service() {

    companion object {
        private const val CHANNEL_ID = "atmini_stealth_channel"
        private const val NOTIFICATION_ID = 999
    }

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var speechEngine: SpeechEngine? = null
    private val audioCapture = AudioCapture()
    private val vad = VoiceActivityDetector()
    private val tracker = SpeechWindowTracker()
    private val wakeBuffer = WakeBuffer()
    private val wakeGate = WakeGate(cooldownMs = 3000)
    private var isListeningSession = false

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

        createNotificationChannel()

        // Start foreground with minimal silent notification complying with OS restrictions
        startForeground(
            NOTIFICATION_ID,
            buildStealthNotification()
        )

        Log.d("AtminiWake", "WakeService started in stealth mode")
        startAudioProcessingLoop()
    }

    @SuppressLint("MissingPermission")
    private fun startAudioProcessingLoop() {
        audioCapture.start { frame ->
            if (isListeningSession) {
                return@start
            }

            val rms = vad.calculateRms(frame.samples)

            if (rms < 120.0 && wakeBuffer.size < 5) {
                return@start
            }

            wakeBuffer.add(frame)

            val speech = vad.isSpeech(frames = wakeBuffer.getWindow())
            val stable = tracker.update(speech)
            if (!stable) {
                return@start
            }

            if (wakeBuffer.size < 10) {
                return@start
            }

            val allowWakeFlag = wakeGate.allowWake(frame.timestamp)

            if (allowWakeFlag && !isListeningSession) {
                isListeningSession = true
                wakeBuffer.reset()
                Log.d("AtminiWake", "🚀 VOICE DETECTED -> LISTENING")

                audioCapture.stop()

                mainScope.launch {
                    val commandEngine = VoiceCommandEngine(applicationContext)

                    speechEngine = SpeechEngine(
                        context = applicationContext,
                        onResult = { recognizedText ->
                            Log.d("AtminiSpeech", "Heard Phrase: $recognizedText")

                            if (!recognizedText.isNullOrBlank()) {
                                val lower = recognizedText.lowercase().trim()

                                val heardBroadcast = Intent("ATMINI_VOICE_HEARD").apply {
                                    putExtra("voice_text", recognizedText)
                                    setPackage(packageName)
                                }
                                sendBroadcast(heardBroadcast)

                                if (lower.contains("atmini") || lower.contains("mini") || lower.contains("open") || lower.contains("call") || lower.contains("timer") || lower.contains("navigate") || lower.contains("alarm")) {
                                    val wakeBroadcast = Intent("ATMINI_WAKE_DETECTED").apply {
                                        putExtra("timestamp", System.currentTimeMillis())
                                        putExtra("voice_text", recognizedText)
                                        setPackage(packageName)
                                    }
                                    sendBroadcast(wakeBroadcast)

                                    val cleanedCommand = lower.replace("atmini", "").trim()
                                    if (cleanedCommand.isNotBlank()) {
                                        commandEngine.processTranscript(cleanedCommand) { statusMsg ->
                                            Log.d("AtminiSpeech", "Command Status: $statusMsg")
                                        }
                                    }
                                }
                            }
                            resumeBackgroundListening()
                        },
                        onError = { error ->
                            Log.e("AtminiSpeech", "Speech error: $error")
                            resumeBackgroundListening()
                        }
                    )
                    speechEngine?.startListening()
                }
            }
        }
    }

    private fun resumeBackgroundListening() {
        mainScope.launch {
            try {
                speechEngine?.destroy()
                speechEngine = null
            } catch (_: Exception) {}

            isListeningSession = false
            try {
                if (hasMicPermission()) {
                    startAudioProcessingLoop()
                }
            } catch (e: Exception) {
                Log.e("AtminiWake", "Failed to resume audio capture", e)
            }
        }
    }

    override fun onDestroy() {
        audioCapture.stop()
        try {
            speechEngine?.destroy()
            speechEngine = null
        } catch (_: Exception) {}
        Log.d("AtminiWake", "WakeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Atmini Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                description = "Background Voice Engine"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildStealthNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Atmini")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .build()
    }
}
