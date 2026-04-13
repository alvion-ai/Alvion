package com.qualcomm.alvion.feature.home.util

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FaceLogicTest {
    private var currentTime = 1000L

    @Before
    fun setUp() {
        currentTime = 1000L
    }

    private val immediatePoster =
        object : MainThreadPoster {
            override fun post(action: () -> Unit) = action()
        }

    private fun face(
        leftEye: Float? = 0.9f,
        rightEye: Float? = 0.9f,
        rotY: Float = 0f,
        rotX: Float = 0f,
        rotZ: Float = 0f,
        centerX: Int = 500,
        width: Int = 200,
        height: Int = 200,
    ): Face {
        val f = mockk<Face>(relaxed = true)
        every { f.leftEyeOpenProbability } returns leftEye
        every { f.rightEyeOpenProbability } returns rightEye
        every { f.headEulerAngleY } returns rotY
        every { f.headEulerAngleX } returns rotX
        every { f.headEulerAngleZ } returns rotZ

        val rect = mockk<Rect>()
        every { rect.width() } returns width
        every { rect.height() } returns height
        every { rect.centerX() } returns centerX
        every { f.boundingBox } returns rect
        every { f.getLandmark(any()) } returns null

        return f
    }

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

    @Test
    fun evaluator_doesNotTriggerDrowsy_beforeThreshold() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
                onFaceTooClose = TODO(),
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // EMA still above threshold

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // EMA now below threshold; timer starts here

        currentTime += 1700
        evaluator.evaluate(listOf(closed), 1000, 1000) // still below 1800ms from start

        assertEquals(0, drowsyCalls.get())
    }

    @Test
    fun evaluator_triggersDrowsy_afterTimeThreshold() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // EMA above threshold

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // EMA drops below threshold, timer starts

        currentTime += 1900
        evaluator.evaluate(listOf(closed), 1000, 1000) // crosses 1800ms

        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_drowsy_firesOnlyOnce_forSameClosure() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts here

        currentTime += 1900
        evaluator.evaluate(listOf(closed), 1000, 1000) // first fire

        currentTime += 4000
        evaluator.evaluate(listOf(closed), 1000, 1000) // still same closure

        assertEquals(1, drowsyCalls.get())
    }

    @Test
    fun evaluator_drowsy_canFireAgain_afterEyesReopen() {
        val drowsyCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts here

        currentTime += 1900
        evaluator.evaluate(listOf(closed), 1000, 1000) // first fire

        currentTime += 100
        evaluator.evaluate(listOf(open), 1000, 1000) // reopen resets closure state

        currentTime += 3100 // cooldown passes
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts again here

        currentTime += 1900
        evaluator.evaluate(listOf(closed), 1000, 1000) // second fire

        assertEquals(2, drowsyCalls.get())
    }

    @Test
    fun evaluator_triggersDistracted_afterTimeThreshold() {
        val distractedCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        // establish forward baseline
        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")
        repeat(6) {
            evaluator.evaluate(listOf(face(rotY = 0f, leftEye = 0.95f, rightEye = 0.95f)), 1000, 1000)
        }
        assertTrue(evaluator.finishCalibration())

        evaluator.monitoringEnabled = true

        val turnedFar = face(rotY = 50f)

        evaluator.evaluate(listOf(turnedFar), 1000, 1000)
        currentTime += 4100
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)

        assertEquals(1, distractedCalls.get())
    }

    @Test
    fun evaluator_doesNotTriggerDistracted_beforeThreshold() {
        val distractedCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
                onFaceTooClose = TODO(),
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")
        repeat(6) {
            evaluator.evaluate(listOf(face(rotY = 0f, leftEye = 0.95f, rightEye = 0.95f)), 1000, 1000)
        }
        assertTrue(evaluator.finishCalibration())

        evaluator.monitoringEnabled = true

        val turnedFar = face(rotY = 50f)

        evaluator.evaluate(listOf(turnedFar), 1000, 1000)
        currentTime += 3900
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)

        assertEquals(0, distractedCalls.get())
    }

    @Test
    fun evaluator_distractionCooldown_preventsImmediateSecondCallback() {
        val distractedCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
                onFaceTooClose = TODO(),
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")
        repeat(6) {
            evaluator.evaluate(
                listOf(face(rotY = 0f, leftEye = 0.95f, rightEye = 0.95f)),
                1000,
                1000,
            )
        }
        assertTrue(evaluator.finishCalibration())

        evaluator.monitoringEnabled = true

        val turnedFar = face(rotY = 50f)
        val forward = face(rotY = 0f)

        // First distraction callback
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)
        currentTime += 4100
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)
        assertEquals(1, distractedCalls.get())

        // Reset distraction state by looking forward
        currentTime += 100
        evaluator.evaluate(listOf(forward), 1000, 1000)

        // Start another distraction event, but finish it before cooldown expires
        currentTime += 100
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)

        currentTime += 2900
        evaluator.evaluate(listOf(turnedFar), 1000, 1000)

        // Still only one callback because cooldown has not expired yet
        assertEquals(1, distractedCalls.get())
    }

    @Test
    fun evaluator_ignoresTooSmallFace() {
        val drowsyCalls = AtomicInteger(0)
        val distractedCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val tinyFace =
            face(
                leftEye = 0.0f,
                rightEye = 0.0f,
                rotY = 30f,
                width = 100,
                height = 100,
            )

        evaluator.evaluate(listOf(tinyFace), 1000, 1000)
        currentTime += 5000
        evaluator.evaluate(listOf(tinyFace), 1000, 1000)

        assertEquals(0, drowsyCalls.get())
        assertEquals(0, distractedCalls.get())
    }

    @Test
    fun evaluator_invalidFrameAfterGrace_resetsDrowsyProgress() {
        val drowsyCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)
        val invalid = face(leftEye = 0.05f, rightEye = 0.05f, rotZ = 50f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts here

        currentTime += 1000
        evaluator.evaluate(listOf(invalid), 1000, 1000) // beyond grace => reset

        currentTime += 100
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts again

        currentTime += 1000
        evaluator.evaluate(listOf(closed), 1000, 1000) // still not enough

        assertEquals(0, drowsyCalls.get())
    }

    @Test
    fun evaluator_noFaceAfterGrace_resetsDrowsyProgress() {
        val drowsyCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true

        val open = face(leftEye = 0.95f, rightEye = 0.95f)
        val closed = face(leftEye = 0.05f, rightEye = 0.05f)

        evaluator.evaluate(listOf(open), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000) // timer starts here

        currentTime += 1000
        evaluator.evaluate(emptyList(), 1000, 1000) // beyond grace => reset

        currentTime += 100
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 50
        evaluator.evaluate(listOf(closed), 1000, 1000)

        currentTime += 1000
        evaluator.evaluate(listOf(closed), 1000, 1000)

        assertEquals(0, drowsyCalls.get())
    }

    @Test
    fun calibrationState_startsAndStopsCorrectly() {
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        assertFalse(evaluator.isCalibrationActive())

        evaluator.startCalibration()
        assertTrue(evaluator.isCalibrationActive())

        evaluator.finishCalibration()
        assertFalse(evaluator.isCalibrationActive())
    }

    @Test
    fun calibrationCount_increases_forForwardSamples() {
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")

        val openForward = face(leftEye = 0.95f, rightEye = 0.95f, rotY = 0f)

        repeat(6) {
            evaluator.evaluate(listOf(openForward), 1000, 1000)
        }

        assertTrue(evaluator.getCalibrationCount("forward") > 0)
    }

    @Test
    fun finishCalibration_returnsFalse_whenNotEnoughForwardSamples() {
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")

        repeat(4) {
            evaluator.evaluate(listOf(face(rotY = 0f)), 1000, 1000)
        }

        assertFalse(evaluator.finishCalibration())
    }

    @Test
    fun finishCalibration_returnsTrue_withEnoughForwardSamples() {
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.startCalibration()
        evaluator.setCalibrationTarget("forward")

        repeat(6) {
            evaluator.evaluate(listOf(face(leftEye = 0.95f, rightEye = 0.95f, rotY = 0f)), 1000, 1000)
        }

        assertTrue(evaluator.finishCalibration())
    }

    @Test
    fun evaluator_doesNothing_whenMonitoringDisabled() {
        val drowsyCalls = AtomicInteger(0)
        val distractedCalls = AtomicInteger(0)

        val evaluator =
            FaceStateEvaluator(
                onDrowsy = { drowsyCalls.incrementAndGet() },
                onDistracted = { distractedCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = false

        val closedAndTurned = face(leftEye = 0.0f, rightEye = 0.0f, rotY = 50f)

        evaluator.evaluate(listOf(closedAndTurned), 1000, 1000)
        currentTime += 5000
        evaluator.evaluate(listOf(closedAndTurned), 1000, 1000)

        assertEquals(0, drowsyCalls.get())
        assertEquals(0, distractedCalls.get())
    }

    @Test
    fun evaluator_eyeOcclusion_triggersOnlyAfter3SecondsOfNullProbabilities() {
        val occlusionEvents = mutableListOf<Boolean>()
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                onEyeOccluded = { occlusionEvents.add(it) },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true
        val occludedFace = face(leftEye = null, rightEye = null)

        evaluator.evaluate(listOf(occludedFace), 1000, 1000)
        currentTime += 2900
        evaluator.evaluate(listOf(occludedFace), 1000, 1000)
        assertTrue(occlusionEvents.isEmpty())

        currentTime += 200
        evaluator.evaluate(listOf(occludedFace), 1000, 1000)
        assertEquals(listOf(true), occlusionEvents)

        currentTime += 50
        evaluator.evaluate(listOf(face(leftEye = 0.9f, rightEye = 0.9f)), 1000, 1000)
        assertEquals(listOf(true, false), occlusionEvents)
    }

    @Test
    fun evaluator_presenceCheck_triggersAfter10SecondsStaticWhileOccluded() {
        val presenceCalls = AtomicInteger(0)
        val evaluator =
            FaceStateEvaluator(
                onDrowsy = {},
                onDistracted = {},
                onPresenceCheck = { presenceCalls.incrementAndGet() },
                mainThreadPoster = immediatePoster,
                clock = { currentTime },
            )

        evaluator.monitoringEnabled = true
        val occludedStaticFace = face(leftEye = null, rightEye = null, rotY = 0f, centerX = 500)

        evaluator.evaluate(listOf(occludedStaticFace), 1000, 1000)
        currentTime += 3000
        evaluator.evaluate(listOf(occludedStaticFace), 1000, 1000) // occlusion confirmed

        repeat(10) {
            currentTime += 1000
            evaluator.evaluate(listOf(occludedStaticFace), 1000, 1000)
        }

        assertEquals(1, presenceCalls.get())
    }
}
