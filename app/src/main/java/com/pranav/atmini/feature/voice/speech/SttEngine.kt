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
import android.util.Log

interface SttEngine {
    val isAvailable: Boolean
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun listenContinuous(onResult: (String) -> Unit, onError: (Int) -> Unit = {})
    fun destroy()
}

class SystemOnDeviceSttEngine(private val context: Context) : SttEngine {
    private var recognizer: SpeechRecognizer? = null
    private var isListeningContinuous = false
    private var isDestroyed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override val isAvailable: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    override fun listenContinuous(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        if (!isAvailable || isDestroyed) {
            Log.e("AtminiSTT", "System SpeechRecognizer is unavailable or destroyed.")
            return
        }

        isListeningContinuous = true
        startListeningSession(onResult, onError)
    }

    private fun startListeningSession(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        mainHandler.post {
            if (isDestroyed) return@post

            try {
                recognizer?.destroy()
                recognizer = null

                recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            Log.d("AtminiSTT", "🎤 ON-DEVICE STT RESULT -> '$text'")
                            onResult(text)
                        }

                        if (isListeningContinuous && !isDestroyed) {
                            mainHandler.postDelayed({
                                startListeningSession(onResult, onError)
                            }, 300)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            Log.d("AtminiSTT", "⚡ PARTIAL STT -> '$partial'")
                        }
                    }

                    override fun onError(error: Int) {
                        Log.d("AtminiSTT", "On-Device STT Session Error/Timeout code: $error")
                        try {
                            onError(error)
                        } catch (_: Exception) {}

                        if (isListeningContinuous && !isDestroyed) {
                            mainHandler.postDelayed({
                                startListeningSession(onResult, onError)
                            }, 350)
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("AtminiSTT", "Failed to start on-device SpeechRecognizer", e)
                if (isListeningContinuous && !isDestroyed) {
                    mainHandler.postDelayed({
                        startListeningSession(onResult, onError)
                    }, 1000)
                }
            }
        }
    }

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        listenContinuous(
            onResult = { text -> onResult(text) },
            onError = { errCode -> onError("STT Error Code: $errCode") }
        )
    }

    override fun destroy() {
        isDestroyed = true
        isListeningContinuous = false
        mainHandler.post {
            try {
                recognizer?.cancel()
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

    override fun listenContinuous(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        onError(-1)
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
