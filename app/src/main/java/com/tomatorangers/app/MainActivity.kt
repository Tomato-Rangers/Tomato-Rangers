package com.tomatorangers.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tomatorangers.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), DetectionHandler.DetectorListener {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraHandler: CameraHandler
    private lateinit var liveDetectionHandler: LiveDetectionHandler
    private lateinit var imageCapturingHandler: ImageCapturingHandler
    private lateinit var detectionHandler: DetectionHandler
    private lateinit var detector: Detector
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private var cameraExecutor: ExecutorService? = null
    private var lastCapturedBitmap: Bitmap? = null
    private var isLiveDetection: Boolean = false

    // request multiple permissions
    private val activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
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
                    cameraHandler.startCamera(isLiveDetection)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // INITs
        boundingBoxOverlay = findViewById(R.id.boundingBoxOverlay)
        cameraExecutor = Executors.newSingleThreadExecutor()
        detectionHandler = DetectionHandler(this, Constants.MODEL_PATH, Constants.LABEL_PATH, this)
        detector = Detector(this, Constants.MODEL_PATH, Constants.LABEL_PATH, this.detectionHandler)
        imageCapturingHandler = ImageCapturingHandler(this, viewBinding.preview)
        liveDetectionHandler = LiveDetectionHandler(this, cameraExecutor!!, detector, viewBinding.boundingBoxOverlay, viewBinding.preview)
        cameraHandler = CameraHandler(liveDetectionHandler, imageCapturingHandler)

        if (allPermissionsGranted()) {
            viewBinding.detectionResultTextView.isVisible = isLiveDetection
            cameraHandler.startCamera(isLiveDetection)
        } else {
            requestPermissions()
        }


        viewBinding.mdswitch.setOnCheckedChangeListener { _, isChecked ->
            isLiveDetection = isChecked
            viewBinding.detectionResultTextView.isVisible = isLiveDetection
            viewBinding.camBtn.isVisible = !isLiveDetection
            cameraHandler.startCamera(isLiveDetection)
            cameraHandler.isFlash = false
        }

        viewBinding.camBtn.setOnClickListener {
            imageCapturingHandler.takePhoto { bitmap ->
                lastCapturedBitmap = bitmap
                detectionHandler.detect(bitmap)
            }
        }

        viewBinding.flashBtn.setOnClickListener{
            cameraHandler.toggleFlash(isLiveDetection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        detector.clear()
        liveDetectionHandler.stop()
    }

    override fun onEmptyDetect() {
        Log.d("Detection", "No objects detected")
        runOnUiThread {
            viewBinding.detectionResultTextView.text = getString(R.string.no_objects_detected)
        }
        boundingBoxOverlay.boundingBoxes.clear()
        boundingBoxOverlay.invalidate()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        Log.d("Detection", "Detected ${boundingBoxes.size} objects in $inferenceTime ms")

        runOnUiThread {
            viewBinding.detectionResultTextView.text = getString(R.string.detection_result, boundingBoxes.size)

            if (!isLiveDetection) {
                if (lastCapturedBitmap != null) {
                    val modifiedBitmap = Draw.drawBoundingBoxes(lastCapturedBitmap!!, boundingBoxes)
                    viewBinding.detectedImageView.setImageBitmap(modifiedBitmap)
                    detectionHandler.saveModifiedImage(modifiedBitmap)
                } else {
                    Log.e("Detection", "No captured bitmap available for processing.")
                    Toast.makeText(this, "No image available for detection.", Toast.LENGTH_SHORT).show()
                }
            } else {
                boundingBoxOverlay.setBoundingBoxes(boundingBoxes)
            }
        }
    }

    /* PERMISSION EME */
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
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
