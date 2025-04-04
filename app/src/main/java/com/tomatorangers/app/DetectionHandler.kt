package com.tomatorangers.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

class DetectionHandler(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val listener: DetectorListener
) : Detector.DetectorListener {
    private lateinit var detector: Detector

    init {
        setup()
    }

    private fun setup() {
        detector = Detector(context, modelPath, labelPath, this)
        detector.setup()
    }

    fun detect(bitmap: Bitmap) {
        detector.detect(bitmap)
    }

    override fun onEmptyDetect() {
        listener.onEmptyDetect()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        listener.onDetect(boundingBoxes, inferenceTime)
    }

    fun saveModifiedImage(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "modified_image_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
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
                    Toast.makeText(context, "Modified image saved successfully", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(context, "Failed to save modified image", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } ?: run {
            Toast.makeText(context, "Failed to create new image entry", Toast.LENGTH_SHORT).show()
        }
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }
}