package com.qualcomm.alvion.feature.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val trips by viewModel.tripHistory.collectAsState()

    val primaryBlue = Color(0xFF2563EB)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // --- MODERN HEADER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "TRIP",
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryBlue,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "History",
                        style =
                            TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (trips.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.1f)),
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            "Clear All",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        if (trips.isEmpty()) {
            EmptyHistoryState(primaryBlue)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(24.dp),
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripCardModern(trip, primaryBlue)
                }
            }
        }
    }
}

@Composable
fun TripCardModern(
    trip: Trip,
    primaryBlue: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val alertCount = trip.alerts.size
    val accentColor =
        when {
            alertCount == 0 -> Color(0xFF10B981)
            alertCount < 4 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { expanded = !expanded }
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            ),
                        ),
                    shape = RoundedCornerShape(24.dp),
                ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Time Indicator Pill
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.05f)),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (alertCount == 0) Icons.Rounded.DoneAll else Icons.Rounded.Route,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trip.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        trip.durationLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Expand Icon with rotation animation
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "Rotation",
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            // Quick Stats Row
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPill(
                    icon = Icons.Rounded.Schedule,
                    text = trip.startTime?.let { timeFormat.format(it.toDate()) } ?: "--",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                )

                if (alertCount > 0) {
                    StatPill(
                        icon = Icons.Rounded.WarningAmber,
                        text = "$alertCount Alerts",
                        containerColor = accentColor.copy(alpha = 0.1f),
                        contentColor = accentColor,
                    )
                } else {
                    StatPill(
                        icon = Icons.Rounded.VerifiedUser,
                        text = "Safe Drive",
                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f),
                        contentColor = Color(0xFF10B981),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "TRIP DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    )

                    Spacer(Modifier.height(12.dp))

                    DetailRowModern(
                        Icons.AutoMirrored.Rounded.Login,
                        "Departure",
                        trip.startTime?.let { timeFormat.format(it.toDate()) } ?: "Unknown",
                    )
                    DetailRowModern(
                        Icons.AutoMirrored.Rounded.Logout,
                        "Arrival",
                        trip.endTime?.let { timeFormat.format(it.toDate()) } ?: "In progress",
                    )

                    if (trip.alerts.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "DETECTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(12.dp))

                        trip.alerts.forEach { alert ->
                            AlertItemModern(alert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = contentColor)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

@Composable
fun DetailRowModern(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$label ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun AlertItemModern(alert: TripAlert) {
    val isDrowsy = alert.type == "DROWSINESS"
    val icon = if (isDrowsy) Icons.Rounded.Face else Icons.Rounded.PanTool
    val color = if (isDrowsy) Color(0xFFF59E0B) else Color(0xFFEF4444)
    val label =
        when (alert.type) {
            "DROWSINESS" -> "Drowsiness Detected"
            "DISTRACTION" -> "Distraction Alert"
            else ->
                alert.type
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
        }
    val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                alert.timestamp?.let { timeFormat.format(it.toDate()) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyHistoryState(primaryBlue: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = primaryBlue.copy(alpha = 0.05f),
                ) {}
                Icon(
                    Icons.Rounded.AutoGraph,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = primaryBlue.copy(alpha = 0.3f),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "No Journeys Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your driving history and safety metrics will appear here once you complete your first trip.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            Surface(
                color = primaryBlue.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = primaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Start a trip from the home screen to begin tracking.",
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryBlue,
                    )
                }
            }
        }
    }
}
