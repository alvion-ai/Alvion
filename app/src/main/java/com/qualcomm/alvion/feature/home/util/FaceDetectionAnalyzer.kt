
package com.qualcomm.alvion.feature.home.util

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceDetectionAnalyzer(
    private val onFacesDetected: (List<Face>) -> Unit,
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val onImageDimensions: (width: Int, height: Int) -> Unit,
) : ImageAnalysis.Analyzer {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var distractionCounter = 0
    private var baselineHeadAngle: Float? = null
    private val distractionThreshold = 35f

    private var eyeScoreEma: Float? = null
    private val emaAlpha = 0.2f

    // Drowsiness state machine
    private var eyeClosedStartTime: Long? = null
    private var hasFiredDrowsyForThisClosure = false
    private val DROWSY_TRIGGER_MS = 3000L // 3.0 seconds for robust detection

    // Grace window for brief bad frames
    private var lastValidMetricsTime = 0L
    private val GRACE_MS = 400L // 400ms grace period

    // Callback rate limiting
    private var lastDrowsyCallbackTime = 0L
    private var lastDistractionCallbackTime = 0L
    private val callbackCooldownMs = 3000L  // Min 3 seconds between callbacks

    private val minFaceWidthFraction = 0.15f
    private val minFaceHeightFraction = 0.15f

    private var isCalibrating = false
    private var calibrationTargetBucket: String? = null
    private var calibrationFramesNeededPerBucket = 0
    private var lastImageWidth = 0
    private var lastImageHeight = 0

    @Volatile
    private var monitoringEnabled: Boolean = false // Default to false until calibrated

    fun setMonitoringEnabled(enabled: Boolean) {
        monitoringEnabled = enabled
    }

    fun setCalibrationTarget(bucketName: String?) {
        calibrationTargetBucket = bucketName
    }

    fun getCalibrationCount(bucketName: String): Int {
        return openEyeStatsByBucket[bucketName]?.count ?: 0
    }

    private data class PoseBucket(
        val name: String,
        val yawMin: Float,
        val yawMax: Float,
    ) {
        fun containsYaw(deltaYaw: Float): Boolean = deltaYaw >= yawMin && deltaYaw < yawMax
    }

    private val poseBuckets =
        listOf(
            PoseBucket(name = "forward", yawMin = -15f, yawMax = 15f),
            PoseBucket(name = "left", yawMin = -35f, yawMax = -15f),
            PoseBucket(name = "right", yawMin = 15f, yawMax = 35f),
        )

    private data class RunningStats(
        var count: Int = 0,
        var mean: Float = 0f,
        var m2: Float = 0f,
    ) {
        fun add(x: Float) {
            count++
            val delta = x - mean
            mean += delta / count
            val delta2 = x - mean
            m2 += delta * delta2
        }

        fun stdDev(): Float {
            if (count < 2) return 0f
            return sqrt(m2 / (count - 1))
        }
    }

    private val openEyeStatsByBucket: MutableMap<String, RunningStats> =
        mutableMapOf(
            "forward" to RunningStats(),
            "left" to RunningStats(),
            "right" to RunningStats(),
        )

    private val closedThresholdByBucket: MutableMap<String, Float> = mutableMapOf()

    fun isCalibrationActive(): Boolean = isCalibrating

    fun startCalibration(framesPerBucket: Int = 45) {
        isCalibrating = true
        calibrationFramesNeededPerBucket = max(1, framesPerBucket)
        calibrationTargetBucket = null

        baselineHeadAngle = null
        eyeScoreEma = null
        eyeClosedStartTime = null
        hasFiredDrowsyForThisClosure = false
        distractionCounter = 0
        lastValidMetricsTime = 0L

        openEyeStatsByBucket.keys.forEach { key ->
            openEyeStatsByBucket[key] = RunningStats()
        }
        closedThresholdByBucket.clear()
    }

    fun finishCalibration(): Boolean {
        isCalibrating = false
        calibrationTargetBucket = null

        val forwardStats = openEyeStatsByBucket["forward"]
        if (forwardStats == null || forwardStats.count < max(10, calibrationFramesNeededPerBucket / 3)) {
            return false
        }

        val k = 2f
        for ((bucketName, stats) in openEyeStatsByBucket) {
            if (stats.count < max(10, calibrationFramesNeededPerBucket / 3)) continue

            val mean = stats.mean
            val std = stats.stdDev()
            val raw = mean - (k * std)
            val clamped = raw.coerceIn(0.15f, 0.75f)
            closedThresholdByBucket[bucketName] = clamped
        }

        return closedThresholdByBucket.isNotEmpty()
    }

    fun resetCalibration() {
        baselineHeadAngle = null
        closedThresholdByBucket.clear()
        monitoringEnabled = false
        eyeClosedStartTime = null
        hasFiredDrowsyForThisClosure = false
        lastDrowsyCallbackTime = 0L
        lastDistractionCallbackTime = 0L
        lastValidMetricsTime = 0L
    }

    private val highAccuracyOpts =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

    private val detector = FaceDetection.getClient(highAccuracyOpts)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        lastImageWidth = imageWidth
        lastImageHeight = imageHeight

        mainHandler.post {
            onImageDimensions(imageWidth, imageHeight)
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                val now = System.currentTimeMillis()

                if (faces.isEmpty()) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                // Step 4: Primary face selection
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace == null) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                if (baselineHeadAngle == null) {
                    baselineHeadAngle = primaryFace.headEulerAngleY
                }

                val rotY = primaryFace.headEulerAngleY
                val baseY = baselineHeadAngle ?: rotY
                val yawDeltaFromBaseline = rotY - baseY

                val angleDifference = abs(yawDeltaFromBaseline)
                if (monitoringEnabled) {
                    if (angleDifference > distractionThreshold) {
                        distractionCounter++
                        if (distractionCounter > 5) {
                            if (now - lastDistractionCallbackTime >= callbackCooldownMs) {
                                lastDistractionCallbackTime = now
                                mainHandler.post { onDistracted() }
                            }
                        }
                    } else {
                        distractionCounter = 0
                    }
                }

                val leftProb = primaryFace.leftEyeOpenProbability
                val rightProb = primaryFace.rightEyeOpenProbability
                if (leftProb == null || rightProb == null) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                val faceBox = primaryFace.boundingBox
                val faceWidthFrac = faceBox.width().toFloat() / max(1, lastImageWidth).toFloat()
                val faceHeightFrac = faceBox.height().toFloat() / max(1, lastImageHeight).toFloat()
                val faceLargeEnough = faceWidthFrac >= minFaceWidthFraction && faceHeightFrac >= minFaceHeightFraction

                val roll = abs(primaryFace.headEulerAngleZ)
                val pitch = abs(primaryFace.headEulerAngleX)
                val yawAbsFromBaseline = abs(yawDeltaFromBaseline)

                val rollPenalty = if (roll > 30f) 0.15f else 0f
                val pitchPenalty = if (pitch > 20f) 0.10f else 0f
                val yawPenalty = if (yawAbsFromBaseline > 25f) 0.10f else 0f
                val totalPenalty = rollPenalty + pitchPenalty + yawPenalty

                val poseNotExtreme = (roll < 45f && pitch < 30f && yawAbsFromBaseline < 45f)
                if (!faceLargeEnough || !poseNotExtreme) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                // If we reached here, the frame is valid
                lastValidMetricsTime = now

                val leftAdjusted = (leftProb - totalPenalty).coerceIn(0f, 1f)
                val rightAdjusted = (rightProb - totalPenalty).coerceIn(0f, 1f)
                val eyeScore = min(leftAdjusted, rightAdjusted)

                val updatedEma =
                    if (eyeScoreEma == null) {
                        eyeScore
                    } else {
                        (1f - emaAlpha) * eyeScoreEma!! + emaAlpha * eyeScore
                    }
                eyeScoreEma = updatedEma

                if (isCalibrating && baselineHeadAngle != null) {
                    calibrationTargetBucket?.let { target ->
                        openEyeStatsByBucket[target]?.add(updatedEma)
                    }
                }

                if (monitoringEnabled) {
                    val currentBucketName = poseBuckets.firstOrNull { it.containsYaw(yawDeltaFromBaseline) }?.name ?: "forward"
                    val threshold =
                        closedThresholdByBucket[currentBucketName]
                            ?: closedThresholdByBucket["forward"]
                            ?: 0.40f

                    // Step 5: Time-based state machine
                    val eyesCurrentlyClosed = updatedEma < threshold

                    if (eyesCurrentlyClosed) {
                        if (eyeClosedStartTime == null) {
                            eyeClosedStartTime = now
                        }
                        
                        val closedDuration = now - (eyeClosedStartTime ?: now)
                        if (closedDuration >= DROWSY_TRIGGER_MS && !hasFiredDrowsyForThisClosure) {
                            if (now - lastDrowsyCallbackTime >= callbackCooldownMs) {
                                lastDrowsyCallbackTime = now
                                mainHandler.post { onDrowsy() }
                                hasFiredDrowsyForThisClosure = true
                            }
                        }
                    } else {
                        // Eyes are open
                        eyeClosedStartTime = null
                        hasFiredDrowsyForThisClosure = false
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleInvalidFrame(now: Long) {
        // Step 6: Grace period logic
        // If we haven't seen a valid frame for more than GRACE_MS, reset state
        if (now - lastValidMetricsTime > GRACE_MS) {
            eyeScoreEma = null
            eyeClosedStartTime = null
            hasFiredDrowsyForThisClosure = false
            distractionCounter = 0
        }
    }
}
