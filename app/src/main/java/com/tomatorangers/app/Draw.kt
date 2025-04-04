package com.tomatorangers.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object Draw {
    fun drawBoundingBoxes(bitmap: Bitmap, boundingBoxes: List<BoundingBox>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
            textSize = 50f
            textAlign = Paint.Align.CENTER
        }

        for (box in boundingBoxes) {
            canvas.drawRect(
                box.x1 * bitmap.width,
                box.y1 * bitmap.height,
                box.x2 * bitmap.width,
                box.y2 * bitmap.height,
                paint
            )

            canvas.drawText(
                box.clsName,
                box.cx * bitmap.width,
                box.y1 * bitmap.height - 10,
                paint
            )
        }

        return mutableBitmap
    }
}