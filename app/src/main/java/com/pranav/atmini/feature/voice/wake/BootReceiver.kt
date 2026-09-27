package com.pranav.atmini.feature.voice.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action
        Log.d("AtminiBoot", "Received system broadcast action: $action -> Starting WakeService")

        val serviceIntent = Intent(context, WakeService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("AtminiBoot", "Failed to start WakeService from BootReceiver", e)
        }
    }
}