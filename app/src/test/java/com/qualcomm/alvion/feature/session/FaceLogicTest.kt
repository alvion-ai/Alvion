package com.qualcomm.alvion.feature.session

import com.google.mlkit.vision.face.Face
import com.qualcomm.alvion.feature.home.util.FaceStateEvaluator
import com.qualcomm.alvion.feature.home.util.ImageSizeCalculator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FaceLogicTest {
    // ---------- Helpers ----------
    private fun face(
        leftEye: Float? = null,
        rightEye: Float? = null,
        rotY: Float = 0f,
    ): Face {
        val f = mockk<Face>(relaxed = true)
        every { f.leftEyeOpenProbability } returns leftEye
        every { f.rightEyeOpenProbability } returns rightEye
        every { f.headEulerAngleY } returns rotY
        return f
    }

    // ---------- ImageSizeCalculator tests ----------
    @Test
    fun computeImageSize_rotation0_keepsSize() {
        val (w, h) = ImageSizeCalculator.compute(rotation = 0, width = 640, height = 480)
        assertEquals(640, w)
        assertEquals(480, h)
    }

    @Test
    fun computeImageSize_rotation90_swapsSize() {
        val (w, h) = ImageSizeCalculator.compute(rotation = 90, width = 640, height = 480)
        assertEquals(480, w)
        assertEquals(640, h)
    }

    @Test
    fun computeImageSize_rotation270_swapsSize() {
        val (w, h) = ImageSizeCalculator.compute(rotation = 270, width = 1920, height = 1080)
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    // ---------- FaceStateEvaluator tests ----------
    @Test
    fun evaluator_resetsCounters_whenNoFaces() {
        val drowsyCalls = AtomicInteger(0)
        val distractedCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { distractedCalls.incrementAndGet() },
            )

        // Build up some "bad" frames first
        val sleepyTurned = face(leftEye = 0.1f, rightEye = 0.1f, rotY = 50f)
        repeat(3) { evaluator.evaluate(listOf(sleepyTurned)) }

        // Losing the face should reset
        evaluator.evaluate(emptyList())

        // Now it should take full threshold again to trigger
        repeat(5) { evaluator.evaluate(listOf(sleepyTurned)) }
        assertEquals(0, drowsyCalls.get())
        assertEquals(0, distractedCalls.get())
    }

    @Test
    fun evaluator_triggersDrowsy_after6ConsecutiveClosedFrames() {
        val drowsyCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { /* ignore */ },
            )

        val sleepy = face(leftEye = 0.2f, rightEye = 0.2f, rotY = 0f)

        // Threshold is > 5, so 5 frames should not trigger
        repeat(5) { evaluator.evaluate(listOf(sleepy)) }
        assertEquals(0, drowsyCalls.get())

        // 6th consecutive frame triggers
        evaluator.evaluate(listOf(sleepy))
        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_drowsyResets_whenEyesOpen() {
        val drowsyCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { /* ignore */ },
            )

        val closed = face(leftEye = 0.2f, rightEye = 0.2f, rotY = 0f)
        val open = face(leftEye = 0.9f, rightEye = 0.9f, rotY = 0f)

        // Build 3 sleepy frames
        repeat(3) { evaluator.evaluate(listOf(closed)) }

        // Open eyes should reset drowsy counter
        evaluator.evaluate(listOf(open))

        // Now it should again take 6 closed frames to trigger
        repeat(5) { evaluator.evaluate(listOf(closed)) }
        assertEquals(0, drowsyCalls.get())

        evaluator.evaluate(listOf(closed))
        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_doesNotDrowsyTrigger_whenEyeProbabilitiesMissing() {
        val drowsyCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { /* ignore */ },
            )

        val unknownEyes = face(leftEye = null, rightEye = null, rotY = 0f)

        repeat(50) { evaluator.evaluate(listOf(unknownEyes)) }
        assertEquals(0, drowsyCalls.get())
    }

    @Test
    fun evaluator_triggersDistracted_after6ConsecutiveTurnedFrames() {
        val distractedCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { /* ignore */ },
                onDistracted = { distractedCalls.incrementAndGet() },
            )

        val turned = face(leftEye = 0.9f, rightEye = 0.9f, rotY = 40f)

        repeat(5) { evaluator.evaluate(listOf(turned)) }
        assertEquals(0, distractedCalls.get())

        evaluator.evaluate(listOf(turned))
        assertEquals(1, distractedCalls.get())
    }

    @Test
    fun evaluator_distractionResets_whenFacingForward() {
        val distractedCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { /* ignore */ },
                onDistracted = { distractedCalls.incrementAndGet() },
            )

        val turned = face(rotY = -50f)
        val forward = face(rotY = 0f)

        repeat(3) { evaluator.evaluate(listOf(turned)) }
        evaluator.evaluate(listOf(forward)) // reset
        repeat(5) { evaluator.evaluate(listOf(turned)) }

        assertEquals(0, distractedCalls.get())
        evaluator.evaluate(listOf(turned))
        assertEquals(1, distractedCalls.get())
    }
}
