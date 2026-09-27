package com.pranav.atmini.feature.voice.overlay

import com.pranav.atmini.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.pranav.atmini.core.VoiceCommandEngine
import com.pranav.atmini.feature.voice.speech.SpeechEngine
import kotlin.math.abs

class FloatingAtminiService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootView: FrameLayout
    private lateinit var bodyView: ImageView
    private lateinit var faceView: ImageView
    private lateinit var bubbleView: TextView
    private lateinit var atmini: AtminiExpressionController
    private lateinit var params: WindowManager.LayoutParams
    private var speechEngine: SpeechEngine? = null
    private val commandEngine by lazy { VoiceCommandEngine(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        rootView = FrameLayout(this)

        bubbleView = TextView(this).apply {
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(24, 12, 24, 12)
            textSize = 13f
            visibility = View.GONE
        }
        bodyView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(250.dp, 250.dp).apply {
                gravity = Gravity.BOTTOM
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_atmini_nobg)
        }
        faceView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(250.dp, 250.dp).apply {
                gravity = Gravity.BOTTOM
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_atmini_nobg)
        }

        rootView.addView(bodyView)
        rootView.addView(faceView)
        rootView.addView(bubbleView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END })

        atmini = AtminiExpressionController(this, bodyView, faceView)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        windowManager.addView(rootView, params)
        makeDraggable()
        
        bodyView.setOnLongClickListener {
            showBubble("Goodbye! Closing Atmini.")
            bodyView.postDelayed({
                stopSelf()
            }, 1500L)
            true
        }

        atmini.startIdle()
    }

    private fun makeDraggable() {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bodyView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onAtminiTapped()
                    true
                }
                else -> false
            }
        }
    }

    private fun onAtminiTapped() {
        atmini.wave()
        showBubble("Listening...", 4000L)

        speechEngine?.destroy()
        speechEngine = SpeechEngine(
            context = applicationContext,
            onResult = { recognizedText ->
                if (!recognizedText.isNullOrBlank()) {
                    showBubble(recognizedText, 3000L)
                    val cleanedCommand = recognizedText.lowercase()
                        .replace("atmini", "")
                        .replace("mini", "")
                        .trim()

                    if (cleanedCommand.isNotBlank()) {
                        commandEngine.processTranscript(cleanedCommand) { statusMsg ->
                            showBubble(statusMsg, 3000L)
                        }
                    }
                } else {
                    showBubble("Didn't catch that.", 2000L)
                }
            },
            onError = { errCode ->
                showBubble("Listening ended.", 1500L)
            }
        )
        speechEngine?.startListening()
    }

    fun showBubble(text: String, durationMs: Long = 2500L, onClick: (() -> Unit)? = null) {
        atmini.say(text, durationMs) { message ->
            bubbleView.text = message
            bubbleView.visibility = View.VISIBLE
            if (onClick != null) {
                bubbleView.setOnClickListener { onClick() }
            } else {
                bubbleView.setOnClickListener(null)
            }
            bubbleView.postDelayed({ 
                bubbleView.visibility = View.GONE
                bubbleView.setOnClickListener(null)
            }, durationMs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        atmini.stopIdle()
        try {
            speechEngine?.destroy()
            speechEngine = null
        } catch (_: Exception) {}
        if (::rootView.isInitialized) windowManager.removeView(rootView)

        val intent = Intent("ATMINI_OVERLAY_CLOSED").apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}

private val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()
