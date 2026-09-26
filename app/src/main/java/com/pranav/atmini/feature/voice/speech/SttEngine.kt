package com.pranav.atmini.feature.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SttEngine {
    val isAvailable: Boolean
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun destroy()
}

class SystemOnDeviceSttEngine(private val context: Context) : SttEngine {
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!isAvailable) {
            onError("System SpeechRecognizer is unavailable.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onResult(matches[0])
                        } else {
                            onError("No speech detected.")
                        }
                    }

                    override fun onError(error: Int) {
                        onError("STT Error Code: $error")
                    }

                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                onError("Failed to start SpeechRecognizer: ${e.message}")
            }
        }
    }

    override fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                recognizer?.destroy()
                recognizer = null
            } catch (_: Exception) {}
        }
    }
}

class LocalWhisperSttEngine : SttEngine {
    override val isAvailable: Boolean = false

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("Local offline Whisper/Vosk STT engine is scheduled for Phase 2.")
    }

    override fun destroy() {}
}

object SttEngineFactory {
    fun create(context: Context): SttEngine {
        val systemEngine = SystemOnDeviceSttEngine(context)
        return if (systemEngine.isAvailable) {
            systemEngine
        } else {
            LocalWhisperSttEngine()
        }
    }
}
