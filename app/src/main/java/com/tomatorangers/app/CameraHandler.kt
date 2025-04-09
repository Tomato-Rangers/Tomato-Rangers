package com.tomatorangers.app

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector

class CameraHandler(
    private val liveDetectionHandler: LiveDetectionHandler,
    private val imageCapturingHandler: ImageCapturingHandler
) {
    var isFlash: Boolean = false
    private var cameraControl: CameraControl? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    // camera mode state machine
    fun startCamera(isLiveDetection: Boolean) {
        if (isLiveDetection) {
            imageCapturingHandler.stop()
            liveDetectionHandler.start()
        } else {
            liveDetectionHandler.stop()
            imageCapturingHandler.start()
        }
    }

    fun switchCamera(isLiveDetection: Boolean) {
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            lensFacing = CameraSelector.LENS_FACING_FRONT

            liveDetectionHandler.lensFacing = lensFacing
            imageCapturingHandler.lensFacing = lensFacing
        } else {
            lensFacing = CameraSelector.LENS_FACING_BACK

            liveDetectionHandler.lensFacing = lensFacing
            imageCapturingHandler.lensFacing = lensFacing
        }

        if (isLiveDetection) {
            liveDetectionHandler.stop()
            liveDetectionHandler.start()
        } else {
            imageCapturingHandler.stop()
            imageCapturingHandler.start()
        }
    }

    fun toggleFlash(isLiveDetection: Boolean) {
        cameraControl = getCameraControl(isLiveDetection)

        if (isFlash) {
            cameraControl?.enableTorch(false)
        } else {
            cameraControl?.enableTorch(true)
        }
        isFlash = !isFlash
    }

    private fun getCameraControl(isLiveDetection: Boolean): CameraControl? {
        return if (isLiveDetection) {
            liveDetectionHandler.cameraControl
        } else {
            imageCapturingHandler.cameraControl
        }
    }
}