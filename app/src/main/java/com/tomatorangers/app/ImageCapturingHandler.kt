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
import com.tomatorangers.app.DetectionHandler.FruitType
import com.tomatorangers.app.DetectionHandler.Vitality
import java.io.File

class ImageCapturingHandler(
    private val context: Context,
    private val preview: PreviewView,
    private val fruitTypeModelPath: String,
    private val tomatoModelPath: String,
    private val orangeModelPath: String,
    private val fruitTypeLabelPath: String,
    private val ripenessLabelPath: String,
    private val activity: MainActivity,
    private val listener: DetectionHandler.DetectorListener,
) : Detector.DetectorListener {
    private lateinit var fruitTypeDetector: Detector
    private lateinit var tomatoDetector: Detector
    private lateinit var orangeDetector: Detector
    private var imageCapture: ImageCapture? = null
    private var savedImageUri: Uri? = null
    private var isCapturing: Boolean = false
    private var cameraProvider: ProcessCameraProvider? = null
    var cameraControl: CameraControl? = null
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private var bitmap: Bitmap? = null
    private var vitality: Vitality = Vitality.UNDETECTED
    private var fruitType: FruitType = FruitType.UNKNOWN
    private var cnfArray: FloatArray = floatArrayOf(0.0f, 0.0f)
    private var aveCnf: Float = 0.00f
    private var tempBox: BoundingBox = BoundingBox(
        0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0, "", Vitality.UNDETECTED
    )

    init { setup() }

    private fun setup() {
        fruitTypeDetector = Detector(context, fruitTypeModelPath, fruitTypeLabelPath, this)
        fruitTypeDetector.setup()
        tomatoDetector = Detector(context, tomatoModelPath, ripenessLabelPath, this)
        tomatoDetector.setup()
        orangeDetector = Detector(context, orangeModelPath, ripenessLabelPath, this)
        orangeDetector.setup()
    }

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
        imageCapture = null
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
                                    detect(bitmap)
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

    private fun detect(bitmap: Bitmap) {
        this.bitmap = bitmap

        fruitTypeDetector.detect(bitmap)
    }

    private fun detectFruitType(box: BoundingBox): FruitType {
        return when (box.cls) {
            0 -> FruitType.ORANGE
            else -> FruitType.TOMATO
        }
    }

    private fun detectRipeness(box: BoundingBox): Vitality {
        return when (box.cls) {
            0 -> Vitality.DAMAGED
            1 -> Vitality.RIPE
            else -> Vitality.UNRIPE
        }
    }

    override fun onEmptyDetect() {
        Log.d("ImageCapturing", context.getString(R.string.no_objects_detected))

        activity.runOnUiThread {

        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        Log.d("ImageCapturing", context.getString(R.string.detection_result, boundingBoxes.size))

        if (imageCapture != null) {

            // determine which fruit
            if (fruitType == FruitType.UNKNOWN) {
                if (boundingBoxes.isNotEmpty()) {
                    Log.d("ImageCapturing", "${boundingBoxes.size} object detected")

                    for (box in boundingBoxes) {
                        fruitType = detectFruitType(box)
                        Log.d("ImageCapturing", "Detected fruit: $fruitType")

                        tempBox = box
                        cnfArray[0] = box.cnf

                        when (fruitType) {
                            FruitType.TOMATO -> {
                                bitmap?.let { tomatoDetector.detect(it) }
                            }
                            else -> {
                                bitmap?.let { orangeDetector.detect(it) }
                            }
                        }

                        box.vit = vitality
                        Log.d("ImageCapturing", "$fruitType vitality: $vitality")

                        // set cnf to the average cnf of both detection
                        if (box.vit != Vitality.UNKNOWN) {
                            box.cnf = aveCnf
                        }

                        vitality = Vitality.UNDETECTED // reset
                    }
                }

                // pass to parent for box drawing
                listener.onDetect(boundingBoxes, inferenceTime)

                // reset
                fruitType = FruitType.UNKNOWN
                vitality = Vitality.UNDETECTED
            }

            // evaluate fruit ripeness
            else if (vitality == Vitality.UNDETECTED) {
                Log.d("ImageCapturing", "Evaluating Vitality")

                if (boundingBoxes.isNotEmpty()) {
                    for (box in boundingBoxes) {
                        // evaluate box coordinates similarity
                        val iou = if (fruitType == FruitType.TOMATO) {
                            tomatoDetector.calculateIoU(box, tempBox)
                        } else {
                            orangeDetector.calculateIoU(box, tempBox)
                        }

                        if (iou > 0.6) {
                            Log.d("ImageCapturing", "Similar box found for vitality checking")
                            vitality = detectRipeness(box)
                            cnfArray[1] = box.cnf
                            aveCnf = (cnfArray[0] + cnfArray[1]) / 2
                            break
                        }
                        Log.d("ImageCapturing", "Box does not match")
                    }
                } else {
                    Log.d("ImageCapturing", "Vitality detectors: No object detected")
                    vitality = Vitality.UNKNOWN
                }
            }
        }
    }
}