package com.pranav.atmini.feature.voice.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

class SpeechEngine(
    private val context: Context,
    private val onResult: (String?) -> Unit,
    private val onError: (Int) -> Unit = {}
) {

    private var isListening = false
    private var isDestroyed = false

    private val speechRecognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle) {
                isListening = false
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                onResult(text)
            }

            override fun onError(error: Int) {
                if (isDestroyed) return
                Log.e("AtminiSpeech", "Speech error: $error")
                isListening = false
                
                // Prevent recursive callback loops safely
                try {
                    onError(error)
                } catch (_: Exception) {}
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (isListening || isDestroyed) {
            Log.d("AtminiSpeech", "Already listening or destroyed")
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SpeechEngine", "Mic permission missing")
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                isListening = true
                speechRecognizer.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                Log.e("AtminiSpeech", "startListening failed", e)
            }
        }
    }

    fun stopListening() {
        try {
            isListening = false
            speechRecognizer.stopListening()
        } catch (_: Exception) {}
    }

    fun destroy() {
        isDestroyed = true
        isListening = false
        try {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        } catch (_: Exception) {}
    }
}
