package com.tomatorangers.app

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tomatorangers.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraHandler: CameraHandler
    private lateinit var detector: Detector
    private var savedImageUri: Uri? = null

    // request multiple permissions
    private val activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                // permission handling { granted V rejected }
                var permissionGranted = true
                permissions.entries.forEach {
                    if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                        permissionGranted = false
                    }
                }
                if (!permissionGranted) {
                    Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT)
                            .show()
                } else {
                    startCamera()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // INITs
        cameraHandler = CameraHandler(this, viewBinding)
        detector = Detector(this, Constants.MODEL_PATH, Constants.LABEL_PATH, this)
        detector.setup()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        viewBinding.imageCaptureButton.setOnClickListener {
            cameraHandler.takePhoto { bitmap ->
                savedImageUri = cameraHandler.getSavedImageUri()
                detector.detect(bitmap)
            }
        }
    }

    private fun startCamera() {
        cameraHandler.startCamera(viewBinding.viewFinder.surfaceProvider)
    }

    /*
        SAVING MODIFIED LOGIC
    */

    override fun onEmptyDetect() {
        Log.d("Detection", "No objects detected")
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        Log.d(TAG, "Saved Image URI: $savedImageUri")

        savedImageUri?.let { uri ->
            try {
                val bitmap =
                        this.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }

                // Check if the bitmap was successfully decoded
                if (bitmap != null) {
                    val modifiedBitmap = Draw.drawBoundingBoxes(bitmap, boundingBoxes)

                    saveModifiedImage(modifiedBitmap)

                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Failed to decode bitmap: Bitmap is null")
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}", e)
                Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
            }
        }
                ?: run {
                    Log.e(TAG, "Saved image URI is null")
                    Toast.makeText(this, "Saved image URI is null", Toast.LENGTH_SHORT).show()
                }
    }

    private fun saveModifiedImage(bitmap: Bitmap) {
        // file name format
        val contentValues =
                ContentValues().apply {
                    put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "modified_image_${System.currentTimeMillis()}.jpg"
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
                    }
                }

        // saving logic
        val uri =
                this.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                )
        uri?.let {
            this.contentResolver.openOutputStream(it).use { outputStream ->
                if (outputStream != null) {
                    // Compress and save the bitmap
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    Toast.makeText(this, "Modified image saved successfully", Toast.LENGTH_SHORT)
                            .show()
                } else {
                    Toast.makeText(this, "Failed to save modified image", Toast.LENGTH_SHORT).show()
                }
            }
        }
                ?: run {
                    Toast.makeText(this, "Failed to create new image entry", Toast.LENGTH_SHORT)
                            .show()
                }
    }

    /* PERMISSION EME */
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() =
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(baseContext, it) ==
                        PackageManager.PERMISSION_GRANTED
            }

    companion object {
        private val REQUIRED_PERMISSIONS =
                mutableListOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                        )
                        .apply {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                        .toTypedArray()
    }
}
