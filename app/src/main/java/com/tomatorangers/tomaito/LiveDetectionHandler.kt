package com.tomatorangers.tomaito

import android.util.Log

class LiveDetectionHandler(
    private val liveDraw: LiveDraw
) : DetectionHandler.DetectorListener {
    fun clear() {
        liveDraw.boundingBoxes.clear()
        liveDraw.invalidate()
    }

    override fun onEmptyDetect() {
        Log.d("LiveDetection", "Clearing live view")

        clear()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>) {
        Log.d("LiveDetection", "Drawing live boxes")

        liveDraw.setBoundingBoxes(boundingBoxes)
    }
}