package com.tomatorangers.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    val boundingBoxes = mutableListOf<BoundingBox>()

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
    }

    // for text bg
    private val textBounds = Rect()
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        alpha = 150
    }

    @SuppressLint("DefaultLocale")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boundingBoxes) {
            paint.color = getPaintColor(box.cls)
            paint.textSize = (box.y2 * height - box.y1 * height) *
                    (resources.getDimension(R.dimen.textSizeFactor) / resources.displayMetrics.density)
            paint.strokeWidth = 8f

            // center dot
            val centerX = box.cx * width
            val centerY = (box.y1 * height + box.y2 * height) / 2
            canvas.drawCircle(centerX, centerY, 5f, paint)

            canvas.drawRect(
                box.x1 * width,
                box.y1 * height,
                box.x2 * width,
                box.y2 * height,
                paint
            )

            val vitality: String = if (
                box.vit == DetectionHandler.Vitality.UNDETECTED ||
                box.vit == DetectionHandler.Vitality.UNKNOWN
                ) {
                ""
            } else {
                box.vit.toString()
            }

            val displayText = "$vitality ${box.clsName} (Confidence: ${String.format("%.2f", box.cnf * 100)})"
            paint.getTextBounds(displayText, 0, displayText.length, textBounds)
            paint.strokeWidth = paint.textSize * (resources.getDimension(R.dimen.strokeFactor) / resources.displayMetrics.density)

            // adjust text info to bottom if it exceeds top screen
            val textYPosition: Float
            val textBackgroundYPosition: Float
            if (box.y1 * height < textBounds.height() + 20) {
                textYPosition = box.y2 * height + textBounds.height() + 9
                textBackgroundYPosition = textYPosition - textBounds.height()
            } else {
                textYPosition = box.y1 * height - 11
                textBackgroundYPosition = box.y1 * height - textBounds.height() - 20
            }

            // text bg
            canvas.drawRect(
                box.cx * width - textBounds.width() / 2 - 10,
                textBackgroundYPosition,
                box.cx * width + textBounds.width() / 2 + 10,
                textBackgroundYPosition + textBounds.height() + 20,
                bgPaint
            )

            // text
            paint.color = Color.WHITE
            canvas.drawText(
                displayText,
                box.cx * width,
                textYPosition,
                paint
            )
        }
    }

    fun setBoundingBoxes(boxes: List<BoundingBox>) {
        boundingBoxes.clear()
        boundingBoxes.addAll(boxes)
        invalidate()
    }

    // interchange color depending on object class
    private fun getPaintColor(cls: Int): Int {
        return if (cls == 0) {
            Color.MAGENTA
        } else {
            Color.BLUE
        }
    }
}