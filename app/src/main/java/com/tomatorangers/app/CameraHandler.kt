package com.tomatorangers.app

import androidx.camera.core.CameraControl

class CameraHandler(
    private val liveDetectionHandler: LiveDetectionHandler,
    private val imageCapturingHandler: ImageCapturingHandler
) {
    var isFlash: Boolean = false
    private var cameraControl: CameraControl? = null

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