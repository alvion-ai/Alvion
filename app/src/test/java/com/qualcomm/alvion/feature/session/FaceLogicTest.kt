package com.qualcomm.alvion.feature.session

import com.qualcomm.alvion.feature.home.util.FaceStateEvaluator
import com.qualcomm.alvion.feature.home.util.ImageSizeCalculator
import com.qualcomm.alvion.feature.home.util.MainThreadPoster
import com.google.mlkit.vision.face.Face
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FaceLogicTest {
    private var currentTime = 1000L
    private val mockPoster = object : MainThreadPoster {
        override fun post(action: () -> Unit) = action()
    }

    private fun face(
        leftEye: Float? = 0.9f,
        rightEye: Float? = 0.9f,
        rotY: Float = 0f,
        width: Int = 200,
        height: Int = 200
    ): Face {
        val f = mockk<Face>(relaxed = true)
        every { f.leftEyeOpenProbability } returns leftEye
        every { f.rightEyeOpenProbability } returns rightEye
        every { f.headEulerAngleY } returns rotY
        every { f.headEulerAngleX } returns 0f
        every { f.headEulerAngleZ } returns 0f
        val mockRect = mockk<android.graphics.Rect>()
        every { mockRect.width() } returns width
        every { mockRect.height() } returns height
        every { f.boundingBox } returns mockRect
        return f
    }

    @Test
    fun computeImageSize_rotation0_keepsSize() {
        val (w, h) = ImageSizeCalculator.compute(rotation = 0, width = 640, height = 480)
        assertEquals(640, w)
        assertEquals(480, h)
    }

    @Test
    fun evaluator_triggersDrowsy_afterTimeThreshold() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator = FaceStateEvaluator(
            onDrowsy = { drowsyCalls.incrementAndGet() },
            onDistracted = {},
            mainThreadPoster = mockPoster,
            clock = { currentTime }
        )
        evaluator.monitoringEnabled = true

        val closed = face(leftEye = 0.1f, rightEye = 0.1f)
        evaluator.evaluate(listOf(closed), 1000, 1000)
        
        currentTime += 3100
        evaluator.evaluate(listOf(closed), 1000, 1000)
        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_triggersDistracted_afterTimeThreshold() {
        val distractedCalls = AtomicInteger(0)
        val evaluator = FaceStateEvaluator(
            onDrowsy = {},
            onDistracted = { distractedCalls.incrementAndGet() },
            mainThreadPoster = mockPoster,
            clock = { currentTime }
        )
        evaluator.monitoringEnabled = true

        // Initial pose to establish baseline if needed, though not strictly required for yawAbs
        val forward = face(rotY = 0f)
        evaluator.evaluate(listOf(forward), 1000, 1000)

        // Threshold is 20, use 25 for distraction
        val turned = face(rotY = 25f)
        evaluator.evaluate(listOf(turned), 1000, 1000)
        
        // Since no mirrors are calibrated, it should use DISTRACTION_BEYOND_MS (4s)
        currentTime += 4100
        evaluator.evaluate(listOf(turned), 1000, 1000)
        assertEquals(1, distractedCalls.get())
    }

    @Test
    fun evaluator_calibration_adjustsThresholds() {
        val evaluator = FaceStateEvaluator(
            onDrowsy = {},
            onDistracted = {},
            mainThreadPoster = mockPoster,
            clock = { currentTime }
        )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")
        val veryOpenForward = face(leftEye = 1.0f, rightEye = 1.0f, rotY = 0f)
        repeat(10) { evaluator.evaluate(listOf(veryOpenForward), 1000, 1000) }

        evaluator.finishCalibration()
        evaluator.monitoringEnabled = true

        val halfClosed = face(leftEye = 0.3f, rightEye = 0.3f)
        evaluator.evaluate(listOf(halfClosed), 1000, 1000)
    }
}
