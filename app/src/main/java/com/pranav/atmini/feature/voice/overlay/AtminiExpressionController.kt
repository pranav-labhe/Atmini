package com.pranav.atmini.feature.voice.overlay

import android.content.Context
import android.widget.ImageView
import com.pranav.atmini.R

class AtminiExpressionController(
    private val context: Context,
    private val bodyView: ImageView,
    private val faceView: ImageView
) {

    enum class Expression(val drawableRes: Int) {
        NEUTRAL(R.drawable.ic_atmini_nobg),
        HAPPY(R.drawable.ic_atmini_nobg),
        THINKING(R.drawable.ic_atmini_nobg),
        TALKING(R.drawable.ic_atmini_nobg)
    }

    private var currentExpression: Expression = Expression.NEUTRAL
    private var idleActive = false

    init {
        bodyView.setImageResource(R.drawable.ic_atmini_nobg)
        faceView.setImageResource(Expression.NEUTRAL.drawableRes)
    }

    fun wave() {
        idleActive = false
        bodyView.setImageResource(currentExpression.drawableRes)
        faceView.setImageResource(currentExpression.drawableRes)
        startIdle()
    }

    fun say(text: String, durationMs: Long = 1800L, onBubble: (String) -> Unit) {
        onBubble(text)
        setExpression(Expression.TALKING)
        bodyView.postDelayed({ setExpression(Expression.NEUTRAL) }, durationMs)
    }

    fun sayBye(durationMs: Long = 1500L, onDismiss: () -> Unit) {
        setExpression(Expression.TALKING)
        bodyView.postDelayed({
            onDismiss()
        }, durationMs)
    }

    fun setExpression(expression: Expression) {
        currentExpression = expression
        bodyView.setImageResource(expression.drawableRes)
        faceView.setImageResource(expression.drawableRes)
    }

    fun startIdle() {
        idleActive = true
        bodyView.setImageResource(currentExpression.drawableRes)
        faceView.setImageResource(currentExpression.drawableRes)
    }

    fun stopIdle() {
        idleActive = false
        bodyView.animate().cancel()
        faceView.animate().cancel()
        bodyView.rotation = 0f
        bodyView.translationY = 0f
        bodyView.scaleX = 1.0f
        bodyView.scaleY = 1.0f
        faceView.rotation = 0f
        faceView.translationY = 0f
        faceView.scaleX = 1.0f
        faceView.scaleY = 1.0f
    }
}
