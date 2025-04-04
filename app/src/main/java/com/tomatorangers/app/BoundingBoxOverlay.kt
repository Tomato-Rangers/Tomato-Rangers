package com.tomatorangers.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.STROKE
        textSize = 50f
        textAlign = Paint.Align.CENTER
    }

    val boundingBoxes = mutableListOf<BoundingBox>()

    fun setBoundingBoxes(boxes: List<BoundingBox>) {
        boundingBoxes.clear()
        boundingBoxes.addAll(boxes)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boundingBoxes) {
            canvas.drawRect(
                box.x1 * width,
                box.y1 * height,
                box.x2 * width,
                box.y2 * height,
                paint
            )
            canvas.drawText(
                box.clsName,
                box.cx * width,
                box.y1 * height - 10,
                paint
            )
        }
    }
}