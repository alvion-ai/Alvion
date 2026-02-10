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
import kotlin.math.max

@Composable
fun GraphicOverlay(
    faces: List<Face>,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas

        val scale = max(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f

        for (face in faces) {
            val bounds = face.boundingBox
            val rectWidth = bounds.width() * scale
            val rectHeight = bounds.height() * scale
            val top = bounds.top * scale + offsetY
            val left = bounds.left * scale + offsetX

            val mirroredLeft =
                if (isFrontCamera) {
                    size.width - (bounds.right * scale + offsetX)
                } else {
                    left
                }

            drawRect(
                color = Color.Red,
                topLeft = Offset(mirroredLeft, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 2f),
            )
        }
    }
}
