package com.pranav.atmini

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.pranav.atmini.core.l0.L0ReflexSystem
import com.pranav.atmini.core.l0.AtminiBrain
import com.pranav.atmini.feature.app.AppDiscovery
import com.pranav.atmini.core.l0.command.CommandRegistry

class AtminiVoiceService : Service() {

    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    override fun onCreate() {
        super.onCreate()
        Log.d("AtminiService", "Voice Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("AtminiService", "Voice Service Triggered")

        // Example: voice text passed from somewhere (future integration)
        val text = intent?.getStringExtra("voice_text")

        if (!text.isNullOrBlank()) {

            val apps = AppDiscovery.getInstalledApps(this)

            val state = CommandRegistry.process(text, apps)

            Log.d("AtminiService", "Command: $state")

            val brain = AtminiBrain()

            val action = brain.process(text, apps)
            val reflex = L0ReflexSystem()
            reflex.execute(this, action)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AtminiService", "Voice Service Destroyed")
    }
}