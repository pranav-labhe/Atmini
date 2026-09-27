package com.pranav.atmini.feature.voice.wake

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCapture {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 2048
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        onFrame: (WakeAudioFrame) -> Unit
    ) {
        if (audioRecord != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val actualBufferSize = maxOf(BUFFER_SIZE, minBufferSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )
        } catch (e: Exception) {
            Log.e("AtminiAudio", "Failed to instantiate AudioRecord with MIC", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AtminiAudio", "AudioRecord failed to initialize. Releasing native handle.")
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null
            return
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("AtminiAudio", "Failed to start recording session", e)
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null
            return
        }

        captureJob = scope.launch {
            // Brief pause to allow system audio focus to release from SpeechRecognizer
            delay(300)

            val buffer = ShortArray(BUFFER_SIZE)
            Log.d("AtminiAudio", "AudioRecord recording thread started. SampleRate=$SAMPLE_RATE, Source=MIC")

            var zeroReadCount = 0
            while (isActive) {
                val record = audioRecord ?: break
                val read = record.read(buffer, 0, buffer.size)

                if (read > 0) {
                    zeroReadCount = 0
                    val frameCopy = buffer.copyOf(read)
                    onFrame(
                        WakeAudioFrame(
                            samples = frameCopy,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    Log.w("AtminiAudio", "⚠️ AudioRecord read returned non-positive value: $read")
                    zeroReadCount++
                    if (zeroReadCount > 20) {
                        Log.w("AtminiAudio", "Multiple error reads detected. Re-initializing AudioRecord...")
                        zeroReadCount = 0
                        try {
                            audioRecord?.stop()
                            audioRecord?.release()
                        } catch (_: Exception) {}
                        
                        delay(200)
                        try {
                            audioRecord = AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                actualBufferSize
                            )
                            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                                audioRecord?.startRecording()
                            }
                        } catch (e: Exception) {
                            Log.e("AtminiAudio", "Recovery re-init failed", e)
                        }
                    }
                    delay(20)
                }
            }
        }

        Log.d("AtminiWake", "Optimized audio capture started successfully with zero memory/CPU overhead")
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        try {
            scope.cancel()
        } catch (_: Exception) {}

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e("AtminiWake", "Error stopping AudioRecord", e)
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("AtminiWake", "Error releasing AudioRecord", e)
            }
            audioRecord = null
        }

        Log.d("AtminiWake", "Audio capture stopped & native resources freed cleanly.")
    }
}
