package com.pranav.atmini.feature.voice.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Intent
import com.pranav.atmini.feature.voice.overlay.FloatingAtminiService

class AtminiVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AtminiVoiceInteractionSession(this)
    }
}

class AtminiVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Launch the floating overlay when the assistant is invoked by the system
        val intent = Intent(context, FloatingAtminiService::class.java)
        context.startService(intent)
        // Hide the session panel so our floating overlay takes over cleanly
        hide()
    }
}
