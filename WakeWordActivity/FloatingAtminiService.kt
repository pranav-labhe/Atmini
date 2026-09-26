package com.yourapp.atmini

import android.app.*
import android.content.Intent
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

/**
 * Foreground service that draws Atmini as a WindowManager overlay so she
 * appears above whatever app is currently in front (home screen, Chrome,
 * WhatsApp, a game, etc).
 *
 * Requires in AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *   <service android:name=".FloatingAtminiService" android:exported="false" />
 *
 * Requires runtime: the user must grant "Display over other apps" via
 *   Settings.canDrawOverlays(context) / ACTION_MANAGE_OVERLAY_PERMISSION
 * before this service is started — check that from your Activity first.
 */
class FloatingAtminiService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootView: FrameLayout
    private lateinit var atminiView: ImageView
    private lateinit var bubbleView: TextView
    private lateinit var atmini: AtminiExpressionController
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        rootView = FrameLayout(this)

        bubbleView = TextView(this).apply {
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(24, 12, 24, 12)
            textSize = 13f
            visibility = View.GONE
        }
        atminiView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(200.dp, 346.dp).apply {
                gravity = Gravity.BOTTOM
            }
        }

        rootView.addView(atminiView)
        rootView.addView(bubbleView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END })

        atmini = AtminiExpressionController(this, atminiView)

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
        atmini.startIdle()
    }

    /** Lets the user drag Atmini to reposition her anywhere on screen. */
    private fun makeDraggable() {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        atminiView.setOnTouchListener { _, event ->
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
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
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

    /** Simple tap-to-interact hook — wire this to your assistant logic. */
    private fun onAtminiTapped() {
        atmini.wave()
        showBubble("Hi! What can I do?")
    }

    /** Public API other parts of your app can call via a bound interface or broadcast. */
    fun showBubble(text: String, durationMs: Long = 2500L) {
        atmini.say(text, durationMs) { message ->
            bubbleView.text = message
            bubbleView.visibility = View.VISIBLE
            bubbleView.postDelayed({ bubbleView.visibility = View.GONE }, durationMs)
        }
    }

    fun wave() = atmini.wave()
    fun setExpression(expression: AtminiExpressionController.Expression) = atmini.setExpression(expression)

    override fun onDestroy() {
        super.onDestroy()
        atmini.stopIdle()
        if (::rootView.isInitialized) windowManager.removeView(rootView)
    }

    private fun buildNotification(): Notification {
        val channelId = "atmini_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Atmini overlay", NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Atmini is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
    }
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
