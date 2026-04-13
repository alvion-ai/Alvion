package com.qualcomm.alvion.feature.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.google.mlkit.vision.face.Face
import com.qualcomm.alvion.feature.home.util.FaceDiagnosticInfo
import kotlin.math.max

@Composable
fun GraphicOverlay(
    faces: List<Face>,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean,
    diagnosticInfo: FaceDiagnosticInfo? = null,
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

            val boxColor = if (diagnosticInfo?.isEyeOccluded == true) Color.Yellow else Color.Green

            drawRect(
                color = boxColor,
                topLeft = Offset(mirroredLeft, top),
                size = Size(rectWidth, rectHeight),
                style = Stroke(width = 2f),
            )

            // Draw diagnostic info text
            diagnosticInfo?.let { info ->
                val paint =
                    android.graphics.Paint().apply {
                        color = (if (info.isEyeOccluded) Color.Yellow else Color.Green).toArgb()
                        textSize = 32f
                        isAntiAlias = true
                        setShadowLayer(2f, 1f, 1f, Color.Black.toArgb())
                    }

                val textX = mirroredLeft
                var textY = top - 10f

                val lines = mutableListOf<String>()

                if (info.isEyeOccluded) {
                    lines.add("!!! EYES OCCLUDED !!!")
                }

                lines.addAll(
                    listOf(
                        "Eye L: %.2f".format(info.leftEye),
                        "Eye R: %.2f".format(info.rightEye),
                        "EMA: %.2f".format(info.eyeEma),
                        "Yaw: %.1f".format(info.yaw),
                        "Pitch: %.1f".format(info.pitch),
                        "Thresh: %.2f".format(info.threshold),
                    ),
                )

                // Draw from bottom to top to stay above the box
                lines.reversed().forEach { line ->
                    // Make occlusion warning red
                    if (line.contains("OCCLUDED")) {
                        paint.color = Color.Red.toArgb()
                        paint.isFakeBoldText = true
                    } else {
                        paint.color = (if (info.isEyeOccluded) Color.Yellow else Color.Green).toArgb()
                        paint.isFakeBoldText = false
                    }

                    drawContext.canvas.nativeCanvas.drawText(line, textX, textY, paint)
                    textY -= 40f
                }
            }
        }
    }
}
