package com.qualcomm.alvion.feature.home.util

import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Refactor goals:
 * 1) Pull pure logic out of analyze() so it can be unit tested on the JVM.
 * 2) Inject dependencies (detector + "post to main") so tests don't need Android Looper/MLKit.
 */
class FaceDetectionAnalyzer(
    private val onFacesDetected: (List<Face>) -> Unit,
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val onImageDimensions: (width: Int, height: Int) -> Unit,
    // Dependency injection for testability (defaults keep current behavior)
    private val detector: FaceProcessor = MlKitFaceProcessor(),
    private val mainThreadPoster: MainThreadPoster = AndroidMainThreadPoster(),
    private val evaluator: FaceStateEvaluator =
        FaceStateEvaluator(
            onDrowsy = onDrowsy,
            onDistracted = onDistracted,
        ),
) : ImageAnalysis.Analyzer {
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val (imageWidth, imageHeight) =
            ImageSizeCalculator.compute(
                rotation = rotation,
                width = imageProxy.width,
                height = imageProxy.height,
            )

        // Post to main so Compose state updates are safe (in tests, poster can be synchronous)
        mainThreadPoster.post {
            onImageDimensions(imageWidth, imageHeight)
        }

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector
            .process(image)
            .addOnSuccessListener { faces ->
                onFacesDetected(faces)
                evaluator.evaluate(faces)
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }
}

/** Pure: easy JVM unit tests */
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

/**
 * Pure(ish) state machine: easy JVM unit tests.
 * Keeps counters inside this class instead of the analyzer.
 */
class FaceStateEvaluator(
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val drowsyEyeThreshold: Float = 0.4f,
    private val distractionAngleThreshold: Float = 35f,
    private val consecutiveFramesThreshold: Int = 5,
) {
    private var drowsinessCounter = 0
    private var distractionCounter = 0

    fun reset() {
        drowsinessCounter = 0
        distractionCounter = 0
    }

    fun evaluate(faces: List<Face>) {
        if (faces.isEmpty()) {
            reset()
            return
        }

        for (face in faces) {
            // Drowsiness
            val left = face.leftEyeOpenProbability
            val right = face.rightEyeOpenProbability
            if (left != null && right != null) {
                if (left < drowsyEyeThreshold && right < drowsyEyeThreshold) {
                    drowsinessCounter++
                    if (drowsinessCounter > consecutiveFramesThreshold) onDrowsy()
                } else {
                    drowsinessCounter = 0
                }
            }

            // Distraction
            val rotY = face.headEulerAngleY
            if (rotY > distractionAngleThreshold || rotY < -distractionAngleThreshold) {
                distractionCounter++
                if (distractionCounter > consecutiveFramesThreshold) onDistracted()
            } else {
                distractionCounter = 0
            }
        }
    }
}

/**
 * Abstraction over ML Kit processing so you can stub it in JVM tests.
 */
interface FaceProcessor {
    fun process(image: InputImage): Task<List<Face>>
}

/** Default production implementation */
class MlKitFaceProcessor : FaceProcessor {
    private val opts =
        FaceDetectorOptions
            .Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

    private val detector = FaceDetection.getClient(opts)

    override fun process(image: InputImage): Task<List<Face>> = detector.process(image)
}

/**
 * Abstraction for posting to main thread.
 * In unit tests you can use ImmediatePoster which runs synchronously.
 */
interface MainThreadPoster {
    fun post(block: () -> Unit)
}

class AndroidMainThreadPoster : MainThreadPoster {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(block: () -> Unit) {
        handler.post(block) // ignore Boolean
    }
}

/** Useful in JVM tests */
class ImmediatePoster : MainThreadPoster {
    override fun post(block: () -> Unit) = block()
}
