package com.tomatorangers.tomaito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        onCaptureClick = {},
        onFlipCameraClick = {
            cameraHandler.flipCamera()
        },
        onGalleryClick = {},
        onSettingsClick = {},
        onTorchClick = {
            cameraHandler.toggleTorch()
        },
    )
}

//@Preview
//@Composable
//fun CameraControlsPreview() {
//    CameraControls(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color.Black),
//        cameraHandler = CameraHandler,
//        onCaptureClick = {},
//        onFlipCameraClick = {},
//        onGalleryClick = {},
//        onSettingsClick = {},
//        onTorchClick = {},
//    )
//}
