package com.qualcomm.alvion.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
                "Look FORWARD at the road.",
                "Look at your LEFT mirror.",
                "Look at your RIGHT mirror.",
                "Tip: Recalibrate via the Eye icon if you adjust your seat.",
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
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(
                primaryBlue,
                secondaryCyan,
                isSessionActive,
                alertSoundEnabled,
            ) { settingsViewModel.toggleAlertSound(!alertSoundEnabled) }

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

            MetricsGrid(warnings, elapsedSeconds, speedKmh, context, primaryBlue)
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
private fun Header(
    primaryBlue: Color,
    secondaryCyan: Color,
    isSessionActive: Boolean,
    soundEnabled: Boolean,
    onSoundToggle: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "ALVION",
                style =
                    TextStyle(
                        brush = Brush.horizontalGradient(listOf(primaryBlue, secondaryCyan)),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    ),
            )
            if (isSessionActive) {
                Spacer(Modifier.width(12.dp))
                LiveIndicator()
            }
        }
        IconButton(
            onClick = onSoundToggle,
            modifier = Modifier.clip(CircleShape).background(if (soundEnabled) primaryBlue.copy(0.1f) else Color.Transparent),
        ) {
            Icon(
                imageVector = if (soundEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = if (soundEnabled) primaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                        .height(if (isSessionActive) 470.dp else 220.dp)
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
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .padding(
                                            24.dp,
                                        ).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                        .padding(20.dp),
                            ) {
                                Text("Step $calibrationStep of 4", style = MaterialTheme.typography.labelLarge, color = primaryBlue)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    aiMessage?.text ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))

                                if (waitingForUserToStartStep) {
                                    Button(
                                        onClick = onStartStep,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                                    ) {
                                        Text("Start Scan")
                                    }
                                } else {
                                    LinearProgressIndicator(
                                        progress = { calibrationProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = primaryBlue,
                                        trackColor = primaryBlue.copy(0.1f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("${(calibrationProgress * 100).toInt()}% Complete", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    StandbyContent()
                }
            }
            if (!isCalibrating) {
                ActionArea(
                    isSessionActive = isSessionActive,
                    primaryBlue = primaryBlue,
                    onStartSession = onStartSession,
                    aiMessage = aiMessage,
                    invertMessageForUpsideDown = debouncedUpsideDown,
                )
            }
        }
    }
}

@Composable
private fun StandbyContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LogoSpotlight(logoSize = 80.dp)
        Spacer(Modifier.height(12.dp))
        Text("Ready for your journey?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap 'Start Trip' below to enable real-time driver monitoring.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionArea(
    isSessionActive: Boolean,
    primaryBlue: Color,
    onStartSession: () -> Unit,
    aiMessage: AIMessage?,
    invertMessageForUpsideDown: Boolean,
) {
    if (!isSessionActive) {
        Button(
            onClick = onStartSession,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue, contentColor = Color.White),
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Start Trip", fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
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
        shape = RoundedCornerShape(28.dp),
        title = {
            Text("Face Calibration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = primaryBlue)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To ensure maximum safety, Alvion needs to learn your features in 3 quick positions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                listOf(
                    "Look straight forward",
                    "Look at your left mirror",
                    "Look at your right mirror",
                ).forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(28.dp), shape = CircleShape, color = primaryBlue.copy(0.1f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (index + 1).toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryBlue,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            step,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Note: You can recalibrate any time using the Eye icon if you adjust your seat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Calibration", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Maybe Later", color = Color(0xFF60A5FA)) }
        },
    )
}

@Composable
private fun MetricsGrid(
    warnings: Int,
    elapsedSeconds: Int,
    speedKmh: Int,
    context: Context,
    primaryBlue: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCardModern(
                label = "Alertness",
                value =
                    if (warnings == 0) {
                        "Optimal"
                    } else if (warnings < 3) {
                        "Caution"
                    } else {
                        "Low"
                    },
                icon = Icons.Default.Visibility,
                color =
                    if (warnings == 0) {
                        Color(0xFF10B981)
                    } else if (warnings < 3) {
                        Color(0xFFF59E0B)
                    } else {
                        Color(0xFFEF4444)
                    },
                modifier = Modifier.weight(1f),
            )
            MetricCardModern("Duration", formatHMS(elapsedSeconds), Icons.Default.Timer, primaryBlue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCardModern("Speed", "$speedKmh km/h", Icons.Default.Speed, primaryBlue, Modifier.weight(1f))
            EmergencyCardModern({ makeEmergencyCall(context, "9513034883") }, Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyCardModern(
    onCall: () -> Unit,
    modifier: Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Card(
        onClick = onCall,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF450A0A).copy(0.9f) else Color(0xFFFEF2F2).copy(0.9f),
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, Modifier.size(14.dp), tint = Color(0xFFEF4444))
                Spacer(Modifier.width(6.6.dp))
                Text("Emergency", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
            Text("SOS Call", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
        }
    }
}

@Composable
fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
        Spacer(Modifier.width(6.6.dp))
        Text("LIVE", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
