package com.pranav.atmini

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.pranav.atmini.core.l0.L0ReflexSystem
import com.pranav.atmini.feature.app.AppDiscovery
import com.pranav.atmini.core.l0.command.CommandRegistry

class AtminiVoiceService : Service() {
    private var isListeningSession = false
    fun startSession() {
        isListeningSession = true
        Log.d("AtminiService", "Listening session started")
    }
    override fun onCreate() {
        super.onCreate()

        Log.d("AtminiService", "Voice Service Created")

        // FUTURE:
        // Start Porcupine wake-word engine here
    }
    fun onAudioText(text: String) {
        if (!isListeningSession) return

        Log.d("AtminiService", "Voice chunk: $text")

        val apps = AppDiscovery.getInstalledApps(this)
        val action = CommandRegistry.process(text, apps)

        L0ReflexSystem().execute(this, action)
    }
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d("AtminiService", "Voice Service Triggered")

        //val text = intent?.getStringExtra("voice_text")


        when (intent?.action) {

            "WAKE_SESSION_START" -> {
                startSession()
            }

            "VOICE_TEXT" -> {
                val text = intent.getStringExtra("voice_text")
                if (!text.isNullOrBlank()) {
                    onAudioText(text)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {

        // FUTURE:
        // Stop Porcupine here

        Log.d("AtminiService", "Voice Service Destroyed")

        super.onDestroy()
    }
}