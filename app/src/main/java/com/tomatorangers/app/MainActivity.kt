package com.tomatorangers.app

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.tomatorangers.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraHandler: CameraHandler
    private lateinit var detector: Detector
    private var savedImageUri: Uri? = null
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private var isLiveDetectionMode = true
    private var cameraExecutor: ExecutorService? = null
    private lateinit var liveDetectionHandler: LiveDetectionHandler

    // request multiple permissions
    private val activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
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

        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        viewBinding.mdswitch.setOnCheckedChangeListener { _, isChecked ->
            isLiveDetectionMode = isChecked
            if (isLiveDetectionMode) {
                switchToLiveDetection()
            } else {
                switchToImageProcessing()
            }
        }

        viewBinding.camBtn.setOnClickListener {
            cameraHandler.takePhoto { bitmap ->
                savedImageUri = cameraHandler.getSavedImageUri() // This should be set in takePhoto
                if (savedImageUri != null) {
                    detector.detect(bitmap)
                } else {
                    Log.e(TAG, "Saved image URI is null after taking photo")
                    Toast.makeText(this, "Failed to get saved image URI", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            liveDetectionHandler = LiveDetectionHandler(this, cameraExecutor, detector, viewBinding.viewFinder, cameraProvider)
            liveDetectionHandler.startLiveDetection()
            switchToLiveDetection()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchToLiveDetection() {
        cameraProvider.unbindAll()
        val rotation = viewBinding.viewFinder.display.rotation

        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .setTargetRotation(rotation)
            .build()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer.setAnalyzer(cameraExecutor!!) { imageProxy ->
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
            detector.detect(rotatedBitmap)
        }

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview.surfaceProvider = viewBinding.viewFinder.surfaceProvider
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun switchToImageProcessing() {
        cameraProvider.unbindAll()
        cameraHandler.startImageCapture(viewBinding.viewFinder.surfaceProvider)
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
                val bitmap = this.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }

                // Check if the bitmap was successfully decoded
                if (bitmap != null) {
                    val modifiedBitmap = Draw.drawBoundingBoxes(bitmap, boundingBoxes)

                    saveModifiedImage(modifiedBitmap)

                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Failed to decode bitmap: Bitmap is null")
                    runOnUiThread {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Log.e(TAG, "Saved image URI is null")
            runOnUiThread {
                Toast.makeText(this, "Saved image URI is null", Toast.LENGTH_SHORT).show()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
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
