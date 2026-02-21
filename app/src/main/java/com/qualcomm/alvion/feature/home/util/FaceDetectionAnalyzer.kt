
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

    private var forwardYawBaseline: Float? = null
    private var calibrationForwardBaseline: Float? = null

    // Distraction state
    private var distractionStartTime: Long? = null
    private var distractionStartThreshold = 35f
    private val DISTRACTION_TRIGGER_MS = 5000L // 5.0 seconds for distraction
    private val DISTRACTION_HYSTERESIS_DEG = 8f

    private var eyeScoreEma: Float? = null
    private val emaAlpha = 0.45f // Faster response

    // Drowsiness state machine
    private var eyeClosedStartTime: Long? = null
    private var hasFiredDrowsyForThisClosure = false
    private val DROWSY_TRIGGER_MS = 1800L // Faster alerting (1.8s)

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

    // Step 7: Robust Bucket Mapping (Stores means RELATIVE to forwardYawBaseline)
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
        var eyeMean: Float = 0f,
        var eyeM2: Float = 0f,
        var yawSum: Float = 0f,
        var eyeSampleCount: Int = 0,
    ) {
        fun add(
            eyeScore: Float?,
            yaw: Float,
        ) {
            count++
            yawSum += yaw

            if (eyeScore != null) {
                eyeSampleCount++
                val delta = eyeScore - eyeMean
                eyeMean += delta / eyeSampleCount
                val delta2 = eyeScore - eyeMean
                eyeM2 += eyeScore * delta2
            }
        }

        fun eyeStdDev(): Float {
            if (eyeSampleCount < 2) return 0f
            return sqrt(eyeM2 / (eyeSampleCount - 1))
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

        forwardYawBaseline = null
        calibrationForwardBaseline = null
        eyeScoreEma = null
        eyeClosedStartTime = null
        hasFiredDrowsyForThisClosure = false
        distractionStartTime = null
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

        // Establish final forward baseline from forward calibration stats
        forwardYawBaseline = forwardStats.yawMean()

        val k = 2f
        var maxMirrorYawAbs = 0f
        for ((bucketName, stats) in openEyeStatsByBucket) {
            if (stats.count < max(10, calibrationFramesNeededPerBucket / 3)) continue

            // Store Relative Yaw Mean
            val relativeMean = stats.yawMean() - forwardYawBaseline!!
            bucketYawMeans[bucketName] = relativeMean

            if (bucketName == "left" || bucketName == "right") {
                maxMirrorYawAbs = max(maxMirrorYawAbs, abs(relativeMean))
            }

            // Use eye stats if available, otherwise fallback to reasonable default
            if (stats.eyeSampleCount > 5) {
                val mean = stats.eyeMean
                val std = stats.eyeStdDev()
                val raw = mean - (k * std)
                closedThresholdByBucket[bucketName] = raw.coerceIn(0.15f, 0.75f)
            } else {
                closedThresholdByBucket[bucketName] = 0.40f
            }
        }

        // Personalize distraction threshold: mirrors + 5 degree margin for more sensitivity
        distractionStartThreshold = if (maxMirrorYawAbs > 0) maxMirrorYawAbs + 5f else 35f

        return closedThresholdByBucket.isNotEmpty()
    }

    fun resetCalibration() {
        forwardYawBaseline = null
        calibrationForwardBaseline = null
        closedThresholdByBucket.clear()
        bucketYawMeans.clear()
        monitoringEnabled = false
        eyeClosedStartTime = null
        distractionStartTime = null
        hasFiredDrowsyForThisClosure = false
        lastDrowsyCallbackTime = 0L
        lastDistractionCallbackTime = 0L
        lastValidMetricsTime = 0L
    }

    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
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

                val rotY = primaryFace.headEulerAngleY
                val baselineForDelta = forwardYawBaseline ?: calibrationForwardBaseline
                val yawDelta = if (baselineForDelta != null) rotY - baselineForDelta!! else 0f
                val yawAbs = abs(yawDelta)

                // Validation
                val faceBox = primaryFace.boundingBox
                val faceWidthFrac = faceBox.width().toFloat() / max(1, lastImageWidth)
                val faceHeightFrac = faceBox.height().toFloat() / max(1, lastImageHeight)

                val roll = abs(primaryFace.headEulerAngleZ)
                val pitch = abs(primaryFace.headEulerAngleX)

                // High tolerance for yaw during calibration to support off-center mounts and track distractions at extreme angles
                if (faceWidthFrac < minFaceWidthFraction || faceHeightFrac < minFaceHeightFraction ||
                    roll > 45f || pitch > 30f || yawAbs > 80f
                ) {
                    handleInvalidFrame(now)
                    return@addOnSuccessListener
                }

                lastValidMetricsTime = now

                // Calculate Eye Score
                val leftProb = primaryFace.leftEyeOpenProbability
                val rightProb = primaryFace.rightEyeOpenProbability

                if (leftProb != null && rightProb != null) {
                    val penalty = (if (roll > 30f) 0.15f else 0f) + (if (pitch > 20f) 0.10f else 0f) + (if (yawAbs > 25f) 0.10f else 0f)
                    val eyeScore = min((leftProb - penalty).coerceIn(0f, 1f), (rightProb - penalty).coerceIn(0f, 1f))
                    eyeScoreEma = if (eyeScoreEma == null) eyeScore else (1f - emaAlpha) * eyeScoreEma!! + emaAlpha * eyeScore
                }

                // Calibration recording with corrected mirrored signs
                if (isCalibrating) {
                    calibrationTargetBucket?.let { target ->
                        if (target == "forward") {
                            calibrationForwardBaseline = openEyeStatsByBucket["forward"]?.yawMean() ?: rotY
                        }

                        val isPoseCorrectForBucket =
                            when (target) {
                                "forward" -> true
                                // MIRRORED: Turning physical LEFT results in POSITIVE yaw in front cam
                                "left" -> yawDelta > 6f
                                // MIRRORED: Turning physical RIGHT results in NEGATIVE yaw in front cam
                                "right" -> yawDelta < -6f
                                else -> false
                            }

                        if (isPoseCorrectForBucket) {
                            openEyeStatsByBucket[target]?.add(eyeScoreEma, rotY)
                        }
                    }
                }

                if (monitoringEnabled) {
                    // Distraction detection
                    if (yawAbs > distractionStartThreshold) {
                        if (distractionStartTime == null) distractionStartTime = now
                        val distractionDuration = now - distractionStartTime!!
                        if (distractionDuration >= DISTRACTION_TRIGGER_MS) {
                            if (now - lastDistractionCallbackTime >= callbackCooldownMs) {
                                lastDistractionCallbackTime = now
                                mainHandler.post { onDistracted() }
                            }
                        }
                    } else if (yawAbs < (distractionStartThreshold - DISTRACTION_HYSTERESIS_DEG)) {
                        distractionStartTime = null
                    }

                    // Drowsiness
                    if (eyeScoreEma != null) {
                        val currentBucket = bucketYawMeans.minByOrNull { abs(it.value - yawDelta) }?.key ?: "forward"
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
                    } else {
                        eyeClosedStartTime = null
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
            distractionStartTime = null
            hasFiredDrowsyForThisClosure = false
        }
    }
}
