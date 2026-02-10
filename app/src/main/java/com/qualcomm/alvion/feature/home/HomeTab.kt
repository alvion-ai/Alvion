package com.qualcomm.alvion.feature.home

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.google.mlkit.vision.face.Face
import com.qualcomm.alvion.R
import com.qualcomm.alvion.feature.home.components.CameraPreviewBox
import com.qualcomm.alvion.feature.home.components.GraphicOverlay
import com.qualcomm.alvion.feature.home.util.FaceDetectionAnalyzer
import kotlinx.coroutines.delay

@Composable
fun HomeTab(
    onSettings: () -> Unit,
    onSummary: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Professional Palette from Intro
    val primaryBlue = Color(0xFF2563EB)
    val secondaryCyan = Color(0xFF06B6D4)
    val surfaceLight = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)

    var isSessionActive by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var warnings by remember { mutableIntStateOf(0) }
    var speedKmh by remember { mutableIntStateOf(92) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var aiMessage by remember { mutableStateOf<String?>(null) }
    var imageWidth by remember { mutableIntStateOf(0) }
    var imageHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            elapsedSeconds = 0
            while (isSessionActive) {
                delay(1000)
                elapsedSeconds += 1
            }
        } else {
            aiMessage = null
        }
    }

    // Clear AI message after a few seconds
    LaunchedEffect(aiMessage) {
        if (aiMessage != null) {
            delay(5000)
            aiMessage = null
        }
    }

    val mediaPlayer =
        remember(context) {
            createAlertPlayer(context)
        }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    LaunchedEffect(soundEnabled) {
        if (!soundEnabled) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
        }
    }

    val faceDetectionAnalyzer =
        remember {
            FaceDetectionAnalyzer(
                onFacesDetected = { faces = it },
                onDrowsy = {
                    warnings += 1
                    aiMessage = "Drowsiness detected. Cognitive check required!"
                    if (soundEnabled) {
                        mediaPlayer?.let { player ->
                            // ONLY start if not already playing. Avoid resetting to 0 every frame.
                            if (!player.isPlaying) {
                                try {
                                    player.start()
                                } catch (e: IllegalStateException) {
                                    Log.w("HomeTab", "Alert audio failed to start", e)
                                }
                            }
                        }
                    }
                },
                onDistracted = {
                    warnings += 1
                    aiMessage = "Please stay focused on the road."
                    if (soundEnabled) {
                        mediaPlayer?.let { player ->
                            // ONLY start if not already playing. Avoid resetting to 0 every frame.
                            if (!player.isPlaying) {
                                try {
                                    player.start()
                                } catch (e: IllegalStateException) {
                                    Log.w("HomeTab", "Alert audio failed to start", e)
                                }
                            }
                        }
                    }
                },
                onImageDimensions = { width, height ->
                    imageWidth = width
                    imageHeight = height
                },
            )
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- SHARED PREMIUM BACKGROUND ---
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        Blob(
            modifier = Modifier.align(Alignment.TopStart).offset((-140).dp, (-140).dp),
            size = 380.dp,
            color = Color(0x1A3B82F6),
        )
        Blob(
            modifier = Modifier.align(Alignment.BottomEnd).offset((140).dp, (140).dp),
            size = 380.dp,
            color = Color(0x1A22D3EE),
        )
        Blob(
            modifier = Modifier.align(Alignment.Center),
            size = 260.dp,
            color = Color(0x0D60A5FA),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // --- HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    onClick = { soundEnabled = !soundEnabled },
                    modifier = Modifier.clip(CircleShape).background(if (soundEnabled) primaryBlue.copy(0.1f) else Color.Transparent),
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (soundEnabled) primaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- CAMERA CARD / STANDBY CONTENT ---
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .border(
                            width = if (isSessionActive) 2.dp else 0.5.dp,
                            brush = Brush.linearGradient(listOf(primaryBlue.copy(0.5f), secondaryCyan.copy(0.5f))),
                            shape = RoundedCornerShape(24.dp),
                        ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(if (isSessionActive) 450.dp else 220.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSessionActive) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                                    } else {
                                        Color.White.copy(alpha = 0.1f) // Glass effect when inactive
                                    },
                                ),
                    ) {
                        if (isSessionActive) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CameraPreviewBox(
                                    modifier = Modifier.fillMaxSize(),
                                    analyzer = faceDetectionAnalyzer,
                                    faces = faces,
                                    graphicOverlay = {
                                        GraphicOverlay(
                                            faces = it,
                                            imageWidth = imageWidth,
                                            imageHeight = imageHeight,
                                            isFrontCamera = true,
                                        )
                                    },
                                )

                                // Floating End Button inside Camera View
                                SmallFloatingActionButton(
                                    onClick = { isSessionActive = false },
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp),
                                    containerColor = Color.Red.copy(alpha = 0.7f),
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "End Trip", modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            // --- STANDBY DESIGN ---
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                LogoSpotlight(logoSize = 80.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Ready for your journey?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Start Trip' below to enable real-time driver monitoring.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }

                    // --- BOTTOM ACTION / MESSAGE AREA ---
                    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        if (!isSessionActive) {
                            Button(
                                onClick = { isSessionActive = true },
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start Trip", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Use Crossfade for smooth swapping between Idle and Warning
                            Crossfade(targetState = aiMessage, animationSpec = tween(500)) { message ->
                                if (message != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(0.3f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = primaryBlue.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(Icons.Default.Security, null, Modifier.size(16.dp), tint = primaryBlue.copy(0.6f))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "System Monitoring Active",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = primaryBlue.copy(0.7f),
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- METRICS GRID ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCardModern(
                        label = "Alertness",
                        value = if (warnings == 0) "Optimal" else if (warnings < 3) "Caution" else "Low",
                        icon = Icons.Default.Visibility,
                        color = if (warnings == 0) Color(0xFF10B981) else if (warnings < 3) Color(0xFFF59E0B) else Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCardModern(
                        label = "Duration",
                        value = formatHMS(elapsedSeconds),
                        icon = Icons.Default.Timer,
                        color = primaryBlue,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCardModern(
                        label = "Speed",
                        value = "$speedKmh km/h",
                        icon = Icons.Default.Speed,
                        color = primaryBlue,
                        modifier = Modifier.weight(1f),
                    )
                    EmergencyCardModern(
                        onCall = { makeEmergencyCall(context, "9513034883") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Blob(
    modifier: Modifier,
    size: Dp,
    color: Color,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .blur(80.dp)
                .background(color, CircleShape),
    )
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
        Box(
            modifier =
                Modifier
                    .size(logoSize * glowScale)
                    .blur(40.dp)
                    .background(Color(0xFF2563EB).copy(alpha = 0.15f), CircleShape),
        )
        Image(
            painter = painterResource(id = R.drawable.alvion_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
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
            Box(Modifier.fillMaxHeight().width(4.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = color)
                    Spacer(Modifier.width(6.dp))
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2).copy(0.9f)),
        onClick = onCall,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, Modifier.size(14.dp), tint = Color(0xFFEF4444))
                Spacer(Modifier.width(6.dp))
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

private fun createAlertPlayer(context: Context): MediaPlayer? {
    val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.alert_beep}")
    return MediaPlayer().apply {
        try {
            val attributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            setAudioAttributes(attributes)
            setDataSource(context, uri)
            isLooping = false
            setOnCompletionListener { it.seekTo(0) }
            setOnErrorListener { _, what, extra ->
                Log.w("HomeTab", "Alert audio error: what=$what extra=$extra")
                true
            }
            prepare()
        } catch (e: Exception) {
            Log.w("HomeTab", "Failed to prepare alert audio", e)
            return null
        }
    }
}

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
