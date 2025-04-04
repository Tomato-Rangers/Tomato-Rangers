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
import java.util.concurrent.ExecutorService
import androidx.core.graphics.createBitmap

class LiveDetectionHandler (
    private val context: Context,
    private val cameraExecutor: ExecutorService?,
    private val detector: Detector,
    private val viewFinder: PreviewView,
    private val cameraProvider: ProcessCameraProvider
) {
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    init {
        detector.setup()
    }

    fun startLiveDetection() {
        bindCameraUseCases()
    }

    private fun bindCameraUseCases() {
        val rotation = viewFinder.display.rotation

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

        cameraExecutor?.let {
            imageAnalyzer.setAnalyzer(it) { imageProxy ->
                val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
                imageProxy.close()

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                }

                val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
                detector.detect(rotatedBitmap)
            }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as AppCompatActivity,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            preview.surfaceProvider = viewFinder.surfaceProvider
        } catch (exc: Exception) {
            Log.e("LiveDetectionHandler", "Use case binding failed", exc)
        }
    }
}