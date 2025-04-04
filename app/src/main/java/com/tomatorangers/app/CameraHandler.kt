package com.tomatorangers.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.tomatorangers.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CameraHandler(private val context: Context, private val binding: ActivityMainBinding) {
    private var imageCapture: ImageCapture? = null
    private var savedImageUri: Uri? = null

    private fun startCamera(previewSurfaceProvider: Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // bind the lifecycle of cams to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
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

    fun startImageCapture(surfaceProvider: Preview.SurfaceProvider) {
        startCamera(surfaceProvider)
    }

    fun takePhoto(onImageCaptured: (Bitmap) -> Unit) {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            File.createTempFile("temp_image", ".jpg", context.cacheDir)
        ).build()

        // Setup image capture listener
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(context, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    savedImageUri = output.savedUri

                    // Load the bitmap from the captured image
                    val uri = output.savedUri
                    uri?.let {
                        try {
                            context.contentResolver.openInputStream(it)?.use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                if (bitmap != null) {
                                    onImageCaptured(bitmap) // Pass the bitmap to the callback
                                } else {
                                    Log.e(TAG, "Failed to decode bitmap: Bitmap is null")
                                    Toast.makeText(context, "Failed to decode image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load bitmap: ${e.message}", e)
                            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    fun getSavedImageUri(): Uri? {
        return savedImageUri
    }

    companion object {
        private const val TAG = "CameraX"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}