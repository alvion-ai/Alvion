package com.qualcomm.alvion.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.mlkit.vision.face.Face
import com.qualcomm.alvion.R
import com.qualcomm.alvion.core.data.SettingsRepository
import com.qualcomm.alvion.feature.history.HistoryViewModel
import com.qualcomm.alvion.feature.history.Trip
import com.qualcomm.alvion.feature.history.TripAlert
import com.qualcomm.alvion.feature.home.components.AIMessageBox
import com.qualcomm.alvion.feature.home.components.CameraPreviewBox
import com.qualcomm.alvion.feature.home.components.GraphicOverlay
import com.qualcomm.alvion.feature.home.util.AlertAudioManager
import com.qualcomm.alvion.feature.home.util.FaceDetectionAnalyzer
import com.qualcomm.alvion.feature.home.util.FaceDiagnosticInfo
import com.qualcomm.alvion.feature.home.util.rememberCurrentSpeedKmh
import com.qualcomm.alvion.feature.home.util.rememberPhoneUpsideDown
import com.qualcomm.alvion.feature.profile.SettingsViewModel
import com.qualcomm.alvion.feature.profile.SettingsViewModelFactory
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/** Shown while the device is physically inverted; kept until rotation corrects (no auto-dismiss). */
private const val PHONE_UPSIDE_DOWN_UI_MESSAGE =
    "Phone is upside down. Flip the device for correct monitoring."

/**
 * Reduces flicker from noisy sensors / display rotation: require sustained readings before
 * flipping UI state.
 */
@Composable
private fun rememberDebouncedUpsideDown(rawUpsideDown: Boolean): Boolean {
    var debounced by remember { mutableStateOf(false) }
    LaunchedEffect(rawUpsideDown) {
        if (rawUpsideDown) {
            delay(250)
            if (rawUpsideDown) debounced = true
        } else {
            delay(750)
            if (!rawUpsideDown) debounced = false
        }
    }
    return debounced
}

enum class MessageType {
    INFO, // Blue: Instructions / Calibration
    SUCCESS, // Green: Calibration Done
    WARNING, // Red: Drowsiness / Distraction
    SYSTEM, // Default: System Monitoring Active
}

data class AIMessage(
    val text: String,
    val type: MessageType,
)

