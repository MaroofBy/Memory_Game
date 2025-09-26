package com.example.firstgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class OutlineTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val outlinePaint: Paint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f // Thickness of the outline
            color = 0xFFFFFFFF.toInt() // Default color is white
        }
    }

    override fun onDraw(canvas: Canvas) {
        val originalColor = currentTextColor
        outlinePaint.color = 0xFF000000.toInt() // Black outline
        outlinePaint.strokeWidth = 2.0f
        paint.color = 0xFFFFFFFF.toInt() // White text

        // Draw the outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = outlinePaint.strokeWidth
        super.onDraw(canvas)

        // Draw the filled text
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        super.onDraw(canvas)
    }
}