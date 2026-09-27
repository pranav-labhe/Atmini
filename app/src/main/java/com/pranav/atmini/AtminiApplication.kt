package com.pranav.atmini

import android.app.Application
import android.content.Intent
import android.util.Log
import com.pranav.atmini.feature.voice.wake.WakeService

class AtminiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AtminiApp", "🚀 AtminiApplication Process Started -> Auto-Launching WakeService in Background")

        try {
            val wakeIntent = Intent(this, WakeService::class.java)
            startService(wakeIntent)
        } catch (e: Exception) {
            Log.e("AtminiApp", "Failed to start WakeService from AtminiApplication", e)
        }
    }
}