@Composable
fun HomeTab(
    onSettings: () -> Unit,
    onSummary: () -> Unit,
    historyViewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(SettingsRepository(LocalContext.current)),
        ),
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isDark = isSystemInDarkTheme()

    val primaryBlue = Color(0xFF2563EB)
    val secondaryCyan = Color(0xFF06B6D4)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.8f else 0.7f)

    val alertSoundEnabled by settingsViewModel.alertSoundEnabled.collectAsState()
    val vibrationEnabled by settingsViewModel.vibrationEnabled.collectAsState()

    var isSessionActive by remember { mutableStateOf(false) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var diagnosticInfo by remember { mutableStateOf<FaceDiagnosticInfo?>(null) }
    var warnings by remember { mutableIntStateOf(0) }
    val speedKmh = rememberCurrentSpeedKmh(enabled = isSessionActive)
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var aiMessage by remember { mutableStateOf<AIMessage?>(null) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }
    var isEyeOccluded by remember { mutableStateOf(false) }

    // --- History Tracking State ---
    var sessionStartTime by remember { mutableStateOf<Timestamp?>(null) }
    val currentSessionAlerts = remember { mutableStateListOf<TripAlert>() }
    var lastDrowsyLogTime by remember { mutableLongStateOf(0L) }
    var lastDistractedLogTime by remember { mutableLongStateOf(0L) }
    var lastPresenceLogTime by remember { mutableLongStateOf(0L) }
    var lastUpsideDownLogTime by remember { mutableLongStateOf(0L) }
    val COOLDOWN_MS = 30_000L

    // Calibration UI state
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationStep by remember { mutableIntStateOf(0) }
    var hasCalibratedOnce by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var waitingForUserToStartStep by remember { mutableStateOf(false) }
    var calibrationProgress by remember { mutableFloatStateOf(0f) }

    // Sound Manager
    val audioManager = remember { AlertAudioManager(context) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    DisposableEffect(Unit) {
        onDispose { audioManager.release() }
    }

    val phoneUpsideDown = rememberPhoneUpsideDown(isSessionActive && !isCalibrating)
    val debouncedUpsideDown = rememberDebouncedUpsideDown(phoneUpsideDown)
    var wasDebouncedUpsideDown by remember { mutableStateOf(false) }

    fun triggerAlertActions(isDrowsy: Boolean) {
        if (alertSoundEnabled) {
            if (isDrowsy) audioManager.playDrowsyAlert() else audioManager.playDistractionAlert()
        }
        if (vibrationEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(500)
            }
        }
    }

    val faceDetectionAnalyzer =
        remember {
            FaceDetectionAnalyzer(
                onFacesDetected = { faces = it },
                onDrowsy = {
                    if (isCalibrating) return@FaceDetectionAnalyzer
                    val now = System.currentTimeMillis()
                    warnings += 1
                    aiMessage = AIMessage("Drowsiness detected", MessageType.WARNING)

                    if (now - lastDrowsyLogTime > COOLDOWN_MS) {
                        currentSessionAlerts.add(TripAlert("DROWSINESS", Timestamp.now()))
                        lastDrowsyLogTime = now
                    }
                    triggerAlertActions(true)
                },
                onDistracted = {
                    if (isCalibrating) return@FaceDetectionAnalyzer
                    val now = System.currentTimeMillis()
                    warnings += 1
                    aiMessage = AIMessage("Please stay focused on the road.", MessageType.WARNING)

                    if (now - lastDistractedLogTime > COOLDOWN_MS) {
                        currentSessionAlerts.add(TripAlert("DISTRACTION", Timestamp.now()))
                        lastDistractedLogTime = now
                    }
                    triggerAlertActions(false)
                },
                onImageDimensions = { width, height ->
                    imageWidth = width
                    imageHeight = height
                },
                onDiagnosticInfo = { info ->
                    diagnosticInfo = info
                },
                onEyeOccluded = { occluded ->
                    isEyeOccluded = occluded
                    if (occluded && isSessionActive && !isCalibrating) {
                        if (aiMessage?.text != PHONE_UPSIDE_DOWN_UI_MESSAGE) {
                            aiMessage =
                                AIMessage(
                                    "Eyes not visible. Drowsiness detection paused. Monitoring head movement.",
                                    MessageType.INFO,
                                )
                        }
                    } else if (!occluded && aiMessage?.text?.contains("not visible") == true) {
                        aiMessage = AIMessage("System Monitoring Active", MessageType.SYSTEM)
                    }
                },
                onPresenceCheck = {
                    if (!isSessionActive || isCalibrating) return@FaceDetectionAnalyzer

                    val now = System.currentTimeMillis()
                    if (now - lastPresenceLogTime < COOLDOWN_MS) return@FaceDetectionAnalyzer

                    aiMessage = AIMessage("Presence Check: move your head to confirm you are a real person.", MessageType.WARNING)

                    currentSessionAlerts.add(TripAlert("PRESENCE_CHECK", Timestamp.now()))
                    lastPresenceLogTime = now
                    triggerAlertActions(false)
                },
                context = context,
            )
        }

    DisposableEffect(faceDetectionAnalyzer) {
        onDispose { faceDetectionAnalyzer.evaluator.cleanup() }
    }

    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            elapsedSeconds = 0
            sessionStartTime = Timestamp.now()
            currentSessionAlerts.clear()
            lastDrowsyLogTime = 0L
            lastDistractedLogTime = 0L
            lastPresenceLogTime = 0L
            lastUpsideDownLogTime = 0L
            wasDebouncedUpsideDown = false

            if (!hasCalibratedOnce) {
                aiMessage = AIMessage("Initial Calibration Required", MessageType.INFO)
                showCalibrationDialog = true
            } else {
                aiMessage = AIMessage("System Monitoring Active", MessageType.SYSTEM)
            }

            while (isSessionActive) {
                delay(1000)
                elapsedSeconds += 1
            }
        } else {
            sessionStartTime?.let { start ->
                val end = Timestamp.now()
                val trip =
                    Trip(
                        dateLabel = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(start.toDate()),
                        startTime = start,
                        endTime = end,
                        durationLabel = formatHMS(elapsedSeconds),
                        alerts = currentSessionAlerts.toList(),
                    )
                historyViewModel.saveTrip(trip)
            }
            aiMessage = null
            sessionStartTime = null
            diagnosticInfo = null
            isEyeOccluded = false
        }
    }

    LaunchedEffect(debouncedUpsideDown, isSessionActive, isCalibrating) {
        if (!isSessionActive) {
            wasDebouncedUpsideDown = false
            return@LaunchedEffect
        }
        if (isCalibrating) {
            return@LaunchedEffect
        }
        if (!debouncedUpsideDown) {
            if (wasDebouncedUpsideDown) {
                wasDebouncedUpsideDown = false
                if (aiMessage?.text == PHONE_UPSIDE_DOWN_UI_MESSAGE) {
                    aiMessage = AIMessage("System Monitoring Active", MessageType.SYSTEM)
                }
            }
            return@LaunchedEffect
        }

        val firstEnter = !wasDebouncedUpsideDown
        if (firstEnter) {
            wasDebouncedUpsideDown = true
            val now = System.currentTimeMillis()
            warnings += 1
            aiMessage = AIMessage(PHONE_UPSIDE_DOWN_UI_MESSAGE, MessageType.WARNING)
            if (now - lastUpsideDownLogTime > COOLDOWN_MS) {
                currentSessionAlerts.add(TripAlert("PHONE_UPSIDE_DOWN", Timestamp.now()))
                lastUpsideDownLogTime = now
            }
            triggerAlertActions(false)
        }

        while (true) {
            delay(250)
            if (aiMessage?.text != PHONE_UPSIDE_DOWN_UI_MESSAGE) {
                aiMessage = AIMessage(PHONE_UPSIDE_DOWN_UI_MESSAGE, MessageType.WARNING)
            }
        }
    }

    LaunchedEffect(aiMessage) {
        val msg = aiMessage ?: return@LaunchedEffect
        if (isCalibrating || msg.type != MessageType.WARNING) return@LaunchedEffect
        if (msg.text == PHONE_UPSIDE_DOWN_UI_MESSAGE) return@LaunchedEffect
        delay(5000)
        aiMessage = AIMessage("System Monitoring Active", MessageType.SYSTEM)
    }

    LaunchedEffect(isCalibrating) {
        if (!isCalibrating) return@LaunchedEffect

        val framesPerBucket = 45
        faceDetectionAnalyzer.setMonitoringEnabled(false)
        faceDetectionAnalyzer.startCalibration(framesPerBucket = framesPerBucket)

        val bucketNames = listOf("forward", "left", "right")
        val steps =
            listOf(
                "Face forward.",
                "Look left.",
                "Look right.",
                "Review your setup.",
            )

        for (i in steps.indices) {
            calibrationStep = i + 1
            aiMessage = AIMessage(steps[i], MessageType.INFO)

            if (i < bucketNames.size) {
                val targetBucket = bucketNames[i]
                waitingForUserToStartStep = true
                calibrationProgress = 0f

                while (waitingForUserToStartStep) {
                    delay(100)
                }

                faceDetectionAnalyzer.setCalibrationTarget(targetBucket)
                aiMessage = AIMessage("Scanning ${targetBucket.uppercase()}...", MessageType.INFO)

                val startTime = System.currentTimeMillis()
                val timeoutMs = 15000L
                while (true) {
                    val count = faceDetectionAnalyzer.getCalibrationCount(targetBucket)
                    calibrationProgress = (count.toFloat() / framesPerBucket).coerceIn(0f, 1f)
                    if (count >= framesPerBucket || System.currentTimeMillis() - startTime > timeoutMs) break
                    delay(100)
                }

                faceDetectionAnalyzer.setCalibrationTarget(null)
                aiMessage = AIMessage("Step ${i + 1} Done!", MessageType.SUCCESS)
                if (alertSoundEnabled) audioManager.playDistractionAlert()
                delay(1500)
            } else {
                calibrationProgress = 1f
                delay(4000)
            }
        }

        val ok = faceDetectionAnalyzer.finishCalibration()
        calibrationStep = 0
        isCalibrating = false
        faceDetectionAnalyzer.setMonitoringEnabled(ok)
        hasCalibratedOnce = ok

        if (ok) {
            aiMessage = AIMessage("Calibration complete. Safe travels!", MessageType.SUCCESS)
            aiMessage = AIMessage("System Monitoring Active", MessageType.SYSTEM)
        } else {
            aiMessage = AIMessage("Calibration failed. Please try again in better light.", MessageType.WARNING)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        ) {
            HomeHeroHeader(
                primaryBlue,
                secondaryCyan,
                isSessionActive,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CameraCard(
                    isSessionActive = isSessionActive,
                    surfaceColor = surfaceColor,
                    primaryBlue = primaryBlue,
                    secondaryCyan = secondaryCyan,
                    faceDetectionAnalyzer = faceDetectionAnalyzer,
                    faces = faces,
                    diagnosticInfo = diagnosticInfo,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    isCalibrating = isCalibrating,
                    hasCalibratedOnce = hasCalibratedOnce,
                    onShowCalibrationDialog = { showCalibrationDialog = true },
                    onEndSession = { isSessionActive = false },
                    onStartSession = { isSessionActive = true },
                    aiMessage = aiMessage,
                    calibrationStep = calibrationStep,
                    calibrationProgress = calibrationProgress,
                    waitingForUserToStartStep = waitingForUserToStartStep,
                    onStartStep = { waitingForUserToStartStep = false },
                    debouncedUpsideDown = debouncedUpsideDown,
                )

                MetricsGrid(isSessionActive, elapsedSeconds, speedKmh, context, primaryBlue)
            }
        }

        if (showCalibrationDialog) {
            CalibrationDialog(
                primaryBlue = primaryBlue,
                onDismiss = { showCalibrationDialog = false },
                onConfirm = {
                    showCalibrationDialog = false
                    isCalibrating = true
                },
            )
        }
    }
}

