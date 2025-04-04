package com.tomatorangers.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import androidx.core.graphics.createBitmap

class LiveDetectionHandler(
    private val context: Context,
    private val cameraExecutor: ExecutorService,
    private val detector: Detector,
    private val viewFinder: PreviewView,
    private val boundingBoxOverlay: BoundingBoxOverlay
) : Detector.DetectorListener {
    private var cameraProvider: ProcessCameraProvider? = null

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
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
            detector.detect(rotatedBitmap)
            imageProxy.close()
        }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(context as AppCompatActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
        } catch (exc: Exception) {
            Log.e("LiveDetectionHandler", "Use case binding failed", exc)
        }
    }

    override fun onEmptyDetect() {
        boundingBoxOverlay.setBoundingBoxes(emptyList())
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        boundingBoxOverlay.setBoundingBoxes(boundingBoxes)
    }
}