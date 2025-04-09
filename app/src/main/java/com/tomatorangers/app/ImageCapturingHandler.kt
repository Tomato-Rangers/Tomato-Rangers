package com.tomatorangers.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

class ImageCapturingHandler(
    private val context: Context,
    private val preview: PreviewView
) {
    private var imageCapture: ImageCapture? = null
    private var savedImageUri: Uri? = null
    private var isCapturing: Boolean = false
    private var cameraProvider: ProcessCameraProvider? = null
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    var cameraControl: CameraControl? = null

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        isCapturing = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraControl = null
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = preview.surfaceProvider
            }

        imageCapture = ImageCapture.Builder()
            .build()

        try {
            cameraProvider?.unbindAll()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val camera = cameraProvider?.bindToLifecycle(
                context as AppCompatActivity,
                cameraSelector,
                preview,
                imageCapture)
            cameraControl = camera?.cameraControl
        } catch (exc: Exception) {
            Log.e("ImageCapturingHandler", "Use case binding failed", exc)
        }
    }


    // image processing logic
    fun takePhoto(onImageCaptured: (Bitmap) -> Unit) {
        val imageCapture = imageCapture ?: return


        if (isCapturing) {
            Log.w("ImageCapturingHandler", "Capture already in progress, ignoring new capture request.")
            return
        }

        isCapturing = true

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File.createTempFile("temp_image", ".jpg", context.cacheDir)
        ).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("ImageCapturingHandler", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(context, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    savedImageUri = output.savedUri
                    savedImageUri?.let {
                        try {
                            context.contentResolver.openInputStream(it)?.use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                if (bitmap != null) {
                                    onImageCaptured(bitmap)
                                } else {
                                    Log.e("ImageCapturingHandler", "Failed to decode bitmap: Bitmap is null")
                                    Toast.makeText(context, "Failed to decode image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageCapturingHandler", "Failed to load bitmap: ${e.message}", e)
                            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCapturing = false
                }
            }
        )
    }
}