@Composable
private fun HomeHeroHeader(
    primaryBlue: Color,
    secondaryCyan: Color,
    isSessionActive: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(190.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(primaryBlue, secondaryCyan.copy(alpha = 0.82f)))),
        )

        Box(
            modifier =
                Modifier
                    .size(170.dp)
                    .offset((-42).dp, (-46).dp)
                    .blur(44.dp)
                    .background(Color.White.copy(alpha = 0.13f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(150.dp)
                    .align(Alignment.BottomEnd)
                    .offset(40.dp, 34.dp)
                    .blur(42.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ALVION",
                    style =
                        TextStyle(
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                        ),
                )
                if (isSessionActive) {
                    Spacer(Modifier.width(12.dp))
                    LiveIndicator(inverted = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSessionActive) "Monitoring your drive in real time." else "Ready when you are.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun CameraCard(
    isSessionActive: Boolean,
    surfaceColor: Color,
    primaryBlue: Color,
    secondaryCyan: Color,
    faceDetectionAnalyzer: FaceDetectionAnalyzer,
    faces: List<Face>,
    diagnosticInfo: FaceDiagnosticInfo?,
    imageWidth: Int,
    imageHeight: Int,
    isCalibrating: Boolean,
    hasCalibratedOnce: Boolean,
    onShowCalibrationDialog: () -> Unit,
    onEndSession: () -> Unit,
    onStartSession: () -> Unit,
    aiMessage: AIMessage?,
    calibrationStep: Int,
    calibrationProgress: Float,
    waitingForUserToStartStep: Boolean,
    onStartStep: () -> Unit,
    debouncedUpsideDown: Boolean,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().animateContentSize().border(
                width = if (isSessionActive) 2.dp else 0.5.dp,
                brush = Brush.linearGradient(listOf(primaryBlue.copy(0.5f), secondaryCyan.copy(0.5f))),
                shape = RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (isSessionActive) 470.dp else 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSessionActive) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                    0.3f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(0.05f)
                            },
                        ),
            ) {
                if (isSessionActive) {
                    CameraPreviewBox(
                        modifier = Modifier.fillMaxSize(),
                        analyzer = faceDetectionAnalyzer,
                        faces = faces,
                        graphicOverlay = { GraphicOverlay(it, imageWidth, imageHeight, true, diagnosticInfo) },
                    )

                    SmallFloatingActionButton(
                        onClick = { if (!isCalibrating) onShowCalibrationDialog() },
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        containerColor = if (hasCalibratedOnce) Color.White.copy(alpha = 0.7f) else primaryBlue,
                        contentColor = if (hasCalibratedOnce) primaryBlue else Color.White,
                        shape = CircleShape,
                    ) { Icon(Icons.Default.Visibility, "Recalibrate", Modifier.size(20.dp)) }

                    SmallFloatingActionButton(
                        onClick = onEndSession,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        containerColor = Color.Red.copy(alpha = 0.7f),
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) { Icon(Icons.Default.Close, "End Trip", Modifier.size(20.dp)) }

                    if (isCalibrating) {
                        CalibrationOverlay(
                            step = calibrationStep,
                            aiMessage = aiMessage,
                            progress = calibrationProgress,
                            waitingForUserToStartStep = waitingForUserToStartStep,
                            primaryBlue = primaryBlue,
                            secondaryCyan = secondaryCyan,
                            onStartStep = onStartStep,
                        )
                    }
                } else {
                    StandbyContent(
                        primaryBlue = primaryBlue,
                        secondaryCyan = secondaryCyan,
                        onStartSession = onStartSession,
                    )
                }
            }
            if (!isCalibrating && isSessionActive) {
                ActionArea(
                    isSessionActive = isSessionActive,
                    aiMessage = aiMessage,
                    invertMessageForUpsideDown = debouncedUpsideDown,
                )
            }
        }
    }
}

