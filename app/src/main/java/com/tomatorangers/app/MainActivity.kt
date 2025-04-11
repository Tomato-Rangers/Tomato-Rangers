package com.tomatorangers.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
                    Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
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

        detectionHandler = DetectionHandler(
            this,
            Constants.TOMATO_MODEL_PATH,
            Constants.ORANGE_MODEL_PATH,
            Constants.MODEL_PATH,
            Constants.LABEL_PATH,
            Constants.RIPENESS_LABEL_PATH,
            this,
            boundingBoxOverlay)

        detector = Detector(
            this,
            Constants.MODEL_PATH,
            Constants.LABEL_PATH,
            this.detectionHandler)

        imageCapturingHandler = ImageCapturingHandler(
            this,
            viewBinding.preview,
            Constants.MODEL_PATH,
            Constants.TOMATO_MODEL_PATH,
            Constants.ORANGE_MODEL_PATH,
            Constants.LABEL_PATH,
            Constants.RIPENESS_LABEL_PATH,
            this,
            this
        )

        liveDetectionHandler = LiveDetectionHandler(
            this, cameraExecutor!!,
            detector,
            viewBinding.boundingBoxOverlay,
            viewBinding.preview,
            detectionHandler)

        cameraHandler = CameraHandler(liveDetectionHandler, imageCapturingHandler)

        if (allPermissionsGranted()) {
            viewBinding.detectionResultTextView.isVisible = isLiveDetection
            cameraHandler.startCamera(isLiveDetection)
        } else {
            requestPermissions()
        }

        uiSetup()
    }

    private fun uiSetup() {
        viewBinding.mdswitch.setOnCheckedChangeListener { _, isChecked ->
            isLiveDetection = isChecked
            detectionHandler.isLiveDetection = isLiveDetection

            cameraHandler.startCamera(isLiveDetection)
            cameraHandler.isFlash = false

            // CAPTURE BUTTON VISIBILITY
            viewBinding.detectionResultTextView.isVisible = isLiveDetection
            viewBinding.camBtn.isVisible = !isLiveDetection
        }

        viewBinding.camBtn.setOnClickListener {
            imageCapturingHandler.takePhoto { bitmap ->
                lastCapturedBitmap = bitmap
                detectionHandler.detect(bitmap)
            }
        }

        viewBinding.flashBtn.setOnClickListener {
            cameraHandler.toggleFlash(isLiveDetection)
        }

        viewBinding.galleryBtn.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }

        viewBinding.swapBtn.setOnClickListener {
            cameraHandler.switchCamera(isLiveDetection)
            cameraHandler.isFlash = false
        }

        viewBinding.settingsBtn.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.sliderValueText -> {
                    showSliderDialog()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showSliderDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Adjust Confidence Level")
            .setView(R.layout.dialog_slider)
            .create()
        dialog.setOnShowListener {
            val sliderValueText = dialog.findViewById<TextView>(R.id.sliderValueText)
            val confidenceSeekBar = dialog.findViewById<SeekBar>(R.id.confidenceSeekBar)
            val closeButton = dialog.findViewById<Button>(R.id.closeButton)

            confidenceSeekBar?.progress = (detector.confidenceThreshold * 100).toInt()
            sliderValueText?.text =
                getString(R.string.confidence_level, confidenceSeekBar?.progress)

            confidenceSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, from:Boolean) {
                    if (isLiveDetection) {
                        liveDetectionHandler.stop()
                    } else {
                        imageCapturingHandler.stop()
                    }

                    detector.confidenceThreshold = progress / 100f
                    sliderValueText?.text = getString(R.string.confidence_level_progress, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    detectionHandler.setup()

                    boundingBoxOverlay.boundingBoxes.clear()
                    boundingBoxOverlay.invalidate()
                }
            })

            closeButton?.setOnClickListener {
                if (isLiveDetection) {
                    liveDetectionHandler.start()
                } else {
                    imageCapturingHandler.start()
                }
                boundingBoxOverlay.boundingBoxes.clear()
                boundingBoxOverlay.invalidate()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        detector.clear()
        liveDetectionHandler.stop()
    }


    /*
        IMAGE PROCESSING DETECTION LOGIC WENT HERE
        IDK WHY BUT ITS WORKING SO...
        KEEP IT.
        MIGHT REFACTOR IT AFTER THE DEADLINE
     */
    override fun onEmptyDetect() {
        Log.d("ImageCapturing", getString(R.string.no_objects_detected))

        runOnUiThread {
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            viewBinding.detectionResultTextView.text = getString(R.string.detection_result, boundingBoxes.size)

            Log.d("LOG", "Last captured bitmap: $lastCapturedBitmap")

            if (lastCapturedBitmap != null) {
                val modifiedBitmap = Draw.drawBoundingBoxes(lastCapturedBitmap!!, boundingBoxes)
                viewBinding.detectedImageView.setImageBitmap(modifiedBitmap)
                detectionHandler.saveModifiedImage(modifiedBitmap)
            } else {
                Log.e("Detection", "No captured bitmap available for processing.")
                Toast.makeText(this, "No image available for detection.", Toast.LENGTH_SHORT).show()
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
            ).apply {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }.toTypedArray()
    }
}
