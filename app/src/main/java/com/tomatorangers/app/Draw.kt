package com.tomatorangers.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

object Draw {
    private val textBounds = Rect()
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        alpha = 150
    }

    @SuppressLint("DefaultLocale")
    fun drawBoundingBoxes(bitmap: Bitmap, boundingBoxes: List<BoundingBox>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            textAlign = Paint.Align.CENTER
        }

        for (box in boundingBoxes) {
            paint.color = getPaintColor(box.cls)
            paint.textSize = (box.y2 * bitmap.height - box.y1 * bitmap.height) * 0.06f

            // center dot
            val centerX = box.cx * bitmap.width
            val centerY = (box.y1 * bitmap.height + box.y2 * bitmap.height) / 2
            canvas.drawCircle(centerX, centerY, 10f, paint)

            canvas.drawRect(
                box.x1 * bitmap.width,
                box.y1 * bitmap.height,
                box.x2 * bitmap.width,
                box.y2 * bitmap.height,
                paint
            )

            val displayText = "${box.clsName} (Confidence: ${String.format("%.2f", box.cnf * 100)})"
            paint.getTextBounds(displayText, 0, displayText.length, textBounds)
            paint.strokeWidth = paint.textSize * 0.1f

            // place text info at bottom if top exceeds
            val textYPosition: Float
            val textBackgroundYPosition: Float
            if (box.y1 * bitmap.height < textBounds.height() + 20) {
                textYPosition = box.y2 * bitmap.height + textBounds.height() + 9
                textBackgroundYPosition = textYPosition - textBounds.height()
            } else {
                textYPosition = box.y1 * bitmap.height - 11
                textBackgroundYPosition = box.y1 * bitmap.height - textBounds.height() - 20
            }


            // text bg
            canvas.drawRect(
                box.cx * bitmap.width - textBounds.width() / 2 - 10,
                textBackgroundYPosition,
                box.cx * bitmap.width + textBounds.width() / 2 + 10,
                textBackgroundYPosition + textBounds.height() + 20,
                bgPaint
            )

            paint.color = Color.WHITE
            canvas.drawText(
                displayText,
                box.cx * bitmap.width,
                textYPosition,
                paint
            )
        }

        return mutableBitmap
    }

    // interchange color depending on object class
    private fun getPaintColor(cls: Int): Int {
        return if (cls % 2 != 0 ) {
            Color.MAGENTA
        } else {
            Color.BLUE
        }
    }
}