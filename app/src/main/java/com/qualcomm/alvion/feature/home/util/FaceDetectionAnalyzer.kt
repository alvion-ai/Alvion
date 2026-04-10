package com.qualcomm.alvion.feature.home.util

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
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
    val isEyeOccluded: Boolean,
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
    private val onEyeOccluded: (Boolean) -> Unit = {},
    private val onOcclusionStateChanged: (Boolean) -> Unit = {},
    private val onPresenceCheck: () -> Unit = {},
    private val context: Context,  // For TFLite model loading
    private val detector: FaceProcessor = MlKitFaceProcessor(),
    mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
) : ImageAnalysis.Analyzer {
    val evaluator =
        FaceStateEvaluator(
            onDrowsy = onDrowsy,
            onDistracted = onDistracted,
            onFaceTooClose = onFaceTooClose,
            onEyeOccluded = { occluded ->
                onEyeOccluded(occluded)
                onOcclusionStateChanged(occluded)
            },
            onPresenceCheck = onPresenceCheck,
            mainThreadPoster = mainThreadPoster,
            context = context,
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

        if (evaluator.shouldUseSunglassesOnlyFrame()) {
            evaluator.evaluateSunglassesOnlyFrame(imageProxy, width, height)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector
            .process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                evaluator.evaluate(faces, width, height, imageProxy)

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
    private val onFaceTooClose: (Boolean) -> Unit = {},
    private val onEyeOccluded: (Boolean) -> Unit = {},
    private val onPresenceCheck: () -> Unit = {},
    private val mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val context: Context? = null,  // For TFLite model loading
    private val sunglassesClassifier: SunglassesClassifier? = context?.let { SunglassesClassifier(it) },
) {
    private val TAG = "FaceStateEvaluator"
    
    // ===== TFLite Sunglasses Classifier =====
    private var frameCounter = 0
    private val INFERENCE_INTERVAL = 3  // Run inference every 3 frames
    private val SUNGLASSES_DETECT_THRESHOLD = 0.70f
    private val SUNGLASSES_CLEAR_THRESHOLD = 0.45f
    private val SUNGLASSES_CONFIRMATION_FRAMES = 2
    private val SUNGLASSES_MLKIT_REFRESH_MS = 1200L
    private val SUNGLASSES_ONLY_INFERENCE_MS = 300L
    
    private val FACE_AREA_THRESHOLD = 0.60f
    private val DROWSY_TRIGGER_MS = 1200L
    private val DISTRACTION_MIRROR_MS = 4000L
    private val DISTRACTION_BEYOND_MS = 2000L
    private val CALLBACK_COOLDOWN_MS = 3000L
    private val GRACE_MS = 600L
    private val EMA_ALPHA = 0.45f
    private val MIN_FACE_SIZE_FRAC = 0.15f
    private val DISTRACTION_HYSTERESIS_DEG = 8f
    private val OCCLUSION_TIMEOUT_MS = 3000L
    private val PRESENCE_CHECK_MS = 10000L
    private val MOTION_BUFFER_SIZE = 5
    // Thresholds tuned for "static photo" detection. A real human typically jitters more than this.
    private val CENTER_X_VARIANCE_EPS = 0.25f // ~within ~1px range
    private val ROT_Y_VARIANCE_EPS = 0.04f // ~within ~0.4deg range

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
    
    // Sunglasses detection state
    private var sunglassesDetected = false
    private var lastSunglassesDetectionTime = 0L
    private var lastSunglassesInferenceTime = 0L
    private var lastMlKitFrameTime = 0L
    private var sunglassesHitStreak = 0
    private var sunglassesClearStreak = 0
    private var cachedFaceBox: Rect? = null
    private var lastDistractionCallbackTime = 0L
    private var isFaceTooClose = false

    // Occlusion and Motion state
    private var occlusionStartTime: Long? = null
    private var isEyeOccluded = false
    private val centerXBuffer = ArrayDeque<Int>(MOTION_BUFFER_SIZE)
    private val rotYBuffer = ArrayDeque<Float>(MOTION_BUFFER_SIZE)
    private var presenceStaticStartTime: Long? = null
    private var hasFiredPresenceCheck = false
    private var lastPresenceCallbackTime = 0L

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
            leftEye = face.leftEyeOpenProbability ?: -1f,
            rightEye = face.rightEyeOpenProbability ?: -1f,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            threshold = threshold,
            eyeEma = eyeScoreEma ?: 0f,
            isEyeOccluded = isEyeOccluded,
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
        occlusionStartTime = null
        isEyeOccluded = false
        centerXBuffer.clear()
        rotYBuffer.clear()
        presenceStaticStartTime = null
        hasFiredPresenceCheck = false
        lastPresenceCallbackTime = 0L
        openEyeStatsByBucket.values.forEach { it.reset() }
        
        // Reset sunglasses state
        sunglassesDetected = false
        frameCounter = 0
        lastSunglassesDetectionTime = 0L
        lastSunglassesInferenceTime = 0L
        lastMlKitFrameTime = 0L
        sunglassesHitStreak = 0
        sunglassesClearStreak = 0
        cachedFaceBox = null
    }
    
    /**
     * Clean up resources (call when activity destroys).
     */
    fun cleanup() {
        try {
            sunglassesClassifier?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing sunglasses classifier: ${e.message}")
        }
    }

    fun shouldUseSunglassesOnlyFrame(): Boolean {
        val now = clock()
        return monitoringEnabled &&
            sunglassesDetected &&
            cachedFaceBox != null &&
            now - lastMlKitFrameTime < SUNGLASSES_MLKIT_REFRESH_MS
    }

    @androidx.camera.core.ExperimentalGetImage
    fun evaluateSunglassesOnlyFrame(
        imageProxy: ImageProxy,
        width: Int,
        height: Int,
    ) {
        val now = clock()
        if (!sunglassesDetected) return

        cachedFaceBox?.let { faceBox ->
            runSunglassesInferenceIfDue(
                imageProxy = imageProxy,
                faceBox = faceBox,
                now = now,
                force = false,
            )
        }

        resetMlKitSafetyTrackingForSunglasses()
        if (!isEyeOccluded) {
            isEyeOccluded = true
            mainThreadPoster.post { onEyeOccluded(true) }
        }
        Log.d(TAG, "ML Kit skipped for sunglasses-only frame (${width}x$height)")
    }

    fun evaluate(
        faces: List<Face>,
        width: Int,
        height: Int,
        @androidx.camera.core.ExperimentalGetImage
        imageProxy: ImageProxy? = null,
    ) {
        val now = clock()
        lastMlKitFrameTime = now
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        if (primaryFace == null) {
            cachedFaceBox = null
            handleInvalidFrame(now)
            if (isFaceTooClose) {
                isFaceTooClose = false
                onFaceTooClose(false)
            }
            return
        }

        cachedFaceBox = Rect(primaryFace.boundingBox)

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
        val leftProb = primaryFace.leftEyeOpenProbability
        val rightProb = primaryFace.rightEyeOpenProbability
        val bothEyeProbsNull = leftProb == null && rightProb == null
        val leftLandmark = primaryFace.getLandmark(FaceLandmark.LEFT_EYE)
        val rightLandmark = primaryFace.getLandmark(FaceLandmark.RIGHT_EYE)

        // ===== TFLITE SUNGLASSES DETECTION =====
        // Run every 3 frames for performance (frame skipping)
        frameCounter++
        SunglassesDebugHelper.logFrameProcessing(frameCounter, frameCounter % INFERENCE_INTERVAL == 0)
        
        val sunglassesMonitoringActive = monitoringEnabled && !isCalibrating

        if (sunglassesMonitoringActive && frameCounter % INFERENCE_INTERVAL == 0 && imageProxy != null) {
            runSunglassesInferenceIfDue(
                imageProxy = imageProxy,
                faceBox = primaryFace.boundingBox,
                now = now,
                force = true,
            )
        }

        // If sunglasses detected, suppress drowsiness and distraction checks.
        if (sunglassesMonitoringActive && sunglassesDetected) {
            resetMlKitSafetyTrackingForSunglasses()
            if (!isEyeOccluded) {
                isEyeOccluded = true
                mainThreadPoster.post { onEyeOccluded(true) }
            }
            Log.d(TAG, "Drowsiness/distraction detection suppressed (sunglasses detected)")
            SunglassesDebugHelper.logPipelineStatus(
                modelLoaded = true,
                faceDetected = true,
                inferenceRan = frameCounter % INFERENCE_INTERVAL == 0,
                sunglassesDetected = true
            )
            return
        }

        if (bothEyeProbsNull) {
            if (occlusionStartTime == null) occlusionStartTime = now
            if (now - occlusionStartTime!! >= OCCLUSION_TIMEOUT_MS && !isEyeOccluded) {
                isEyeOccluded = true
                mainThreadPoster.post { onEyeOccluded(true) }
            }
        } else {
            occlusionStartTime = null
            if (isEyeOccluded) {
                isEyeOccluded = false
                mainThreadPoster.post { onEyeOccluded(false) }
            }
        }

        val currentCenterX = faceBox.centerX()
        // Motion detection (Presence check)
        centerXBuffer.addLast(currentCenterX)
        rotYBuffer.addLast(rotY)
        while (centerXBuffer.size > MOTION_BUFFER_SIZE) centerXBuffer.removeFirst()
        while (rotYBuffer.size > MOTION_BUFFER_SIZE) rotYBuffer.removeFirst()

        if (monitoringEnabled && isEyeOccluded) {
            val isStaticNow =
                if (centerXBuffer.size == MOTION_BUFFER_SIZE && rotYBuffer.size == MOTION_BUFFER_SIZE) {
                    val cx = centerXBuffer.map { it.toFloat() }
                    val ry = rotYBuffer.toList()

                    fun variance(samples: List<Float>): Float {
                        val mean = samples.sum() / samples.size
                        var acc = 0f
                        for (sample in samples) {
                            val delta = sample - mean
                            acc += delta * delta
                        }
                        return acc / samples.size
                    }

                    variance(cx) <= CENTER_X_VARIANCE_EPS && variance(ry) <= ROT_Y_VARIANCE_EPS
                } else {
                    false
                }

            if (isStaticNow) {
                if (presenceStaticStartTime == null) presenceStaticStartTime = now
                if (
                    now - presenceStaticStartTime!! >= PRESENCE_CHECK_MS &&
                        !hasFiredPresenceCheck &&
                        now - lastPresenceCallbackTime >= CALLBACK_COOLDOWN_MS
                ) {
                    hasFiredPresenceCheck = true
                    lastPresenceCallbackTime = now
                    mainThreadPoster.post(onPresenceCheck)
                }
            } else {
                presenceStaticStartTime = null
                hasFiredPresenceCheck = false
            }
        } else {
            presenceStaticStartTime = null
            hasFiredPresenceCheck = false
        }

        // Process Eye Score for Drowsiness
        if (leftProb != null || rightProb != null) {
            val penalty =
                (if (abs(rotZ) > 30f) 0.15f else 0f) +
                    (if (abs(rotX) > 20f) 0.10f else 0f) +
                    (if (yawAbs > 25f) 0.10f else 0f)

            val leftScore = leftProb ?: if (leftLandmark != null) 0.05f else 1f
            val rightScore = rightProb ?: if (rightLandmark != null) 0.05f else 1f
            
            val rawScore = min(leftScore, rightScore)

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
                    "forward" -> abs(yawDelta) < 12f
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

            // Pause eye-state drowsiness checks as soon as both eye-open probabilities disappear,
            // even before we confirm occlusion (3s). This prevents sunglasses from producing
            // false "drowsy" detections due to stale eyeScoreEma.
            if (!isEyeOccluded && !bothEyeProbsNull) {
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
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun runSunglassesInferenceIfDue(
        imageProxy: ImageProxy,
        faceBox: Rect,
        now: Long,
        force: Boolean,
    ) {
        val classifier = sunglassesClassifier ?: return
        if (!force && now - lastSunglassesInferenceTime < SUNGLASSES_ONLY_INFERENCE_MS) return
        lastSunglassesInferenceTime = now

        try {
            val faceBitmap = FaceCropper.cropFaceFromFrame(imageProxy, faceBox)
            if (faceBitmap != null) {
                val classificationResult = classifier.classify(faceBitmap)

                if (classificationResult.error == null) {
                    updateSunglassesState(classificationResult, now)
                } else {
                    Log.w(TAG, "Classification error: ${classificationResult.error}")
                    SunglassesDebugHelper.logError("Classification failed: ${classificationResult.error}")
                }

                faceBitmap.recycle()
            } else {
                SunglassesDebugHelper.logImageExtraction(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error: ${e.message}")
            SunglassesDebugHelper.logError("Inference exception", e)
        }
    }

    private fun updateSunglassesState(
        classificationResult: SunglassesClassifier.ClassificationResult,
        now: Long,
    ) {
        val confidentSunglasses = classificationResult.confidence >= SUNGLASSES_DETECT_THRESHOLD
        val confidentClear = classificationResult.confidence <= SUNGLASSES_CLEAR_THRESHOLD

        if (confidentSunglasses) {
            sunglassesHitStreak++
            sunglassesClearStreak = 0
        } else if (confidentClear) {
            sunglassesClearStreak++
            sunglassesHitStreak = 0
        } else {
            sunglassesHitStreak = 0
            sunglassesClearStreak = 0
        }

        if (!sunglassesDetected && sunglassesHitStreak >= SUNGLASSES_CONFIRMATION_FRAMES) {
            setSunglassesDetected(true, classificationResult, now)
        } else if (sunglassesDetected && sunglassesClearStreak >= SUNGLASSES_CONFIRMATION_FRAMES) {
            setSunglassesDetected(false, classificationResult, now)
        }
    }

    private fun setSunglassesDetected(
        detected: Boolean,
        classificationResult: SunglassesClassifier.ClassificationResult,
        now: Long,
    ) {
        if (detected == sunglassesDetected) return

        sunglassesDetected = detected
        lastSunglassesDetectionTime = now

        SunglassesDebugHelper.logStateChange(
            sunglassesDetected,
            "confidence=${"%.3f".format(classificationResult.confidence)}, raw=${"%.3f".format(classificationResult.rawOutput)}",
        )

        if (sunglassesDetected) {
            Log.d(TAG, "Sunglasses detected: ${classificationResult.confidence}")
            println("[ALVION] SUNGLASSES DETECTED: confidence=${"%.2f".format(classificationResult.confidence)}")
            resetMlKitSafetyTrackingForSunglasses()
            if (!isEyeOccluded) {
                isEyeOccluded = true
                mainThreadPoster.post { onEyeOccluded(true) }
            }
        } else {
            Log.d(TAG, "Sunglasses removed: ${classificationResult.confidence}")
            println("[ALVION] SUNGLASSES REMOVED: confidence=${"%.2f".format(classificationResult.confidence)}")
            if (isEyeOccluded) {
                isEyeOccluded = false
                mainThreadPoster.post { onEyeOccluded(false) }
            }
        }
    }

    private fun resetMlKitSafetyTrackingForSunglasses() {
        eyeScoreEma = null
        eyeClosedStartTime = null
        distractionStartTime = null
        hasFiredDrowsyForThisClosure = false
        occlusionStartTime = null
        presenceStaticStartTime = null
        hasFiredPresenceCheck = false
    }

    private fun handleInvalidFrame(now: Long) {
        if (now - lastValidMetricsTime > GRACE_MS) {
            eyeScoreEma = null
            eyeClosedStartTime = null
            distractionStartTime = null
            hasFiredDrowsyForThisClosure = false
            occlusionStartTime = null
            if (isEyeOccluded) {
                isEyeOccluded = false
                mainThreadPoster.post { onEyeOccluded(false) }
            }
            centerXBuffer.clear()
            rotYBuffer.clear()
            presenceStaticStartTime = null
            hasFiredPresenceCheck = false
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
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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
