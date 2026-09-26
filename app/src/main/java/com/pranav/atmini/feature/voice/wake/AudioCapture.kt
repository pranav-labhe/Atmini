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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        onFrame: (WakeAudioFrame) -> Unit
    ) {
        if (audioRecord != null) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val actualBufferSize = maxOf(BUFFER_SIZE, minBufferSize)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize
        )

        audioRecord?.startRecording()

        captureJob = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(BUFFER_SIZE)
            Log.d("AtminiAudio", "AudioRecord recording thread started. SampleRate=$SAMPLE_RATE, BufferSize=$BUFFER_SIZE")

            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    val copy = buffer.copyOf(read)
                    onFrame(
                        WakeAudioFrame(
                            samples = copy,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    delay(10)
                }
            }
        }

        Log.d("AtminiWake", "Audio capture started")
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AtminiWake", "Error stopping audio record", e)
        }
        audioRecord = null

        Log.d("AtminiWake", "Audio capture stopped")
    }
}
