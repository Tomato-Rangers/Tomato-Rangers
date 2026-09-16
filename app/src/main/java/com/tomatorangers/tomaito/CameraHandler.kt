package com.tomatorangers.tomaito

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner

class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val preview = Preview.Builder().build()
    var cameraSelector by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
    var torchEnabled by mutableStateOf(false)
        private set

    suspend fun startCamera() {
        cameraProvider = ProcessCameraProvider.awaitInstance(context)
        bindCamera()
    }

    fun flipCamera() {
        cameraSelector =
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

        // disable torch upon flipping
        torchEnabled = false

        bindCamera()
    }

    fun toggleTorch() {
        torchEnabled = !torchEnabled
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        preview.surfaceProvider = surfaceProvider
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        provider.unbindAll()

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
        )
    }
}
