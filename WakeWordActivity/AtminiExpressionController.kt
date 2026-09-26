package com.yourapp.atmini

import android.content.Context
import android.widget.ImageView

/**
 * Drives Atmini's gestures and expressions. This is the layer your app
 * code actually talks to: atmini.wave(), atmini.say("Hello!") { ... }.
 *
 * Expression switching swaps the whole vector drawable resource rather
 * than editing pathData at runtime — VectorDrawable doesn't expose a
 * public, version-safe API for rewriting a named group's path at runtime,
 * so the reliable approach is to ship one vector_atmini_<expression>.xml
 * per expression (identical body, different mouth/eyebrow paths only)
 * and swap resources. Duplicate vector_atmini.xml, change just the
 * <group android:name="mouth"> and eyebrow paths, and register each
 * variant in Expression.drawableRes below.
 */
class AtminiExpressionController(
    private val context: Context,
    private val imageView: ImageView
) {

    enum class Expression(val drawableRes: Int) {
        NEUTRAL(R.drawable.vector_atmini),
        HAPPY(R.drawable.vector_atmini_happy),       // create by duplicating vector_atmini.xml
        THINKING(R.drawable.vector_atmini_thinking),  // and editing the mouth/eyebrow paths
        TALKING(R.drawable.vector_atmini_talking)
    }

    private var currentExpression: Expression = Expression.NEUTRAL
    private var idleActive = false

    init {
        imageView.setImageResource(Expression.NEUTRAL.drawableRes)
    }

    // ---- Gestures -----------------------------------------------------

    /** Plays the wave. */
    fun wave() {
        idleActive = false
        imageView.setImageResource(currentExpression.drawableRes)
    }

    /** Shows a speech bubble via the supplied callback and switches to a talking expression. */
    fun say(text: String, durationMs: Long = 1800L, onBubble: (String) -> Unit) {
        onBubble(text)
        setExpression(Expression.TALKING)
        imageView.postDelayed({ setExpression(Expression.NEUTRAL) }, durationMs)
    }

    fun setExpression(expression: Expression) {
        currentExpression = expression
        if (!idleActive) {
            imageView.setImageResource(expression.drawableRes)
        }
        // If idle sway is running on the AVD, the next idle loop iteration
        // will pick up currentExpression's drawable when it restarts.
    }

    // ---- Idle loop ------------------------------------------------------

    /** Starts the idle. */
    fun startIdle() {
        idleActive = true
        imageView.setImageResource(currentExpression.drawableRes)
    }

    fun stopIdle() {
        idleActive = false
    }
}