@Composable
private fun CalibrationOverlay(
    step: Int,
    aiMessage: AIMessage?,
    progress: Float,
    waitingForUserToStartStep: Boolean,
    primaryBlue: Color,
    secondaryCyan: Color,
    onStartStep: () -> Unit,
) {
    val currentStep = step.coerceIn(1, 4)
    val scanProgress = progress.coerceIn(0f, 1f)
    val stepProgress = if (currentStep == 4) 1f else scanProgress
    val totalProgress = (((currentStep - 1) + stepProgress) / 4f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally { it / 5 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally { -it / 5 })
            },
            label = "CalibrationStepTransition",
        ) { animatedStep ->
            Surface(
                modifier =
                    Modifier
                        .padding(18.dp)
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "Step $animatedStep of 4",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryBlue,
                            )
                            Text(
                                calibrationStepTitle(animatedStep),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Box(
                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(primaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = calibrationStepIcon(animatedStep, aiMessage?.type),
                                contentDescription = null,
                                tint = primaryBlue,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    CalibrationStepDots(currentStep = animatedStep, accent = primaryBlue)

                    LinearProgressIndicator(
                        progress = { totalProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = primaryBlue,
                        trackColor = primaryBlue.copy(alpha = 0.12f),
                    )

                    CalibrationInstructionCard(
                        step = animatedStep,
                        aiMessage = aiMessage,
                        progress = scanProgress,
                        waitingForUserToStartStep = waitingForUserToStartStep,
                        primaryBlue = primaryBlue,
                    )

                    CalibrationStepAction(
                        step = animatedStep,
                        progress = scanProgress,
                        waitingForUserToStartStep = waitingForUserToStartStep,
                        primaryBlue = primaryBlue,
                        secondaryCyan = secondaryCyan,
                        onStartStep = onStartStep,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationInstructionCard(
    step: Int,
    aiMessage: AIMessage?,
    progress: Float,
    waitingForUserToStartStep: Boolean,
    primaryBlue: Color,
) {
    val isComplete = aiMessage?.type == MessageType.SUCCESS || progress >= 1f
    val icon =
        when {
            aiMessage?.type == MessageType.WARNING -> Icons.Default.Warning
            isComplete -> Icons.Default.CheckCircle
            waitingForUserToStartStep -> Icons.Default.Info
            else -> Icons.Default.PhotoCamera
        }
    val accent =
        when {
            aiMessage?.type == MessageType.WARNING -> Color(0xFFEF4444)
            isComplete -> Color(0xFF10B981)
            else -> primaryBlue
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    calibrationInstructionTitle(waitingForUserToStartStep, isComplete, step),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    calibrationInstruction(step, waitingForUserToStartStep, isComplete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalibrationStepAction(
    step: Int,
    progress: Float,
    waitingForUserToStartStep: Boolean,
    primaryBlue: Color,
    secondaryCyan: Color,
    onStartStep: () -> Unit,
) {
    if (waitingForUserToStartStep && step <= 3) {
        Button(
            onClick = onStartStep,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Scan", fontWeight = FontWeight.Bold)
        }
    } else {
        val complete = progress >= 1f || step == 4
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (complete) Color(0xFF10B981).copy(alpha = 0.1f) else primaryBlue.copy(alpha = 0.08f),
            border =
                BorderStroke(
                    1.dp,
                    if (complete) Color(0xFF10B981).copy(alpha = 0.16f) else primaryBlue.copy(alpha = 0.12f),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (complete) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = secondaryCyan,
                        trackColor = secondaryCyan.copy(alpha = 0.12f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (complete) "Captured" else "Scanning",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (step == 4) "Saving your calibration" else "${(progress * 100).toInt()}% complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationStepDots(
    currentStep: Int,
    accent: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val step = index + 1
            val width by animateDpAsState(
                targetValue = if (step == currentStep) 28.dp else 8.dp,
                animationSpec = tween(220),
                label = "CalibrationDotWidth",
            )
            Box(
                modifier =
                    Modifier
                        .width(width)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                step == currentStep -> accent
                                step < currentStep -> Color(0xFF10B981)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                            },
                        ),
            )
        }
    }
}

private fun calibrationStepTitle(step: Int): String =
    when (step) {
        1 -> "Face forward"
        2 -> "Check left"
        3 -> "Check right"
        else -> "Finish setup"
    }

private fun calibrationInstructionTitle(
    waitingForUserToStartStep: Boolean,
    isComplete: Boolean,
    step: Int,
): String =
    when {
        isComplete && step < 4 -> "Position captured"
        step == 4 -> "Saving calibration"
        waitingForUserToStartStep -> "Ready when you are"
        else -> "Hold steady"
    }

private fun calibrationInstruction(
    step: Int,
    waitingForUserToStartStep: Boolean,
    isComplete: Boolean,
): String =
    when {
        step == 4 -> "Keep your phone mounted. This only takes a moment."
        isComplete -> "Nice. Move naturally to the next guided position."
        waitingForUserToStartStep -> "Tap Start Scan, then hold this position for a few seconds."
        step == 1 -> "Look straight ahead and keep your face centered."
        step == 2 -> "Turn toward your left mirror and hold steady."
        else -> "Turn toward your right mirror and hold steady."
    }

private fun calibrationStepIcon(
    step: Int,
    messageType: MessageType?,
): ImageVector =
    when {
        messageType == MessageType.SUCCESS -> Icons.Default.CheckCircle
        step == 4 -> Icons.Default.TaskAlt
        step == 1 -> Icons.Default.Visibility
        step == 2 -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        else -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    }

@Composable
private fun StandbyContent(
    primaryBlue: Color,
    secondaryCyan: Color,
    onStartSession: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2563EB).copy(alpha = 0.08f),
                            Color(0xFF22D3EE).copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                    ),
                ).padding(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LogoSpotlight(logoSize = 64.dp)
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Ready to drive?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Start a trip when your phone is mounted and your face is visible.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(22.dp))

            StartTripButton(
                primaryBlue = primaryBlue,
                secondaryCyan = secondaryCyan,
                onStartSession = onStartSession,
            )
        }
    }
}

@Composable
private fun StartTripButton(
    primaryBlue: Color,
    secondaryCyan: Color,
    onStartSession: () -> Unit,
) {
    Surface(
        onClick = onStartSession,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(66.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(primaryBlue, secondaryCyan.copy(alpha = 0.9f))))
                    .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Start Trip", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("Begin live monitoring", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.82f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ActionArea(
    isSessionActive: Boolean,
    aiMessage: AIMessage?,
    invertMessageForUpsideDown: Boolean,
) {
    if (isSessionActive) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (invertMessageForUpsideDown) 128.dp else 100.dp),
        ) {
            AIMessageBox(
                message = aiMessage,
                invertForUpsideDownReading = invertMessageForUpsideDown,
            )
        }
    }
}

