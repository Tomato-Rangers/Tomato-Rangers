package com.tomatorangers.tomaito

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomatorangers.tomaito.camerax.components.CameraControls
import com.tomatorangers.tomaito.camerax.CameraHandler
import com.tomatorangers.tomaito.camerax.components.CameraPreview
import com.tomatorangers.tomaito.permission.PermissionGate
import com.tomatorangers.tomaito.ui.theme.TomAitoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomAitoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionGate {
                        CameraScreen(innerPadding)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraScreen(
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraHandler = remember {
        CameraHandler(
            context = context,
            lifecycleOwner = lifecycleOwner
        )
    }

    CameraPreview(
        modifier = Modifier
            .fillMaxSize(),
        cameraHandler,
    )

    CameraControls(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        cameraHandler = cameraHandler,
        onCaptureClick = {
            cameraHandler.takePhoto(
                onPhotoSaved = { uri ->
                    Log.d(
                        "Camera",
                        "Saved: $uri"
                    )
                },
                onError = { exception ->
                    Log.d(
                        "Camera",
                        "Capture failed",
                        exception
                    )
                }
            )
        },
        onGalleryClick = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                type = "image/*"
            }

            context.startActivity(intent)
        },
        onSettingsClick = {},
    )
}

@Preview
@Composable
fun CameraControlsPreview() {
    CameraControls(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        cameraHandler = CameraHandler(
            context = LocalContext.current,
            lifecycleOwner = LocalLifecycleOwner.current,
        ),
        onCaptureClick = {},
        onGalleryClick = {},
        onSettingsClick = {},
    )
}
