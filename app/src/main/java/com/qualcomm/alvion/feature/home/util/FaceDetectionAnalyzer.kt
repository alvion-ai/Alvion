package com.qualcomm.alvion.feature.home.util

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetectionAnalyzer(
    private val onFacesDetected: (List<Face>) -> Unit,
    private val onDrowsy: () -> Unit,
    private val onDistracted: () -> Unit,
    private val onImageDimensions: (width: Int, height: Int) -> Unit,
) : ImageAnalysis.Analyzer {
    private var drowsinessCounter = 0
    private var distractionCounter = 0

    private val highAccuracyOpts =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

    private val detector = FaceDetection.getClient(highAccuracyOpts)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val imageHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
            onImageDimensions(imageWidth, imageHeight)

            val image = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    onFacesDetected(faces)
                    if (faces.isEmpty()) {
                        drowsinessCounter = 0
                        distractionCounter = 0
                    }
                    for (face in faces) {
                        // Drowsiness detection logic
                        if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                            val leftEyeOpenProb = face.leftEyeOpenProbability!!
                            val rightEyeOpenProb = face.rightEyeOpenProbability!!

                            if (leftEyeOpenProb < 0.4 && rightEyeOpenProb < 0.4) {
                                drowsinessCounter++
                                if (drowsinessCounter > 5) {
                                    onDrowsy()
                                }
                            } else {
                                drowsinessCounter = 0
                            }
                        }

                        // Distraction detection logic
                        val rotY = face.headEulerAngleY
                        if (rotY > 35 || rotY < -35) {
                            distractionCounter++
                            if (distractionCounter > 5) {
                                onDistracted()
                            }
                        } else {
                            distractionCounter = 0
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
}
