package com.tomatorangers.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHandler(private val context: Context) {
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(previewSurfaceProvider: Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // bind the lifecycle of cams to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewSurfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .build()

            try {
                // unbinding bfr rebinding
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    (context as AppCompatActivity),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // media storing
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // create output | contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // setup image capture listener
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                // error handling
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    companion object {
        private const val TAG = "CameraX"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}