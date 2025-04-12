package com.tomatorangers.tomaito

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tomatorangers.tomaito.databinding.ActivityMainBinding

class DetectionHandler(
    private val activity: MainActivity,
    private val context: Context,
    private val liveDetectionHandler: LiveDetectionHandler,
    private val viewBinding: ActivityMainBinding
) : Detector.DetectorListener {
    private lateinit var imageCapturingHandler: ImageCapturingHandler
    private lateinit var liveDetectorListener: DetectorListener
    private lateinit var imageCaptureListener: DetectorListener

    private lateinit var fruitTypeDetector: Detector
    private lateinit var tomatoDetector: Detector
    private lateinit var orangeDetector: Detector
    var confidenceThreshold: Float = 0.8f

    private var bitmap: Bitmap? = null
    private var fruitType = FruitType.UNKNOWN
    private var vitality = Vitality.UNDETECTED
    private var cnfArray: FloatArray = floatArrayOf(0.0f, 0.0f)
    private var tempBox: BoundingBox = BoundingBox(
        0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0, "", Vitality.UNDETECTED
    )

    enum class FruitType {
        TOMATO,
        ORANGE,
        UNKNOWN
    }

    enum class Vitality {
        DAMAGED,
        RIPE,
        UNRIPE,
        UNKNOWN,
        UNDETECTED
    }

    init { setup() }

    fun setup() {
        Log.d("DetectionHandler", "Setting up DetectionHandler")

        imageCapturingHandler = ImageCapturingHandler(
            context,
            this,
            viewBinding
        )

        imageCaptureListener = imageCapturingHandler
        liveDetectorListener = liveDetectionHandler

        fruitTypeDetector = Detector(
            context,
            Constants.FRUIT_TYPE_MODEL_PATH,
            Constants.FRUIT_TYPE_LABEL_PATH,
            this
        )
        fruitTypeDetector.confidenceThreshold = confidenceThreshold
        fruitTypeDetector.setup()

        tomatoDetector = Detector(
            context,
            Constants.TOMATO_MODEL_PATH,
            Constants.RIPENESS_LABEL_PATH,
            this
        )
        tomatoDetector.confidenceThreshold = confidenceThreshold
        tomatoDetector.setup()

        orangeDetector = Detector(
            context,
            Constants.ORANGE_MODEL_PATH,
            Constants.RIPENESS_LABEL_PATH,
            this
        )
        // set the threshold to +5% more
        orangeDetector.confidenceThreshold = if (confidenceThreshold > 0.94f) {
            0.99f
        } else {
            confidenceThreshold
        }
        orangeDetector.setup()

        Log.d("DetectionHandler", "DetectionHandler DONE")
    }

    fun detect(bitmap: Bitmap) {
        this.bitmap = bitmap
        fruitTypeDetector.detect(bitmap)
    }

    private fun detectFruitType(box: BoundingBox): FruitType {
        return when (box.cls) {
            0 -> FruitType.ORANGE
            1 -> FruitType.TOMATO
            else -> FruitType.UNKNOWN
        }
    }

    private fun detectRipeness(box: BoundingBox): Vitality {
        return when (box.cls) {
            0 -> Vitality.DAMAGED
            1 -> Vitality.RIPE
            else -> Vitality.UNRIPE
        }
    }

    fun onDestroy() {
        fruitTypeDetector.clear()
        tomatoDetector.clear()
        orangeDetector.clear()
    }

    override fun onEmptyDetect() {
        Log.d("Detection", context.getString(R.string.no_objects_detected))

        activity.runOnUiThread {
            if (fruitType == FruitType.UNKNOWN) {
                // double check to avoid the detection buffer
                if (activity.currentCameraMode == CameraHandler.CameraMode.LIVE) {
                    liveDetectorListener.onEmptyDetect()
                }
                else if (activity.currentCameraMode == CameraHandler.CameraMode.IMAGE_CAPTURE) {
                    imageCaptureListener.onEmptyDetect()
                }
            }
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>) {
        activity.runOnUiThread {

            // determine which fruit
            if (fruitType == FruitType.UNKNOWN) {
                Log.d("Detection", context.getString(R.string.object_count, boundingBoxes.size))

                for (box in boundingBoxes) {
                    fruitType = detectFruitType(box)
                    Log.d("Detection", "Detected fruit: $fruitType")

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
                    Log.d("Detection", "$fruitType vitality: $vitality")

                    // set cnf to the average cnf of both detection
                    // otherwise it use its fruit type cnf
                    if (box.vit != Vitality.UNKNOWN) {
                        box.cnf = (cnfArray[0] + cnfArray[1]) / 2
                    }

                    vitality = Vitality.UNDETECTED // reset
                }

                // double check to avoid the detection delay
                if (!activity.isSwitchingMode) {
                    if (activity.currentCameraMode == CameraHandler.CameraMode.IMAGE_CAPTURE) {
                        imageCapturingHandler.bitmap = bitmap
                        imageCaptureListener.onDetect(boundingBoxes, activity.isSwitchingMode)
                    }
                    else if (activity.currentCameraMode == CameraHandler.CameraMode.LIVE) {
                        liveDetectorListener.onDetect(boundingBoxes, activity.isSwitchingMode)
                    }
                }

                // reset
                fruitType = FruitType.UNKNOWN
                vitality = Vitality.UNDETECTED
            }

            // evaluate the vitality of the fruit
            else if (vitality == Vitality.UNDETECTED) {
                Log.d("Detection", "Evaluating Vitality")

                for (box in boundingBoxes) {
                    // evaluate box coordinates similarity
                    val iou = if (fruitType == FruitType.TOMATO) {
                        tomatoDetector.calculateIoU(box, tempBox)
                    } else {
                        orangeDetector.calculateIoU(box, tempBox)
                    }

                    if (iou > 0.6) {
                        Log.d("Detection", "Similar box found for vitality checking")

                        vitality = detectRipeness(box)
                        cnfArray[1] = box.cnf
                    } else {
                        Log.d("Detection", "Box does not match")

                        vitality = Vitality.UNKNOWN
                    }
                    break
                }
            }
        }
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, isSwitchingMode: Boolean)
    }
}