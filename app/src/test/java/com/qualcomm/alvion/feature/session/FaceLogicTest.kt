package com.qualcomm.alvion.feature.session

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.qualcomm.alvion.feature.home.util.FaceStateEvaluator
import com.qualcomm.alvion.feature.home.util.ImageSizeCalculator
import com.qualcomm.alvion.feature.home.util.MainThreadPoster
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FaceLogicTest {
    private var currentTime = 1000L
    private val mockPoster =
        object : MainThreadPoster {
            override fun post(action: () -> Unit) = action()
        }

    private fun face(
        leftEye: Float? = 0.9f,
        rightEye: Float? = 0.9f,
        rotY: Float = 0f,
        width: Int = 200,
        height: Int = 200,
    ): Face {
        val f = mockk<Face>(relaxed = true)
        every { f.leftEyeOpenProbability } returns leftEye
        every { f.rightEyeOpenProbability } returns rightEye
        every { f.headEulerAngleY } returns rotY
        every { f.headEulerAngleX } returns 0f
        every { f.headEulerAngleZ } returns 0f
        every { f.boundingBox } returns Rect(0, 0, width, height)
        return f
    }

    // ---------- ImageSizeCalculator tests ----------
    @Test
    fun computeImageSize_rotation0_keepsSize() {
        val (w, h) = ImageSizeCalculator.compute(rotation = 0, width = 640, height = 480)
        assertEquals(640, w)
        assertEquals(480, h)
    }

    // ---------- FaceStateEvaluator tests ----------
    @Test
    fun evaluator_triggersDrowsy_afterTimeThreshold() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = mockPoster,
                clock = { currentTime },
            )
        evaluator.monitoringEnabled = true

        val closed = face(leftEye = 0.1f, rightEye = 0.1f)

        // 1st frame: eye closed at T=1000
        evaluator.evaluate(listOf(closed), 1000, 1000)
        assertEquals(0, drowsyCalls.get())

        // Move time forward by 2.9s (threshold is 3s)
        currentTime += 2900
        evaluator.evaluate(listOf(closed), 1000, 1000)
        assertEquals(0, drowsyCalls.get())

        // Move past 3s
        currentTime += 200
        evaluator.evaluate(listOf(closed), 1000, 1000)
        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_triggersDistracted_afterTimeThreshold() {
        val distractedCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = mockPoster,
                clock = { currentTime },
            )
        evaluator.monitoringEnabled = true

        // Default threshold is 20, let's turn 25
        val turned = face(rotY = 25f)

        evaluator.evaluate(listOf(turned), 1000, 1000)
        assertEquals(0, distractedCalls.get())

        // Move time forward by 4.1s (beyond threshold is 4s)
        currentTime += 4100
        evaluator.evaluate(listOf(turned), 1000, 1000)
        assertEquals(1, distractedCalls.get())
    }

    @Test
    fun evaluator_calibration_adjustsThresholds() {
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                mainThreadPoster = mockPoster,
                clock = { currentTime },
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")

        // Feed 10 frames of very open eyes (1.0) at yaw 0
        val veryOpenForward = face(leftEye = 1.0f, rightEye = 1.0f, rotY = 0f)
        repeat(10) { evaluator.evaluate(listOf(veryOpenForward), 1000, 1000) }

        evaluator.finishCalibration()
        evaluator.monitoringEnabled = true

        // Now eyes at 0.3 should be considered "closed"
        val halfClosed = face(leftEye = 0.3f, rightEye = 0.3f)

        evaluator.evaluate(listOf(halfClosed), 1000, 1000)
        currentTime += 2000
    }
}
