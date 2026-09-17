package com.tomatorangers.tomaito.camerax

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

class CameraHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    // regular camera essentials
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val preview = Preview.Builder().build()

    // image capture essentials
    private var imageCapture: ImageCapture? = null

    var cameraSelector by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
    var hasFlashUnit by mutableStateOf(true) // true for preview
    var flashEnabled by mutableStateOf(false)
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

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        preview.surfaceProvider = surfaceProvider
    }

    fun toggleFlashMode() {
        if (!hasFlashUnit) return

        flashEnabled = !flashEnabled

        imageCapture?.flashMode =
            if (flashEnabled) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
    }

    fun toggleTorch() {
        if (!hasFlashUnit) return

        torchEnabled = !torchEnabled
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    fun takePhoto(
        onPhotoSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit,
    ) {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS",
            Locale.getDefault()
        ).format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg"
            )

            put(
                MediaStore.Images.Media.MIME_TYPE, "image/jpg"
            )

            put(
                MediaStore.Images.Media.RELATIVE_PATH, "DCIM/tomAIto"
            )
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let(onPhotoSaved)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            })
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.unbindAll()

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
        )

        hasFlashUnit = camera!!.cameraInfo.hasFlashUnit()
    }
}
