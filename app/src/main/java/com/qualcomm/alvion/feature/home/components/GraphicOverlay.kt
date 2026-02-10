package com.qualcomm.alvion.feature.home.components

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

data class FaceOverlayState(
    val rects: List<Rect> = emptyList(),
    val imageWidth: Int = 0, // upright image width
    val imageHeight: Int = 0, // upright image height
    val isFrontCamera: Boolean = true,
)

@Composable
fun GraphicOverlay(state: FaceOverlayState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (state.imageWidth <= 0 || state.imageHeight <= 0) return@Canvas

        val viewW = size.width
        val viewH = size.height

        // Matches PreviewView.ScaleType.FILL_CENTER (center-crop)
        val scale =
            max(
                viewW / state.imageWidth.toFloat(),
                viewH / state.imageHeight.toFloat(),
            )

        val scaledW = state.imageWidth * scale
        val scaledH = state.imageHeight * scale

        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f

        state.rects.forEach { r ->
            val left = r.left * scale + dx
            val top = r.top * scale + dy
            val right = r.right * scale + dx
            val bottom = r.bottom * scale + dy

            val (finalLeft, finalRight) =
                if (state.isFrontCamera) {
                    (viewW - right) to (viewW - left) // mirror for front camera
                } else {
                    left to right
                }

            drawRect(
                color = Color.Red,
                topLeft = Offset(finalLeft, top),
                size = Size(finalRight - finalLeft, bottom - top),
                style = Stroke(width = 2f),
            )
        }
    }
}
