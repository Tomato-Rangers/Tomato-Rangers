package com.tomatorangers.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

class DetectionHandler(
    private val context: Context,
    private val modelPath: String,
    private val tomatoModelPath: String,
    private val orangeModelPath: String,
    private val labelPath: String,
    private val ripenessLabelPath: String,
    private val listener: DetectorListener,
    private val boundingBoxOverlay: BoundingBoxOverlay
) : Detector.DetectorListener {
    private lateinit var tomatoDetector : Detector
    private lateinit var orangeDetector: Detector
    private lateinit var fruitTypeDetector: Detector

    var bitmap: Bitmap? = null
    var isLiveDetection: Boolean = false
    private var fruitType: FruitType = FruitType.UNKNOWN
    private var vitality: Vitality = Vitality.UNDETECTED
    private var cnfArray: FloatArray = floatArrayOf(0.0f, 0.0f)
    private var aveCnf: Float = 0.0f
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

    init {
        setup()
    }

    fun setup() {
        tomatoDetector = Detector(context, tomatoModelPath, ripenessLabelPath, this)
        tomatoDetector.setup()
        orangeDetector = Detector(context, orangeModelPath, ripenessLabelPath, this)
        orangeDetector.setup()
        fruitTypeDetector = Detector(context, modelPath, labelPath, this)
        fruitTypeDetector.setup()
    }

    fun detect(bitmap: Bitmap) {
        fruitTypeDetector.detect(bitmap)

        this.bitmap = bitmap
    }

    private fun detectFruitTypeFromBoundingBox(box: BoundingBox): FruitType {
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

    /*
        LIVE DETECTION LOGIC WENT HERE
        IDK WHY BUT ITS WORKING SO...
        KEEP IT.
        MIGHT REFACTOR IT AFTER THE DEADLINE
     */
    override fun onEmptyDetect() {
        if (isLiveDetection) {
            Log.d("LiveDetection", context.getString(R.string.no_objects_detected))

            if (fruitType == FruitType.UNKNOWN) {
                boundingBoxOverlay.setBoundingBoxes(emptyList())
                listener.onEmptyDetect()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        if (isLiveDetection) {

            // determine first which fruit
            if (fruitType == FruitType.UNKNOWN) {
                if (boundingBoxes.isNotEmpty()) {
                    Log.d("LiveDetection", "${boundingBoxes.size} Object Detected")

                    for (box in boundingBoxes) {
                        fruitType = detectFruitTypeFromBoundingBox(box)
                        Log.d("LiveDetection", "Detected fruit: $fruitType")

                        tempBox = box
                        cnfArray[0] = box.cnf

                        when (fruitType) {
                            FruitType.TOMATO -> {
                                bitmap?.let { tomatoDetector.detect(it) }
                            }
                            FruitType.ORANGE -> {
                                bitmap?.let { orangeDetector.detect(it) }
                            }
                            else -> {
                                listener.onEmptyDetect()
                                continue
                            }
                        }
                        box.vit = vitality
                        Log.d("LiveDetection", "$fruitType Vitality: ${box.vit}")

                        // set cnf to the average cnf of both detection
                        if (box.vit != Vitality.UNKNOWN) {
                            box.cnf = aveCnf
                        }

                        vitality = Vitality.UNDETECTED // reset
                    }


                    boundingBoxOverlay.setBoundingBoxes(boundingBoxes)

                    // reset
                    fruitType = FruitType.UNKNOWN
                    vitality = Vitality.UNDETECTED
                } else {
                    Log.d("LiveDetection", "Bounding boxes are empty")
                    listener.onEmptyDetect()
                }
            }

            // evaluate fruit vitality
            else if (vitality == Vitality.UNDETECTED) {
                Log.d("LiveDetection", "Evaluating Vitality")

                if (boundingBoxes.isNotEmpty()) {
                    for (box in boundingBoxes) {
                        // evaluate box coords similarities
                        val iou = if (fruitType == FruitType.TOMATO) {
                            tomatoDetector.calculateIoU(box, tempBox)
                        } else {
                            orangeDetector.calculateIoU(box, tempBox)
                        }

                        if (iou > 0.6) {
                            Log.d("LiveDetection", "Similar box found for vitality checking")
                            vitality = detectRipeness(box)
                            cnfArray[1] = box.cnf
                            aveCnf = (cnfArray[0] + cnfArray[1]) / 2
                            break
                        }
                        Log.d("LiveDetection", "Box does not match")
                    }
                } else {
                    Log.d("LiveDetection", "Vitality detectors: No object detected")
                    vitality = Vitality.UNKNOWN
                }
            }
        }
    }

    fun saveModifiedImage(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "modified_image_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, context.getString(R.string.folderPath))
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