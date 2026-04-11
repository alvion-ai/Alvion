package com.qualcomm.alvion.feature.home.util

import android.util.Log

/**
 * Debug utilities for testing sunglasses detection pipeline without physical sunglasses.
 *
 * Provides:
 * - Comprehensive logging at each step
 * - Ability to simulate sunglasses detection
 * - Pipeline verification
 */
object SunglassesDebugHelper {
    private const val TAG = "SunglassesDebugHelper"

    var simulateSunglasses = false
    var debugLoggingEnabled = true

    fun logClassifierInit() {
        if (debugLoggingEnabled) {
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "✅ SunglassesClassifier INITIALIZED")
            Log.d(TAG, "════════════════════════════════════════")
            println("[DEBUG] ✅ SunglassesClassifier initialized - Pipeline ready!")
        }
    }

    fun logFrameProcessing(
        frameNumber: Int,
        runInference: Boolean,
    ) {
        if (debugLoggingEnabled && frameNumber % 30 == 0) { // Log every 30 frames (1 second @ 30fps)
            Log.d(TAG, "Frame $frameNumber - Inference: ${if (runInference) "RUN" else "SKIP"}")
        }
    }

    fun logImageExtraction(
        success: Boolean,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (debugLoggingEnabled) {
            if (success) {
                Log.d(TAG, "📷 Face image extracted: ${width}x$height")
                println("[DEBUG] 📷 Face region extracted successfully")
            } else {
                Log.w(TAG, "⚠️ Failed to extract face image")
            }
        }
    }

    fun logNormalization(success: Boolean) {
        if (debugLoggingEnabled) {
            if (success) {
                Log.d(TAG, "🔄 Image normalized (0-1 range)")
            } else {
                Log.w(TAG, "⚠️ Normalization failed")
            }
        }
    }

    fun logInferenceStart() {
        if (debugLoggingEnabled) {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🚀 RUNNING TFLite INFERENCE")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    fun logInferenceResult(
        confidence: Float,
        hasSunglasses: Boolean,
    ) {
        if (debugLoggingEnabled) {
            val message =
                if (hasSunglasses) {
                    "🕶️ SUNGLASSES DETECTED: confidence=${"%.3f".format(confidence)}"
                } else {
                    "👁️ NO SUNGLASSES: confidence=${"%.3f".format(confidence)}"
                }
            Log.d(TAG, message)
            println("[DEBUG] $message")
        }
    }

    fun logStateChange(
        detected: Boolean,
        reason: String,
    ) {
        if (debugLoggingEnabled) {
            if (detected) {
                Log.d(TAG, "✓ STATE CHANGED: Sunglasses detected ($reason)")
                Log.d(TAG, "✓ onEyeOccluded(true) called")
                Log.d(TAG, "✓ Drowsiness detection SUPPRESSED")
                println("[DEBUG] ✓ Sunglasses state: DETECTED - Drowsiness suppressed")
            } else {
                Log.d(TAG, "✗ STATE CHANGED: Sunglasses removed ($reason)")
                Log.d(TAG, "✗ onEyeOccluded(false) called")
                Log.d(TAG, "✗ Drowsiness detection RESUMED")
                println("[DEBUG] ✗ Sunglasses state: NOT DETECTED - Drowsiness monitoring active")
            }
        }
    }

    fun logPipelineStatus(
        modelLoaded: Boolean,
        faceDetected: Boolean,
        inferenceRan: Boolean,
        sunglassesDetected: Boolean,
    ) {
        if (debugLoggingEnabled) {
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "📊 PIPELINE STATUS:")
            Log.d(TAG, "  Model Loaded: ${if (modelLoaded) "✅" else "❌"}")
            Log.d(TAG, "  Face Detected: ${if (faceDetected) "✅" else "❌"}")
            Log.d(TAG, "  Inference Ran: ${if (inferenceRan) "✅" else "❌"}")
            Log.d(TAG, "  Sunglasses Detected: ${if (sunglassesDetected) "🕶️" else "👁️"}")
            Log.d(TAG, "════════════════════════════════════════")

            println("\n[DEBUG-PIPELINE-STATUS]")
            println("  ✓ Model: ${if (modelLoaded) "LOADED" else "ERROR"}")
            println("  ✓ Face: ${if (faceDetected) "DETECTED" else "WAITING"}")
            println("  ✓ Inference: ${if (inferenceRan) "RUNNING" else "IDLE"}")
            println("  ✓ Sunglasses: ${if (sunglassesDetected) "DETECTED 🕶️" else "NOT DETECTED 👁️"}")
            println()
        }
    }

    fun logError(
        error: String,
        throwable: Throwable? = null,
    ) {
        Log.e(TAG, "❌ ERROR: $error", throwable)
        println("[ERROR] ❌ $error")
        throwable?.printStackTrace()
    }
}