@Composable
private fun CalibrationDialog(
    primaryBlue: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(primaryBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PhotoCamera, null, tint = primaryBlue, modifier = Modifier.size(26.dp))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Face Calibration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Four quick steps",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryBlue,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Follow each prompt and hold steady while Alvion scans.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                listOf(
                    "Face forward" to "Look straight ahead",
                    "Check left" to "Turn toward your left mirror",
                    "Check right" to "Turn toward your right mirror",
                    "Finish setup" to "Save your calibration",
                ).forEachIndexed { index, (title, instruction) ->
                    CalibrationPreviewStep(
                        number = index + 1,
                        title = title,
                        instruction = instruction,
                        primaryBlue = primaryBlue,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Begin Calibration", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Later", color = Color(0xFF60A5FA)) }
        },
    )
}

@Composable
private fun CalibrationPreviewStep(
    number: Int,
    title: String,
    instruction: String,
    primaryBlue: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(primaryBlue.copy(alpha = 0.06f))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(primaryBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = primaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(instruction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricsGrid(
    isSessionActive: Boolean,
    elapsedSeconds: Int,
    speedKmh: Int,
    context: Context,
    primaryBlue: Color,
) {
    AnimatedContent(
        targetState = isSessionActive,
        transitionSpec = {
            (fadeIn(tween(220)) + expandVertically()).togetherWith(fadeOut(tween(140)) + shrinkVertically())
        },
        label = "TripMetricsTransition",
    ) { active ->
        if (active) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCardModern("Duration", formatHMS(elapsedSeconds), Icons.Default.Timer, primaryBlue, Modifier.weight(1f))
                MetricCardModern("Speed", "$speedKmh km/h", Icons.Default.Speed, primaryBlue, Modifier.weight(1f))
            }
        } else {
            EmergencyCallButton(
                onCall = { makeEmergencyCall(context, "9513034883") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LogoSpotlight(logoSize: Dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
    )
    Box(contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(logoSize * glowScale).blur(40.dp).background(Color(0xFF2563EB).copy(alpha = 0.15f), CircleShape))
        Image(painterResource(R.drawable.alvion_logo), null, Modifier.size(logoSize))
    }
}

@Composable
fun MetricCardModern(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
    ) {
        Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = color)
                    Spacer(Modifier.width(6.6.dp))
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmergencyCallButton(
    onCall: () -> Unit,
    modifier: Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val emergencyRed = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
    Surface(
        onClick = onCall,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = emergencyRed,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Phone, null, Modifier.size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Emergency Call",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text("Tap for immediate help", fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun LiveIndicator(inverted: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )
    val liveColor = if (inverted) Color.White else Color.Red
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(liveColor.copy(alpha = alpha)))
        Spacer(Modifier.width(6.6.dp))
        Text("LIVE", color = liveColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

private fun formatHMS(s: Int): String = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)

internal fun makeEmergencyCall(
    context: Context,
    number: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
