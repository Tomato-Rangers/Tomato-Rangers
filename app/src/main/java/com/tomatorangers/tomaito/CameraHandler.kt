package com.tomatorangers.tomaito

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHandler (
    private val context: Context,
    private val previewView: PreviewView,
    private val detectionHandler: DetectionHandler,
    imageCapturingHandler: ImageCapturingHandler
) {
    private val imageCapture: ImageCapture = ImageCapture.Builder().build()
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var isFlash: Boolean = false

    enum class CameraMode {
        LIVE,
        IMAGE_CAPTURE
    }

    init { imageCapturingHandler.setImageCapture(imageCapture) }

    fun startInitialCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            try {
                cameraProvider?.unbindAll()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val camera = cameraProvider?.bindToLifecycle(
                    context as AppCompatActivity,
                    cameraSelector,
                    preview)

                cameraControl = camera?.cameraControl
            } catch (exc: Exception) {
                Log.e("CameraHandler", "Initial camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(cameraMode: CameraMode) {
        Log.d("CameraHandler", "Connecting camera")

        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            when (cameraMode) {
                CameraMode.LIVE -> bindLive()
                CameraMode.IMAGE_CAPTURE -> bindImageCapture()
            }
        }, ContextCompat.getMainExecutor(context))

        Log.d("CameraHandler", "Camera connected")
    }

    fun stopCamera() {
        Log.d("CameraHandler", "Disconnecting camera")

        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraControl = null
        isFlash = false

        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }

        Log.d("CameraHandler", "Camera disconnected")
    }

    fun switchCamera(currentCameraMode: CameraMode) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        Log.d("CameraHandler", "Switching to $lensFacing")

        stopCamera()
        startInitialCamera()
        startCamera(currentCameraMode)

        Log.d("CameraHandler", "Camera switched")
    }

    fun toggleFlash(): Boolean {
        cameraControl?.enableTorch(!isFlash)
        isFlash = !isFlash
        return isFlash
    }

    private fun bindLive() {
        Log.d("CameraHandler", "Binding Live Detection")

        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(previewView.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer!!.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                }

                val bitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

                detectionHandler.detect(bitmap)
            } catch (e: Exception) {
                Log.e("CameraHandler", "Error processing image", e)
            } finally {
                imageProxy.close()
            }
        }

        try {
            cameraProvider?.unbind(imageCapture)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val camera = cameraProvider?.bindToLifecycle(
                context as AppCompatActivity,
                cameraSelector,
                imageAnalyzer
            )

            cameraControl = camera?.cameraControl
        } catch (exc: Exception) {
            Log.e("CameraHandler", "Use case of Live Detection binding failed", exc)
        }

        Log.d("CameraHandler", "Binding Live Detection DONE")
    }

    private fun bindImageCapture() {
        Log.d("CameraHandler", "Binding Image Capture")

        try {
            imageAnalyzer?.let { cameraProvider?.unbind(it) }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val camera = cameraProvider?.bindToLifecycle(
                context as AppCompatActivity,
                cameraSelector,
                imageCapture
            )

            cameraControl = camera?.cameraControl
        } catch (exc: Exception) {
            Log.e("CameraHandler", "Use case binding failed", exc)
        }

        Log.d("CameraHandler", "Binding Image Capture DONE")
    }
}