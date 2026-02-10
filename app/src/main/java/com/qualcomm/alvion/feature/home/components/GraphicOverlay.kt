package com.qualcomm.alvion.feature.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.Face

@Composable
fun GraphicOverlay(faces: List<Face>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        for (face in faces) {
            val bounds = face.boundingBox
            drawRect(
                color = Color.Red,
                topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
                size = Size(bounds.width().toFloat(), bounds.height().toFloat()),
                style = Stroke(width = 2f),
            )
        }
    }
}
