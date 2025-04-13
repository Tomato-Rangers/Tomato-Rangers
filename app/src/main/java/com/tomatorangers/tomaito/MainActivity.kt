package com.tomatorangers.tomaito

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import com.tomatorangers.tomaito.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraHandler: CameraHandler
    private lateinit var detectionHandler: DetectionHandler
    private lateinit var imageCapturingHandler: ImageCapturingHandler
    private lateinit var liveDetectionHandler: LiveDetectionHandler

    var currentCameraMode: CameraHandler.CameraMode = CameraHandler.CameraMode.IMAGE_CAPTURE
    var isSwitchingMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        setup()
        permissionSetup()
        uiSetup()
    }

    private fun permissionSetup() {
        fun allPermissionsGranted() =
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
            }

        val activityResultLauncher =
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
                    viewBinding.root.post {
                        cameraHandler.startInitialCamera()
                        cameraHandler.startCamera(CameraHandler.CameraMode.IMAGE_CAPTURE)// default startup mode
                    }
                }
            }

        // Request permissions if not already granted
        if (!allPermissionsGranted()) {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            viewBinding.root.post {
                cameraHandler.startInitialCamera()
                cameraHandler.startCamera(CameraHandler.CameraMode.IMAGE_CAPTURE) // default startup mode
            }
        }
    }



    private fun setup() {
        Log.d("MainActivity", "Setting up handlers")

        liveDetectionHandler = LiveDetectionHandler(viewBinding.liveDraw)

        detectionHandler = DetectionHandler(
            this,
            this,
            liveDetectionHandler,
            viewBinding
        )

        imageCapturingHandler = ImageCapturingHandler(
            this,
            detectionHandler,
            viewBinding
        )

        cameraHandler = CameraHandler(
            this,
            viewBinding.preview,
            detectionHandler,
            imageCapturingHandler
        )

        Log.d("MainActivity", "Handlers DONE")
    }

    private fun uiSetup() {
        Log.d("MainActivity", "Setting up UI listeners")

        viewBinding.captureBtn.setOnClickListener {
            imageCapturingHandler.takePhoto { bitmap ->
                imageCapturingHandler.bitmap = bitmap
            }
        }

        viewBinding.flashBtn.setOnClickListener {
            // ui update
            if (cameraHandler.toggleFlash()) {
                viewBinding.flashBtn.setBackgroundResource(R.drawable.flash_on)
            } else {
                viewBinding.flashBtn.setBackgroundResource(R.drawable.flash_off)
            }
        }

        // open native gallery app on click
        viewBinding.galleryBtn.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))

            // reset flash
            viewBinding.root.post {
                cameraHandler.isFlash = false
                viewBinding.flashBtn.setBackgroundResource(R.drawable.flash_off)
            }
        }

        viewBinding.modeSwitch.setOnClickListener {
            isSwitchingMode = true

            currentCameraMode = if (currentCameraMode == CameraHandler.CameraMode.LIVE) {
                // to avoid image saving on detection delay
                viewBinding.root.postDelayed({
                    isSwitchingMode = false
                }, 3000)

                viewBinding.liveDraw.visibility = View.GONE
                CameraHandler.CameraMode.IMAGE_CAPTURE
            } else {
                liveDetectionHandler.clear()
                viewBinding.liveDraw.visibility = View.VISIBLE
                isSwitchingMode = false
                CameraHandler.CameraMode.LIVE
            }

            cameraHandler.startCamera(currentCameraMode)
        }

        viewBinding.settingsBtn.setOnClickListener { view -> showPopupMenu(view) }

        viewBinding.switchCamBtn.setOnClickListener {
            cameraHandler.switchCamera(currentCameraMode)

            // reset flash
            viewBinding.root.post {
                if (cameraHandler.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    cameraHandler.isFlash = false
                    viewBinding.flashBtn.visibility = View.GONE
                    viewBinding.flashBtn.setBackgroundResource(R.drawable.flash_off)
                } else {
                    viewBinding.flashBtn.visibility = View.VISIBLE
                }
            }
        }

        Log.d("MainActivity", "UI listeners DONE")
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

            confidenceSeekBar?.progress = (detectionHandler.confidenceThreshold * 100).toInt()
            sliderValueText?.text = getString(R.string.confidence_level, confidenceSeekBar?.progress)

            confidenceSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var lastUpdateTime: Long = 0

                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, from: Boolean) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime > 300) { // debounce with 300ms interval
                        lastUpdateTime = currentTime

                        // set cnf threshold only if it changes
                        val progressPercentage = progress / 100f
                        if (progressPercentage != detectionHandler.confidenceThreshold) {
                            detectionHandler.confidenceThreshold = progressPercentage
                            sliderValueText?.text = getString(R.string.confidence_level, progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { detectionHandler.setup() }
            })

            closeButton?.setOnClickListener {
                cameraHandler.startCamera(currentCameraMode)
                liveDetectionHandler.clear()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun setCaptureButtonEnabled(enabled: Boolean) {
        viewBinding.captureBtn.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHandler.stopCamera()
        detectionHandler.onDestroy()
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).toTypedArray()
    }
}