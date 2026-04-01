package com.qualcomm.alvion.feature.home.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TFLite Sunglasses Classifier
 *
 * Binary classifier: sunglasses vs no_sunglasses
 * Input: 128x128 RGB image (normalized 0-1)
 * Output: float (0-1), threshold 0.5
 *
 * Usage:
 * ```kotlin
 * val classifier = SunglassesClassifier(context)
 * val result = classifier.classify(faceBitmap)
 * if (result.hasSunglasses) {
 *     // suppress drowsiness detection
 * }
 * classifier.close()
 * ```
 */
class SunglassesClassifier(context: Context) {
    private val TAG = "SunglassesClassifier"
    
    companion object {
        private const val MODEL_NAME = "sunglasses_model.tflite"
        private const val INPUT_SIZE = 128
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }
    
    private lateinit var interpreter: Interpreter
    private var isInitialized = false
    
    init {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // CPU only - no GPU delegate
            }
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Log.d(TAG, "✅ SunglassesClassifier initialized successfully")
            SunglassesDebugHelper.logClassifierInit()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize SunglassesClassifier: ${e.message}")
            SunglassesDebugHelper.logError("Failed to initialize classifier", e)
            isInitialized = false
        }
    }
    
    /**
     * Classify whether sunglasses are present in the face image.
     *
     * @param faceBitmap Cropped face image (will be resized to 128x128)
     * @return ClassificationResult with confidence and prediction
     */
    fun classify(faceBitmap: Bitmap): ClassificationResult {
        if (!isInitialized) {
            Log.w(TAG, "Classifier not initialized")
            SunglassesDebugHelper.logError("Classifier not initialized")
            return ClassificationResult(
                hasSunglasses = false,
                confidence = 0f,
                error = "Classifier not initialized"
            )
        }
        
        return try {
            SunglassesDebugHelper.logInferenceStart()
            SunglassesDebugHelper.logImageExtraction(true, INPUT_SIZE, INPUT_SIZE)
            
            // Resize to model input size (128x128)
            val resizedBitmap = if (faceBitmap.width != INPUT_SIZE || faceBitmap.height != INPUT_SIZE) {
                Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            } else {
                faceBitmap
            }
            
            SunglassesDebugHelper.logNormalization(true)
            
            // Convert to ByteBuffer and normalize
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)
            
            // Prepare output buffer - model outputs shape [1, 1] (2D)
            val outputBuffer = Array(1) { FloatArray(1) }
            
            // Run inference
            interpreter.run(inputBuffer, outputBuffer)
            
            // Extract confidence from [1, 1] tensor
            val confidence = outputBuffer[0][0]
            val hasSunglasses = confidence > CONFIDENCE_THRESHOLD
            
            SunglassesDebugHelper.logInferenceResult(confidence, hasSunglasses)
            Log.d(TAG, "Classification: confidence=${"%.3f".format(confidence)}, sunglasses=$hasSunglasses")
            
            // Clean up if we resized
            if (resizedBitmap != faceBitmap) {
                resizedBitmap.recycle()
            }
            
            ClassificationResult(
                hasSunglasses = hasSunglasses,
                confidence = confidence,
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification: ${e.message}")
            SunglassesDebugHelper.logError("Inference failed", e)
            ClassificationResult(
                hasSunglasses = false,
                confidence = 0f,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Convert Bitmap to normalized ByteBuffer (0-1 range).
     * Expected input: RGB image, will normalize pixel values.
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixel in pixels) {
            // Extract RGB components
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            // Add to buffer (normalized to 0-1)
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        
        buffer.rewind()
        return buffer
    }
    
    /**
     * Release resources.
     */
    fun close() {
        try {
            if (this::interpreter.isInitialized) {
                interpreter.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter: ${e.message}")
        }
    }
    
    /**
     * Result of sunglasses classification.
     */
    data class ClassificationResult(
        val hasSunglasses: Boolean,      // true if confidence > 0.5
        val confidence: Float,            // Raw model output (0-1)
        val error: String? = null         // Error message if classification failed
    ) {
        fun toLog(): String = when {
            error != null -> "⚠️ Classification error: $error"
            hasSunglasses -> "🕶️ SUNGLASSES DETECTED: confidence=${"%.2f".format(confidence)}"
            else -> "👁️ NO SUNGLASSES: confidence=${"%.2f".format(confidence)}"
        }
    }
}





