package com.tomatorangers.app

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.tomatorangers.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService

class CameraHandler(
    private val liveDetectionHandler: LiveDetectionHandler,
    private val imageCapturingHandler: ImageCapturingHandler,
    private val viewBinding: ActivityMainBinding
) {
    // camera mode state machine
    fun startCamera(isLiveDetection: Boolean) {
        if (isLiveDetection) {
            imageCapturingHandler.stop()
            liveDetectionHandler.start()
        } else {
            liveDetectionHandler.stop()
            imageCapturingHandler.start(viewBinding.viewFinder.surfaceProvider)
        }
    }
}