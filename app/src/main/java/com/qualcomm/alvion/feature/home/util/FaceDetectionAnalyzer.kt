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
 * Diagnostic data for the UI overlay.
 */
data class FaceDiagnosticInfo(
    val leftEye: Float,
    val rightEye: Float,
    val yaw: Float,
    val pitch: Float,
    val threshold: Float,
    val eyeEma: Float,
)

/**
 * Image analysis component that delegates detection logic to [FaceStateEvaluator].
 */
class FaceDetectionAnalyzer(
    private val onFacesDetected: (List<Face>) -> Unit,
    onDrowsy: () -> Unit,
    onDistracted: () -> Unit,
    private val onImageDimensions: (width: Int, height: Int) -> Unit,
    private val onDiagnosticInfo: (FaceDiagnosticInfo?) -> Unit = {},
    private val onFaceTooClose: (Boolean) -> Unit = {},
    private val detector: FaceProcessor = MlKitFaceProcessor(),
    mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
) : ImageAnalysis.Analyzer {
    val evaluator =
        FaceStateEvaluator(
            onDrowsy = onDrowsy,
            onDistracted = onDistracted,
            onFaceTooClose = onFaceTooClose,
            mainThreadPoster = mainThreadPoster,
        )

    fun setMonitoringEnabled(enabled: Boolean) {
        evaluator.monitoringEnabled = enabled
    }

    fun startCalibration(framesPerBucket: Int = 45) = evaluator.startCalibration(framesPerBucket)

    fun setCalibrationTarget(targetBucket: String?) = evaluator.setCalibrationTarget(targetBucket)

    fun getCalibrationCount(targetBucket: String): Int = evaluator.getCalibrationCount(targetBucket)

    fun finishCalibration(): Boolean = evaluator.finishCalibration()

    fun isCalibrationActive(): Boolean = evaluator.isCalibrationActive()

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val rotation = imageProxy.imageInfo.rotationDegrees

        val (width, height) = ImageSizeCalculator.compute(rotation, imageProxy.width, imageProxy.height)
        onImageDimensions(width, height)

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector
            .process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                evaluator.evaluate(faces, width, height)

                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace != null) {
                    onDiagnosticInfo(evaluator.getDiagnosticInfo(primaryFace))
                } else {
                    onDiagnosticInfo(null)
                }
            }.addOnFailureListener { it.printStackTrace() }
            .addOnCompleteListener { imageProxy.close() }
    }
}

object ImageSizeCalculator {
    fun compute(
        rotation: Int,
        width: Int,
        height: Int,
    ): Pair<Int, Int> =
        if (rotation == 90 || rotation == 270) {
            height to width
        } else {
            width to height
        }
}

