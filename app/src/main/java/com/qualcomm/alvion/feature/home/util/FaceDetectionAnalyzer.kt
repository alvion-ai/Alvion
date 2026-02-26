package com.qualcomm.alvion.feature.home.util

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Image analysis component that delegates detection logic to [FaceStateEvaluator].
 */
class FaceDetectionAnalyzer(
    private val onFacesDetected: (List<Face>) -> Unit,
    onDrowsy: () -> Unit,
    onDistracted: () -> Unit,
    private val onImageDimensions: (width: Int, height: Int) -> Unit,
    private val detector: FaceProcessor = MlKitFaceProcessor(),
    mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
) : ImageAnalysis.Analyzer {

    val evaluator = FaceStateEvaluator(
        onDrowsy = onDrowsy,
        onDistracted = onDistracted,
        mainThreadPoster = mainThreadPoster
    )

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()

        val rotation = imageProxy.imageInfo.rotationDegrees
        val (width, height) = ImageSizeCalculator.compute(rotation, imageProxy.width, imageProxy.height)
        
        onImageDimensions(width, height)

        val image = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                evaluator.evaluate(faces, width, height)
            }
            .addOnFailureListener { it.printStackTrace() }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/**
 * The core logic for drowsiness and distraction detection.
 */
class FaceStateEvaluator(
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    // --- Constants ---
    private val DROWSY_TRIGGER_MS = 1800L
    private val DISTRACTION_TRIGGER_MS = 5000L
    private val CALLBACK_COOLDOWN_MS = 3000L
    private val GRACE_MS = 400L
    private val EMA_ALPHA = 0.45f
    private val MIN_FACE_SIZE_FRAC = 0.15f
    private val DISTRACTION_HYSTERESIS_DEG = 8f

    // --- State ---
    @Volatile
    var monitoringEnabled = false
    private var isCalibrating = false
    private var calibrationTargetBucket: String? = null
    private var calibrationFramesNeeded = 45

    private var forwardYawBaseline: Float? = null
    private var calibrationForwardBaseline: Float? = null
    private var distractionStartThreshold = 35f
    
    private var eyeScoreEma: Float? = null
    private var eyeClosedStartTime: Long? = null
    private var distractionStartTime: Long? = null
    private var lastValidMetricsTime = 0L
    private var hasFiredDrowsyForThisClosure = false
    
    private var lastDrowsyCallbackTime = 0L
    private var lastDistractionCallbackTime = 0L

    private val bucketYawMeans = mutableMapOf<String, Float>()
    private val closedThresholdByBucket = mutableMapOf<String, Float>()
    private val openEyeStatsByBucket = mutableMapOf(
        "forward" to RunningStats(), "left" to RunningStats(), "right" to RunningStats()
    )

    fun setMonitoringEnabled(enabled: Boolean) { monitoringEnabled = enabled }
    fun setCalibrationTarget(bucket: String?) { calibrationTargetBucket = bucket }
    fun isCalibrationActive() = isCalibrating
    fun getCalibrationCount(bucket: String) = openEyeStatsByBucket[bucket]?.count ?: 0

    fun startCalibration(frames: Int = 45) {
        isCalibrating = true
        calibrationFramesNeeded = max(1, frames)
        forwardYawBaseline = null
        calibrationForwardBaseline = null
        bucketYawMeans.clear()
        closedThresholdByBucket.clear()
        reset()
    }

    /**
     * Finishes calibration and applies MIRROR LOGIC:
     * If only one side mirror is calibrated, the other side is inferred by symmetry.
     */
    fun finishCalibration(): Boolean {
        isCalibrating = false
        val forwardStats = openEyeStatsByBucket["forward"] ?: return false
        if (forwardStats.count < 5) return false

        forwardYawBaseline = forwardStats.yawMean()
        var maxMirrorYawAbs = 0f

        // 1. Process existing buckets
        openEyeStatsByBucket.forEach { (name, stats) ->
            if (stats.count < 5) return@forEach
            val relYaw = stats.yawMean() - forwardYawBaseline!!
            bucketYawMeans[name] = relYaw
            if (name != "forward") maxMirrorYawAbs = max(maxMirrorYawAbs, abs(relYaw))
            
            val threshold = if (stats.eyeSampleCount > 5) {
                (stats.eyeMean - 2f * stats.eyeStdDev()).coerceIn(0.15f, 0.75f)
            } else 0.40f
            closedThresholdByBucket[name] = threshold
        }

        // 2. MIRROR SYMMETRY LOGIC: 
        if (closedThresholdByBucket.containsKey("left") && !closedThresholdByBucket.containsKey("right")) {
            bucketYawMeans["right"] = -bucketYawMeans["left"]!!
            closedThresholdByBucket["right"] = closedThresholdByBucket["left"]!!
        } else if (closedThresholdByBucket.containsKey("right") && !closedThresholdByBucket.containsKey("left")) {
            bucketYawMeans["left"] = -bucketYawMeans["right"]!!
            closedThresholdByBucket["left"] = closedThresholdByBucket["right"]!!
        }

        distractionStartThreshold = if (maxMirrorYawAbs > 0) maxMirrorYawAbs + 5f else 35f
        return closedThresholdByBucket.containsKey("forward")
    }

    fun reset() {
        eyeScoreEma = null
        eyeClosedStartTime = null
        distractionStartTime = null
        lastValidMetricsTime = 0L
        hasFiredDrowsyForThisClosure = false
        lastDrowsyCallbackTime = 0L
        lastDistractionCallbackTime = 0L
        openEyeStatsByBucket.values.forEach { it.reset() }
    }

    fun evaluate(faces: List<Face>, width: Int, height: Int) {
        val now = clock()
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        if (primaryFace == null) {
            handleInvalidFrame(now)
            return
        }

        val rotY = primaryFace.headEulerAngleY
        val rotX = primaryFace.headEulerAngleX
        val rotZ = primaryFace.headEulerAngleZ
        val baseline = forwardYawBaseline ?: calibrationForwardBaseline
        val yawDelta = if (baseline != null) rotY - baseline else 0f
        val yawAbs = abs(yawDelta)

        val faceBox = primaryFace.boundingBox
        val faceWFrac = faceBox.width().toFloat() / max(1, width)
        val faceHFrac = faceBox.height().toFloat() / max(1, height)

        if (faceWFrac < MIN_FACE_SIZE_FRAC || faceHFrac < MIN_FACE_SIZE_FRAC ||
            abs(rotZ) > 45f || abs(rotX) > 30f || yawAbs > 80f
        ) {
            handleInvalidFrame(now)
            return
        }

        lastValidMetricsTime = now
        val left = primaryFace.leftEyeOpenProbability
        val right = primaryFace.rightEyeOpenProbability

        if (left != null && right != null) {
            val penalty = (if (abs(rotZ) > 30f) 0.15f else 0f) + 
                          (if (abs(rotX) > 20f) 0.10f else 0f) + 
                          (if (yawAbs > 25f) 0.10f else 0f)
            val currentScore = min(left - penalty, right - penalty).coerceIn(0f, 1f)
            eyeScoreEma = if (eyeScoreEma == null) currentScore else (1f - EMA_ALPHA) * eyeScoreEma!! + EMA_ALPHA * currentScore
        }

        if (isCalibrating && calibrationTargetBucket != null) {
            val target = calibrationTargetBucket!!
            if (target == "forward") {
                calibrationForwardBaseline = openEyeStatsByBucket["forward"]?.yawMean() ?: rotY
            }
            
            val isPoseCorrect = when(target) {
                "forward" -> abs(yawDelta) < 5f
                "left" -> yawDelta > 6f    // User Left = Camera Right
                "right" -> yawDelta < -6f  // User Right = Camera Left
                else -> false
            }
            if (isPoseCorrect) openEyeStatsByBucket[target]?.add(eyeScoreEma, rotY)
        }

        if (monitoringEnabled) {
            // Distraction check
            if (yawAbs > distractionStartThreshold) {
                if (distractionStartTime == null) distractionStartTime = now
                if (now - distractionStartTime!! >= DISTRACTION_TRIGGER_MS) {
                    if (now - lastDistractionCallbackTime >= CALLBACK_COOLDOWN_MS) {
                        lastDistractionCallbackTime = now
                        mainThreadPoster.post(onDistracted)
                    }
                }
            } else if (yawAbs < (distractionStartThreshold - DISTRACTION_HYSTERESIS_DEG)) {
                distractionStartTime = null
            }

            // Drowsiness check
            eyeScoreEma?.let { score ->
                val bucket = bucketYawMeans.minByOrNull { abs(it.value - yawDelta) }?.key ?: "forward"
                val threshold = closedThresholdByBucket[bucket] ?: 0.40f

                if (score < threshold) {
                    if (eyeClosedStartTime == null) eyeClosedStartTime = now
                    if (now - eyeClosedStartTime!! >= DROWSY_TRIGGER_MS && !hasFiredDrowsyForThisClosure) {
                        if (now - lastDrowsyCallbackTime >= CALLBACK_COOLDOWN_MS) {
                            lastDrowsyCallbackTime = now
                            mainThreadPoster.post(onDrowsy)
                            hasFiredDrowsyForThisClosure = true
                        }
                    }
                } else {
                    eyeClosedStartTime = null
                    hasFiredDrowsyForThisClosure = false
                }
            }
        }
    }

    private fun handleInvalidFrame(now: Long) {
        if (now - lastValidMetricsTime > GRACE_MS) {
            eyeScoreEma = null
            eyeClosedStartTime = null
            distractionStartTime = null
            hasFiredDrowsyForThisClosure = false
        }
    }

    private class RunningStats {
        var count = 0
        var eyeMean = 0f
        var eyeM2 = 0f
        var yawSum = 0f
        var eyeSampleCount = 0

        fun add(eye: Float?, yaw: Float) {
            count++
            yawSum += yaw
            if (eye != null) {
                eyeSampleCount++
                val delta = eye - eyeMean
                eyeMean += delta / eyeSampleCount
                eyeM2 += (eye - eyeMean) * delta
            }
        }

        fun yawMean() = if (count > 0) yawSum / count else 0f
        fun eyeStdDev() = if (eyeSampleCount > 1) sqrt(eyeM2 / (eyeSampleCount - 1)) else 0f
        fun reset() { count = 0; eyeMean = 0f; eyeM2 = 0f; yawSum = 0f; eyeSampleCount = 0 }
    }
}

object ImageSizeCalculator {
    fun compute(rotation: Int, width: Int, height: Int): Pair<Int, Int> =
        if (rotation == 90 || rotation == 270) height to width else width to height
}

interface FaceProcessor {
    fun process(image: InputImage): Task<List<Face>>
}

class MlKitFaceProcessor : FaceProcessor {
    private val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build())
    override fun process(image: InputImage): Task<List<Face>> = detector.process(image)
}

interface MainThreadPoster {
    fun post(action: () -> Unit)
}

class AndroidMainThreadPoster : MainThreadPoster {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    override fun post(action: () -> Unit) = Unit.also { handler.post(action) }
}
