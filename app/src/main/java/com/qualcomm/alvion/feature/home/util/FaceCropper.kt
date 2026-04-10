package com.qualcomm.alvion.feature.home.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face

/**
 * Utility to extract and process face regions from camera frames.
 * Converts YUV frames to RGB bitmaps for ML Kit and custom TFLite models.
 */
object FaceCropper {
    private const val TAG = "FaceCropper"
    
    /**
     * Crop face region from camera frame as RGB Bitmap.
     * 
     * @param imageProxy Camera frame
     * @param face ML Kit detected face
     * @return Cropped RGB bitmap, or null if extraction fails
     */
    @ExperimentalGetImage
    fun cropFaceFromFrame(
        imageProxy: ImageProxy,
        face: Face
    ): Bitmap? = cropFaceFromFrame(imageProxy, face.boundingBox)

    /**
     * Crop face region from camera frame as RGB Bitmap.
     *
     * @param imageProxy Camera frame
     * @param boundingBox ML Kit face bounding box
     * @return Cropped RGB bitmap, or null if extraction fails
     */
    @ExperimentalGetImage
    fun cropFaceFromFrame(
        imageProxy: ImageProxy,
        boundingBox: Rect
    ): Bitmap? {
        return try {
            val mediaImage = imageProxy.image ?: return null
            val imageBoundingBox =
                mapBoundingBoxToImageCoordinates(
                    boundingBox = boundingBox,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    imageWidth = mediaImage.width,
                    imageHeight = mediaImage.height,
                )
            
            // Add padding (10%) for better context
            val padding = 0.1f
            val paddedLeft = (imageBoundingBox.left - imageBoundingBox.width() * padding)
                .toInt().coerceIn(0, mediaImage.width - 1)
            val paddedTop = (imageBoundingBox.top - imageBoundingBox.height() * padding)
                .toInt().coerceIn(0, mediaImage.height - 1)
            val paddedRight = (imageBoundingBox.right + imageBoundingBox.width() * padding)
                .toInt().coerceIn(0, mediaImage.width)
            val paddedBottom = (imageBoundingBox.bottom + imageBoundingBox.height() * padding)
                .toInt().coerceIn(0, mediaImage.height)
            
            val cropWidth = (paddedRight - paddedLeft).coerceAtLeast(1)
            val cropHeight = (paddedBottom - paddedTop).coerceAtLeast(1)
            
            // Convert YUV to RGB
            val rgbBitmap = yuvToRgb(mediaImage, paddedLeft, paddedTop, cropWidth, cropHeight)
            
            Log.d(TAG, "✅ Face cropped: ${cropWidth}x${cropHeight}")
            return rgbBitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cropping face: ${e.message}")
            null
        }
    }

    private fun mapBoundingBoxToImageCoordinates(
        boundingBox: Rect,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int
    ): Rect =
        when (rotationDegrees) {
            90 ->
                Rect(
                    boundingBox.top,
                    imageHeight - boundingBox.right,
                    boundingBox.bottom,
                    imageHeight - boundingBox.left,
                )
            180 ->
                Rect(
                    imageWidth - boundingBox.right,
                    imageHeight - boundingBox.bottom,
                    imageWidth - boundingBox.left,
                    imageHeight - boundingBox.top,
                )
            270 ->
                Rect(
                    imageWidth - boundingBox.bottom,
                    boundingBox.left,
                    imageWidth - boundingBox.top,
                    boundingBox.right,
                )
            else -> Rect(boundingBox)
        }.let { mapped ->
            val left = minOf(mapped.left, mapped.right).coerceIn(0, imageWidth - 1)
            val right = maxOf(mapped.left, mapped.right).coerceIn(1, imageWidth)
            val top = minOf(mapped.top, mapped.bottom).coerceIn(0, imageHeight - 1)
            val bottom = maxOf(mapped.top, mapped.bottom).coerceIn(1, imageHeight)

            Rect(
                left,
                top,
                right.coerceAtLeast(left + 1),
                bottom.coerceAtLeast(top + 1),
            )
        }
    
    /**
     * Convert a YUV_420_888 frame region to RGB Bitmap.
     * Handles row and pixel strides from Android camera planes.
     */
    @androidx.camera.core.ExperimentalGetImage
    private fun yuvToRgb(
        image: Image,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int
    ): Bitmap {
        try {
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            
            val yPixelStride = yPlane.pixelStride
            val yRowStride = yPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val vPixelStride = vPlane.pixelStride
            val vRowStride = vPlane.rowStride
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            // Get the Y data
            val yData = ByteArray(yBuffer.remaining())
            yBuffer.get(yData)
            yBuffer.rewind()
            
            // Get the U and V data
            val uData = ByteArray(uBuffer.remaining())
            uBuffer.get(uData)
            uBuffer.rewind()
            
            val vData = ByteArray(vBuffer.remaining())
            vBuffer.get(vData)
            vBuffer.rewind()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            
            val imageWidth = image.width
            val imageHeight = image.height
            
            var pixelIndex = 0
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val frameY = startY + y
                    val frameX = startX + x
                    
                    // Bounds check
                    if (frameY >= imageHeight || frameX >= imageWidth) {
                        pixels[pixelIndex++] = 0xFF000000.toInt()  // Black pixel for out of bounds
                        continue
                    }
                    
                    // Get Y value
                    val yIndex = frameY * yRowStride + frameX * yPixelStride
                    if (yIndex >= yData.size) {
                        pixels[pixelIndex++] = 0xFF000000.toInt()
                        continue
                    }
                    
                    val yVal = yData[yIndex].toInt() and 0xFF
                    
                    // Get U and V values from the YUV_420_888 chroma planes.
                    val uvFrameX = frameX / 2
                    val uvFrameY = frameY / 2
                    val uIndex = uvFrameY * uRowStride + uvFrameX * uPixelStride
                    val vIndex = uvFrameY * vRowStride + uvFrameX * vPixelStride
                    
                    if (uIndex >= uData.size || vIndex >= vData.size) {
                        pixels[pixelIndex++] = 0xFF000000.toInt()
                        continue
                    }
                    
                    val uVal = (uData[uIndex].toInt() and 0xFF) - 128
                    val vVal = (vData[vIndex].toInt() and 0xFF) - 128
                    
                    // YUV to RGB conversion
                    val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)
                    
                    pixels[pixelIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in YUV conversion: ${e.message}")
            SunglassesDebugHelper.logError("YUV conversion failed", e)
            // Return a black bitmap as fallback
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLACK)
            return bitmap
        }
    }
}

