
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
    private val callbackCooldownMs = 3000L // Min 3 seconds between callbacks

    private val minFaceWidthFraction = 0.15f
    private val minFaceHeightFraction = 0.15f

    private var isCalibrating = false
    private var calibrationTargetBucket: String? = null
    private var calibrationFramesNeededPerBucket = 0
    private var lastImageWidth = 0
    private var lastImageHeight = 0

    @Volatile
    private var monitoringEnabled: Boolean = false

    // Step 7: Robust Bucket Mapping
    private val bucketYawMeans: MutableMap<String, Float> = mutableMapOf()

    fun setMonitoringEnabled(enabled: Boolean) {
        monitoringEnabled = enabled
    }

    fun setCalibrationTarget(bucketName: String?) {
        calibrationTargetBucket = bucketName
    }

    fun getCalibrationCount(bucketName: String): Int {
        return openEyeStatsByBucket[bucketName]?.count ?: 0
    }

    private data class RunningStats(
        var count: Int = 0,
        var mean: Float = 0f,
        var m2: Float = 0f,
        var yawSum: Float = 0f, // Track yaw for Step 7
    ) {
        fun add(
            eyeScore: Float,
            yaw: Float,
        ) {
            count++
            yawSum += yaw
            val delta = eyeScore - mean
            mean += delta / count
            val delta2 = eyeScore - mean
            m2 += eyeScore * delta2
        }

        fun stdDev(): Float {
            if (count < 2) return 0f
            return sqrt(m2 / (count - 1))
        }

        fun yawMean(): Float = if (count > 0) yawSum / count else 0f
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
        bucketYawMeans.clear()

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

            // Store Yaw Mean for robust classification (Step 7)
            bucketYawMeans[bucketName] = stats.yawMean()

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
        bucketYawMeans.clear()
        monitoringEnabled = false
        eyeClosedStartTime = null
        hasFiredDrowsyForThisClosure = false
        lastDrowsyCallbackTime = 0L
        lastDistractionCallbackTime = 0L
        lastValidMetricsTime = 0L
    }

    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build(),
        )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()

        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        lastImageWidth = imageWidth
        lastImageHeight = imageHeight

        mainHandler.post { onImageDimensions(imageWidth, imageHeight) }

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                val now = System.currentTimeMillis()

                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace == null) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                if (baselineHeadAngle == null) baselineHeadAngle = primaryFace.headEulerAngleY
                val rotY = primaryFace.headEulerAngleY
                val yawDelta = rotY - (baselineHeadAngle ?: rotY)

                // Validation
                val leftProb = primaryFace.leftEyeOpenProbability
                val rightProb = primaryFace.rightEyeOpenProbability
                val faceBox = primaryFace.boundingBox
                val faceWidthFrac = faceBox.width().toFloat() / max(1, lastImageWidth)
                val faceHeightFrac = faceBox.height().toFloat() / max(1, lastImageHeight)

                val roll = abs(primaryFace.headEulerAngleZ)
                val pitch = abs(primaryFace.headEulerAngleX)
                val yawAbs = abs(yawDelta)

                if (leftProb == null || rightProb == null ||
                    faceWidthFrac < minFaceWidthFraction || faceHeightFrac < minFaceHeightFraction ||
                    roll > 45f || pitch > 30f || yawAbs > 50f
                ) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                lastValidMetricsTime = now

                // Calculate Eye Score
                val penalty = (if (roll > 30f) 0.15f else 0f) + (if (pitch > 20f) 0.10f else 0f) + (if (yawAbs > 25f) 0.10f else 0f)
                val eyeScore = min((leftProb - penalty).coerceIn(0f, 1f), (rightProb - penalty).coerceIn(0f, 1f))
                eyeScoreEma = if (eyeScoreEma == null) eyeScore else (1f - emaAlpha) * eyeScoreEma!! + emaAlpha * eyeScore

                // Calibration recording
                if (isCalibrating) {
                    calibrationTargetBucket?.let { openEyeStatsByBucket[it]?.add(eyeScoreEma!!, rotY) }
                }

                if (monitoringEnabled) {
                    // Distraction
                    if (yawAbs > distractionThreshold) {
                        distractionCounter++
                        if (distractionCounter > 5 && now - lastDistractionCallbackTime >= callbackCooldownMs) {
                            lastDistractionCallbackTime = now
                            mainHandler.post { onDistracted() }
                        }
                    } else {
                        distractionCounter = 0
                    }

                    // Drowsiness with Step 7: Closest Mean Bucket selection
                    val currentBucket = bucketYawMeans.minByOrNull { abs(it.value - rotY) }?.key ?: "forward"
                    val threshold = closedThresholdByBucket[currentBucket] ?: 0.40f

                    if (eyeScoreEma!! < threshold) {
                        if (eyeClosedStartTime == null) eyeClosedStartTime = now
                        if (now - eyeClosedStartTime!! >= DROWSY_TRIGGER_MS && !hasFiredDrowsyForThisClosure) {
                            if (now - lastDrowsyCallbackTime >= callbackCooldownMs) {
                                lastDrowsyCallbackTime = now
                                mainHandler.post { onDrowsy() }
                                hasFiredDrowsyForThisClosure = true
                            }
                        }
                    } else {
                        eyeClosedStartTime = null
                        hasFiredDrowsyForThisClosure = false
                    }
                }
            }
            .addOnFailureListener { it.printStackTrace() }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleInvalidFrame(now: Long) {
        if (now - lastValidMetricsTime > GRACE_MS) {
            eyeScoreEma = null
            eyeClosedStartTime = null
            hasFiredDrowsyForThisClosure = false
            distractionCounter = 0
        }
    }
}
