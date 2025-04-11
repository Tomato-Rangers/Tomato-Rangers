package com.tomatorangers.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.util.concurrent.ExecutorService

class LiveDetectionHandler(
    private val context: Context,
    private val cameraExecutor: ExecutorService,
    private val detector: Detector,
    private val boundingBoxOverlay: BoundingBoxOverlay,
    private val preview: PreviewView,
    private val detectionHandler: DetectionHandler
) : Detector.DetectorListener {
    private var cameraProvider: ProcessCameraProvider? = null
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var cameraControl: CameraControl? = null

    init {
        detector.setup()
    }

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        boundingBoxOverlay.boundingBoxes.clear()
        boundingBoxOverlay.invalidate()
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraControl = null
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = preview.surfaceProvider
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(this.preview.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val bitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

            imageProxy.close()

            detectionHandler.bitmap = bitmap
            detector.detect(bitmap)
        }

        try {
            cameraProvider?.unbindAll()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val camera = cameraProvider?.bindToLifecycle(
                context as AppCompatActivity,
                cameraSelector,
                preview,
                imageAnalyzer)
            cameraControl = camera?.cameraControl
        } catch (exc: Exception) {
            Log.e("LiveDetectionHandler", "Use case binding failed", exc)
        }
    }

    override fun onEmptyDetect() {}

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {}
}