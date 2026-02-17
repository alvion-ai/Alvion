
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
    private val onImageDimensions: (width: Int, height: Int) -> Unit, // callback to update Compose state with image dimensions for proper coordinate mapping
) : ImageAnalysis.Analyzer { // interface for CameraX to analyze each frame

    // --- Frame-based stability counters (reduce flicker / false positives)
    private var drowsinessCounter = 0
    private var distractionCounter = 0

    // --- UI thread handler (Compose state updates must occur on main thread)
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Distraction baseline (yaw / looking left-right)
    // baselineHeadAngle is the "normal forward" yaw angle we calibrate against.
    private var baselineHeadAngle: Float? = null
    private val distractionThreshold = 35f // degrees away from baseline before counting as distracted

    // --- Eye detection smoothing (EMA) to reduce per-frame noise
    private var eyeScoreEma: Float? = null
    private val emaAlpha = 0.2f // 0.0 = never updates, 1.0 = no smoothing

    // --- Quality gates (skip drowsiness checks when detection is unreliable)
    private val minFaceWidthFraction = 0.15f  // face must be at least 15% of frame width
    private val minFaceHeightFraction = 0.15f // face must be at least 15% of frame height

    // --- Multi-angle calibration support (Phase 1)
    // During calibration, user keeps eyes OPEN while turning head through poses.
    // We learn what "open eyes" look like per pose bucket, then derive thresholds.
    private var isCalibrating = false
    private var calibrationFramesNeededPerBucket = 0
    private var lastImageWidth = 0
    private var lastImageHeight = 0

    @Volatile
    private var monitoringEnabled: Boolean = true

    fun setMonitoringEnabled(enabled: Boolean) {
        monitoringEnabled = enabled
    }

    private data class PoseBucket(
        val name: String,
        val yawMin: Float, // inclusive
        val yawMax: Float, // exclusive
    ) {
        fun containsYaw(deltaYaw: Float): Boolean = deltaYaw >= yawMin && deltaYaw < yawMax
    }

    // Buckets are based on yaw delta from baseline (in degrees).
    // These are intentionally modest so "forward" stays tight.
    private val poseBuckets = listOf(
        PoseBucket(name = "forward", yawMin = -15f, yawMax = 15f),
        PoseBucket(name = "left", yawMin = -35f, yawMax = -15f),
        PoseBucket(name = "right", yawMin = 15f, yawMax = 35f),
    )

    private data class RunningStats(
        var count: Int = 0,
        var mean: Float = 0f,
        var m2: Float = 0f, // for variance
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

    // Collected OPEN-eye scores per pose bucket
    private val openEyeStatsByBucket: MutableMap<String, RunningStats> = mutableMapOf(
        "forward" to RunningStats(),
        "left" to RunningStats(),
        "right" to RunningStats(),
    )

    // Derived "closed" thresholds per bucket, computed when calibration ends
    // If calibration isn't done, we fall back to a conservative default.
    private val closedThresholdByBucket: MutableMap<String, Float> = mutableMapOf()

    //Display progress in UI when calibration is active
    fun isCalibrationActive(): Boolean = isCalibrating

    /**
     * Starts multi-angle calibration.
     * @param framesPerBucket how many valid frames to collect per pose bucket (e.g., 45 ~ 1.5s at 30fps)
     */
    fun startCalibration(framesPerBucket: Int = 45) {
        isCalibrating = true
        calibrationFramesNeededPerBucket = max(1, framesPerBucket)

        // Reset baseline and stats
        baselineHeadAngle = null
        eyeScoreEma = null
        drowsinessCounter = 0
        distractionCounter = 0

        openEyeStatsByBucket.keys.forEach { key ->
            openEyeStatsByBucket[key] = RunningStats()
        }
        closedThresholdByBucket.clear()
    }

    /**
     * Ends calibration and computes per-pose thresholds.
     * Returns true if enough data was collected to compute reasonable thresholds.
     */
    fun finishCalibration(): Boolean {
        isCalibrating = false

        // Require at least some data in forward bucket to be usable
        val forwardStats = openEyeStatsByBucket["forward"]
        if (forwardStats == null || forwardStats.count < max(10, calibrationFramesNeededPerBucket / 3)) {
            // Not enough data; leave thresholds empty so runtime uses fallback
            return false
        }

        // Compute baseline yaw as whatever it already became during runtime, otherwise keep null.
        // (We set baseline in analyze() when we first see a face.)

        // Derive closed thresholds per bucket from observed OPEN-eye distribution:
        // threshold = mean - k*std (clamped). Using k=2 is a good starting point.
        val k = 2f
        for ((bucketName, stats) in openEyeStatsByBucket) {
            if (stats.count < max(10, calibrationFramesNeededPerBucket / 3)) continue

            val mean = stats.mean
            val std = stats.stdDev()
            val raw = mean - (k * std)

            // Clamp so we don't set impossible thresholds.
            //  - Too low => never triggers
            //  - Too high => constant false positives
            val clamped = raw.coerceIn(0.15f, 0.75f)
            closedThresholdByBucket[bucketName] = clamped
        }

        return closedThresholdByBucket.isNotEmpty()
    }

    /** Reset only the distraction baseline (yaw). */
    fun resetCalibration() {
        baselineHeadAngle = null
    }

    private val highAccuracyOpts =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

    private val detector = FaceDetection.getClient(highAccuracyOpts)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) { // wrapper around ML Kit face detection to handle image format and threading
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

        // Dispatch to main thread to safely update Compose state
        mainHandler.post {
            onImageDimensions(imageWidth, imageHeight)
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation) // convert CameraX frame to ML Kit input format

        detector.process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)

                if (faces.isEmpty()) {
                    drowsinessCounter = 0
                    distractionCounter = 0
                    eyeScoreEma = null
                    return@addOnSuccessListener
                }

                // NOTE: If multiple faces are present, current logic iterates all.
                // In driving scenarios should typically select the largest face / closest face.
                for (face in faces) {

                    // --- Distraction baseline calibration (yaw)
                    if (baselineHeadAngle == null) {
                        baselineHeadAngle = face.headEulerAngleY
                    }

                    val rotY = face.headEulerAngleY
                    val baseY = baselineHeadAngle ?: rotY
                    val yawDeltaFromBaseline = rotY - baseY

                    // --- Distraction detection (relative to baseline)
                    val angleDifference = abs(yawDeltaFromBaseline)
                    if (monitoringEnabled) {
                        if (angleDifference > distractionThreshold) {
                            distractionCounter++
                            if (distractionCounter > 5) {
                                onDistracted()
                            }
                        } else {
                            distractionCounter = 0
                        }
                    }

                    // --- Drowsiness detection
                    // 1) Basic availability check
                    val leftProb = face.leftEyeOpenProbability
                    val rightProb = face.rightEyeOpenProbability
                    if (leftProb == null || rightProb == null) {
                        // If ML Kit cannot classify eyes, do not carry over old EMA
                        eyeScoreEma = null
                        drowsinessCounter = 0
                        continue
                    }

                    // 2) Quality gate: face must be large enough in frame (avoid far-away / blurry)
                    val faceBox = face.boundingBox
                    val faceWidthFrac = faceBox.width().toFloat() / max(1, lastImageWidth).toFloat()
                    val faceHeightFrac = faceBox.height().toFloat() / max(1, lastImageHeight).toFloat()
                    val faceLargeEnough = faceWidthFrac >= minFaceWidthFraction && faceHeightFrac >= minFaceHeightFraction

                    // 3) Pose penalties: eye probs degrade with roll/pitch/yaw
                    val roll = abs(face.headEulerAngleZ)
                    val pitch = abs(face.headEulerAngleX)
                    val yawAbsFromBaseline = abs(yawDeltaFromBaseline)

                    val rollPenalty = if (roll > 30f) 0.15f else 0f
                    val pitchPenalty = if (pitch > 20f) 0.10f else 0f
                    val yawPenalty = if (yawAbsFromBaseline > 25f) 0.10f else 0f
                    val totalPenalty = rollPenalty + pitchPenalty + yawPenalty

                    // If pose is extreme or face is too small, skip drowsiness evaluation.
                    // Prevents both false positives and false negatives under low-confidence conditions.
                    val poseNotExtreme = (roll < 45f && pitch < 30f && yawAbsFromBaseline < 45f)
                    if (!faceLargeEnough || !poseNotExtreme) {
                        eyeScoreEma = null
                        drowsinessCounter = 0
                        continue
                    }

                    // 4) Use conservative eye score (min of both eyes), adjusted
                    val leftAdjusted = (leftProb - totalPenalty).coerceIn(0f, 1f)
                    val rightAdjusted = (rightProb - totalPenalty).coerceIn(0f, 1f)
                    val eyeScore = min(leftAdjusted, rightAdjusted)

                    // 5) Smooth with EMA
                    val updatedEma = if (eyeScoreEma == null) {
                        eyeScore
                    } else {
                        (1f - emaAlpha) * eyeScoreEma!! + emaAlpha * eyeScore
                    }
                    eyeScoreEma = updatedEma

                    // 6) Multi-angle calibration collection (OPEN eyes)
                    // Only collect if user is calibrating and baseline exists.
                    if (isCalibrating && baselineHeadAngle != null) {
                        val bucket = poseBuckets.firstOrNull { it.containsYaw(yawDeltaFromBaseline) } ?: poseBuckets[0]
                        openEyeStatsByBucket[bucket.name]?.add(updatedEma)

                        // Early exit condition: once each bucket has enough samples, you can finish.
                        // (UI can call finishCalibration() explicitly; this just allows optional auto-finish later.)
                        // No-op here by default.
                    }

                    // 7) Choose pose-aware threshold (fallback if not calibrated)
                    val currentBucketName = poseBuckets.firstOrNull { it.containsYaw(yawDeltaFromBaseline) }?.name ?: "forward"
                    val threshold = closedThresholdByBucket[currentBucketName]
                        ?: closedThresholdByBucket["forward"]
                        ?: 0.40f // fallback if no calibration data exists

                    // 8) Drowsy decision (EMA vs threshold)
                    if (monitoringEnabled) {
                        if (updatedEma < threshold) {
                            drowsinessCounter++
                            if (drowsinessCounter > 5) {
                                onDrowsy()
                            }
                        } else {
                            drowsinessCounter = 0
                        }
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
}
