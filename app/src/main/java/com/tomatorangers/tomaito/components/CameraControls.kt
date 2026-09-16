package com.tomatorangers.tomaito.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomatorangers.tomaito.R

@Composable
fun CameraControls(
    modifier: Modifier = Modifier,
    onCaptureClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = modifier
    ) {

        // settings button
        ActionButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 24.dp),
            onClick = onSettingsClick,
            icon = painterResource(R.drawable.settings),
            contentDescription = "Settings"
        )


        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .background(
                    color = Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // gallery button
            ActionButton(
                onClick = onGalleryClick,
                icon = painterResource(R.drawable.gallery),
                contentDescription = "Gallery"
            )

            // capture button
            ActionButton(
                onClick = onCaptureClick,
                icon = painterResource(R.drawable.camera_capture),
                contentDescription = "Take photo"
            )

            // flip camera button
            ActionButton(
                onClick = onFlipCameraClick,
                icon = painterResource(R.drawable.camera_flip),
                contentDescription = "Flip camera"
            )
        }
    }
}