class FaceStateEvaluator(
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val onFaceTooClose: (Boolean) -> Unit,
    private val mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val FACE_AREA_THRESHOLD = 0.60f
    private val DROWSY_TRIGGER_MS = 1800L
    private val DISTRACTION_MIRROR_MS = 6000L // Reduced from 8000L per user request
    private val DISTRACTION_BEYOND_MS = 3000L // Reduced from 4000L per user request
    private val CALLBACK_COOLDOWN_MS = 3000L
    private val GRACE_MS = 400L
    private val EMA_ALPHA = 0.45f
    private val MIN_FACE_SIZE_FRAC = 0.15f
    private val DISTRACTION_HYSTERESIS_DEG = 8f

    @Volatile
    var monitoringEnabled = false
    private var isCalibrating = false
    private var calibrationTargetBucket: String? = null
    private var calibrationFramesNeeded = 45
    private var forwardYawBaseline: Float? = null
    private var calibrationForwardBaseline: Float? = null
    private var distractionStartThreshold = 20f
    private var eyeScoreEma: Float? = null
    private var eyeClosedStartTime: Long? = null
    private var distractionStartTime: Long? = null
    private var lastValidMetricsTime = 0L
    private var hasFiredDrowsyForThisClosure = false
    private var lastDrowsyCallbackTime = 0L
    private var lastDistractionCallbackTime = 0L
    private var isFaceTooClose = false

    private val bucketYawMeans = mutableMapOf<String, Float>()
    private val closedThresholdByBucket = mutableMapOf<String, Float>()
    private val openEyeStatsByBucket =
        mutableMapOf(
            "forward" to RunningStats(),
            "left" to RunningStats(),
            "right" to RunningStats(),
        )

    fun getDiagnosticInfo(face: Face): FaceDiagnosticInfo {
        val yawDelta = if (forwardYawBaseline != null) face.headEulerAngleY - forwardYawBaseline!! else face.headEulerAngleY
        val bucket = bucketYawMeans.minByOrNull { abs(it.value - yawDelta) }?.key ?: "forward"
        val threshold = closedThresholdByBucket[bucket] ?: 0.40f

        return FaceDiagnosticInfo(
            leftEye = face.leftEyeOpenProbability ?: 0f,
            rightEye = face.rightEyeOpenProbability ?: 0f,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            threshold = threshold,
            eyeEma = eyeScoreEma ?: 0f,
        )
    }

    fun setCalibrationTarget(bucket: String?) {
        calibrationTargetBucket = bucket
    }

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

    fun finishCalibration(): Boolean {
        isCalibrating = false
        val forwardStats = openEyeStatsByBucket["forward"] ?: return false
        if (forwardStats.count < 5) return false

        forwardYawBaseline = forwardStats.yawMean()

        openEyeStatsByBucket.forEach { (name, stats) ->
            if (stats.count < 5) return@forEach
            val relYaw = stats.yawMean() - forwardYawBaseline!!
            bucketYawMeans[name] = relYaw

            val threshold =
                if (stats.eyeSampleCount > 5) {
                    (stats.eyeMean - 2f * stats.eyeStdDev()).coerceIn(0.15f, 0.75f)
                } else {
                    0.40f
                }
            closedThresholdByBucket[name] = threshold
        }

        if (closedThresholdByBucket.containsKey("left") && !closedThresholdByBucket.containsKey("right")) {
            bucketYawMeans["right"] = -(bucketYawMeans["left"] ?: 0f)
            closedThresholdByBucket["right"] = closedThresholdByBucket["left"]!!
        } else if (closedThresholdByBucket.containsKey("right") && !closedThresholdByBucket.containsKey("left")) {
            bucketYawMeans["left"] = -(bucketYawMeans["right"] ?: 0f)
            closedThresholdByBucket["left"] = closedThresholdByBucket["right"]!!
        }

        distractionStartThreshold = 20f
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

    fun evaluate(
        faces: List<Face>,
        width: Int,
        height: Int,
    ) {
        val now = clock()
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        if (primaryFace == null) {
            handleInvalidFrame(now)
            if (isFaceTooClose) {
                isFaceTooClose = false
                onFaceTooClose(false)
            }
            return
        }

        val faceArea = (primaryFace.boundingBox.width() * primaryFace.boundingBox.height()).toFloat()
        val imageArea = (width * height).toFloat()
        val faceAreaRatio = if (imageArea > 0) faceArea / imageArea else 0f

        if (faceAreaRatio > FACE_AREA_THRESHOLD) {
            if (!isFaceTooClose) {
                isFaceTooClose = true
                onFaceTooClose(true)
            }
            return
        } else {
            if (isFaceTooClose) {
                isFaceTooClose = false
                onFaceTooClose(false)
            }
        }

        val rotY = primaryFace.headEulerAngleY
        val rotX = primaryFace.headEulerAngleX
        val rotZ = primaryFace.headEulerAngleZ

        // Calibration Baseline Logic Fix:
        // When forward calibration starts, ensure we anchor to the CURRENT head pose
        // rather than defaulting to 0 (camera-center).
        if (isCalibrating && calibrationTargetBucket == "forward") {
            val stats = openEyeStatsByBucket["forward"]
            calibrationForwardBaseline = if (stats != null && stats.count > 0) stats.yawMean() else rotY
        }

        val baseline = forwardYawBaseline ?: calibrationForwardBaseline
        val yawDelta = if (baseline != null) rotY - baseline else 0f
        val yawAbs = abs(yawDelta)

        val faceBox = primaryFace.boundingBox
        val faceWFrac = faceBox.width().toFloat() / max(1, width)
        val faceHFrac = faceBox.height().toFloat() / max(1, height)

        if (faceWFrac < MIN_FACE_SIZE_FRAC ||
            faceHFrac < MIN_FACE_SIZE_FRAC ||
            abs(rotZ) > 45f ||
            abs(rotX) > 30f ||
            (baseline != null && yawAbs > 80f)
        ) {
            handleInvalidFrame(now)
            return
        }

        lastValidMetricsTime = now
        val left = primaryFace.leftEyeOpenProbability
        val right = primaryFace.rightEyeOpenProbability

        if (left != null || right != null) {
            val penalty =
                (if (abs(rotZ) > 30f) 0.15f else 0f) +
                    (if (abs(rotX) > 20f) 0.10f else 0f) +
                    (if (yawAbs > 25f) 0.10f else 0f)

            val rawScore =
                when {
                    left != null && right != null -> min(left, right)
                    left != null -> left
                    else -> right!!
                }

            val currentScore = (rawScore - penalty).coerceIn(0f, 1f)
            eyeScoreEma =
                if (eyeScoreEma == null) {
                    currentScore
                } else {
                    (1f - EMA_ALPHA) * eyeScoreEma!! + EMA_ALPHA * currentScore
                }
        }

        if (isCalibrating && calibrationTargetBucket != null) {
            val target = calibrationTargetBucket!!

            val isPoseCorrect =
                when (target) {
                    "forward" -> abs(yawDelta) < 12f // Relaxed to make it easier to start
                    "left" -> yawDelta > 6f
                    "right" -> yawDelta < -6f
                    else -> false
                }
            if (isPoseCorrect) openEyeStatsByBucket[target]?.add(eyeScoreEma, rotY)
        }

        if (monitoringEnabled) {
            val isDistractedByPose = yawAbs > distractionStartThreshold

            if (isDistractedByPose) {
                if (distractionStartTime == null) distractionStartTime = now
                val maxMirrorYawAbs = max(abs(bucketYawMeans["left"] ?: 0f), abs(bucketYawMeans["right"] ?: 0f))
                val mirrorThreshold = if (maxMirrorYawAbs > 0) maxMirrorYawAbs + 10f else 45f

                // Mirror checking allows up to 6s. Looking beyond that allows only 3s.
                val triggerMs = if (yawAbs <= mirrorThreshold) DISTRACTION_MIRROR_MS else DISTRACTION_BEYOND_MS

                if (now - distractionStartTime!! >= triggerMs) {
                    if (now - lastDistractionCallbackTime >= CALLBACK_COOLDOWN_MS) {
                        lastDistractionCallbackTime = now
                        mainThreadPoster.post(onDistracted)
                    }
                }
            } else if (yawAbs < (distractionStartThreshold - DISTRACTION_HYSTERESIS_DEG)) {
                distractionStartTime = null
            }

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

        fun add(
            eye: Float?,
            yaw: Float,
        ) {
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

        fun reset() {
            count = 0
            eyeMean = 0f
            eyeM2 = 0f
            yawSum = 0f
            eyeSampleCount = 0
        }
    }
}

interface FaceProcessor {
    fun process(image: InputImage): Task<List<Face>>
}

class MlKitFaceProcessor : FaceProcessor {
    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions
                .Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build(),
        )

    override fun process(image: InputImage): Task<List<Face>> = detector.process(image)
}

interface MainThreadPoster {
    fun post(action: () -> Unit)
}

class AndroidMainThreadPoster : MainThreadPoster {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun post(action: () -> Unit) {
        handler.post(action)
    }
}
