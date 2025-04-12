package com.tomatorangers.tomaito

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.tomatorangers.tomaito.databinding.ActivityMainBinding
import java.io.File

class ImageCapturingHandler(
    private val context: Context,
    private val detectionHandler: DetectionHandler,
    private val viewBinding: ActivityMainBinding
) : DetectionHandler.DetectorListener {
    var imageCapture: ImageCapture? = null
    var bitmap: Bitmap? = null

    fun takePhoto(onImageCaptured: (Bitmap) -> Unit) {
        Log.d("ImageCapturingHandler", "Taking photo")

        val imageCapture = imageCapture ?: return

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
                    output.savedUri?.let {
                        try {
                            context.contentResolver.openInputStream(it)?.use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                if (bitmap != null) {
                                    onImageCaptured(bitmap)
                                    detectionHandler.detect(bitmap)
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
                }
            }
        )
    }

    private fun saveModifiedImage(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "modified_image_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, context.getString(R.string.folder_path))
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    Toast.makeText(context,
                        context.getString(R.string.object_count) + context.getString(R.string.image_saved), Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } ?: run {
            Toast.makeText(context, "Failed to create new image entry", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEmptyDetect() {
        bitmap = null
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>) {
        Log.d("ImageCapturing", "Drawing boxes")
        val modifiedBitmap = ImageDraw.drawBoundingBoxes(bitmap!!, boundingBoxes)
        Log.d("ImageCapturing", "Boxes drawn")

        viewBinding.detectedImageView.setImageBitmap(modifiedBitmap)

        Log.d("ImageCapturing", "Saving image")
        saveModifiedImage(modifiedBitmap)
        Log.d("ImageCapturing", "Image saved")
    }
}