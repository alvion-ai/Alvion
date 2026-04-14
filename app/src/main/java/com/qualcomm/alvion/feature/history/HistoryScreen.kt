package com.qualcomm.alvion.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

private val HistoryBlue = Color(0xFF2563EB)
private val HistoryCyan = Color(0xFF22D3EE)
private val HistoryGreen = Color(0xFF10B981)
private val HistoryRose = Color(0xFFEF4444)
private val HistoryAmber = Color(0xFFF59E0B)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val trips by viewModel.tripHistory.collectAsState()
    val totalAlerts = remember(trips) { trips.sumOf { it.alerts.size } }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryHeroHeader(
                tripCount = trips.size,
                alertCount = totalAlerts,
                showClear = trips.isNotEmpty(),
                onClear = { viewModel.clearHistory() },
            )

            if (trips.isEmpty()) {
                EmptyHistoryState(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                ) {
                    items(trips, key = { trip ->
                        trip.id.ifBlank { "${trip.dateLabel}-${trip.durationLabel}-${trip.startTime}" }
                    }) { trip ->
                        TripCard(trip)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeroHeader(
    tripCount: Int,
    alertCount: Int,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(214.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(HistoryBlue, HistoryCyan.copy(alpha = 0.82f)))),
        )
        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .offset((-50).dp, (-48).dp)
                    .blur(48.dp)
                    .background(Color.White.copy(alpha = 0.13f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(160.dp)
                    .align(Alignment.BottomEnd)
                    .offset(44.dp, 36.dp)
                    .blur(44.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Drive History",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Recent sessions and safety detections.",
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (showClear) {
                    IconButton(
                        onClick = onClear,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear history",
                            tint = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryStatPill(Icons.Default.Route, "$tripCount trips")
                HistoryStatPill(
                    icon = if (alertCount == 0) Icons.Default.Verified else Icons.Default.Warning,
                    label = if (alertCount == 0) "No alerts" else "$alertCount alerts",
                )
            }
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
private fun HistoryStatPill(
    icon: ImageVector,
    label: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TripCard(trip: Trip) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val accent =
        when {
            trip.alerts.isEmpty() -> HistoryGreen
            trip.alerts.size < 4 -> HistoryAmber
            else -> HistoryRose
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trip.dateLabel.ifEmpty { "Recent drive" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${trip.durationLabel.ifEmpty { "--:--:--" }} drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AlertBadge(alertCount = trip.alerts.size)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryInlineStatPill(
                    icon = Icons.Default.Schedule,
                    label = trip.startTime?.let { timeFormat.format(it.toDate()) } ?: "--",
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistoryInlineStatPill(
                    icon = if (trip.alerts.isEmpty()) Icons.Default.Verified else Icons.Default.WarningAmber,
                    label = if (trip.alerts.isEmpty()) "Safe drive" else "${trip.alerts.size} alerts",
                    containerColor = accent.copy(alpha = 0.12f),
                    contentColor = accent,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    DetailRow(Icons.Default.Schedule, "Started", trip.startTime?.let { timeFormat.format(it.toDate()) } ?: "--:--")
                    DetailRow(Icons.Default.Flag, "Ended", trip.endTime?.let { timeFormat.format(it.toDate()) } ?: "--:--")

                    if (trip.alerts.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Detections",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))

                        trip.alerts.forEach { alert ->
                            AlertItem(alert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertBadge(alertCount: Int) {
    val color =
        when {
            alertCount == 0 -> HistoryGreen
            alertCount < 4 -> HistoryAmber
            else -> HistoryRose
        }
    val label = if (alertCount == 0) "Clear" else "$alertCount alerts"

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HistoryInlineStatPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
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
            label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HistoryBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = HistoryBlue)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AlertItem(alert: TripAlert) {
    val icon =
        when (alert.type) {
            "DROWSINESS" -> Icons.Default.Bedtime
            "PRESENCE_CHECK" -> Icons.Default.Security
            "PHONE_UPSIDE_DOWN" -> Icons.Default.PhoneAndroid
            else -> Icons.Default.PanTool
        }
    val color =
        when (alert.type) {
            "DROWSINESS" -> HistoryAmber
            "PRESENCE_CHECK" -> Color(0xFF60A5FA)
            "PHONE_UPSIDE_DOWN" -> Color(0xFF7C3AED)
            else -> HistoryRose
        }
    val label =
        when (alert.type) {
            "DROWSINESS" -> "Drowsiness"
            "PRESENCE_CHECK" -> "Presence Check"
            "PHONE_UPSIDE_DOWN" -> "Phone upside down"
            "DISTRACTION" -> "Distraction"
            else ->
                alert.type
                    .replace("_", " ")
                    .lowercase(Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }
        }
    val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
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
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(HistoryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.History,
                    null,
                    modifier = Modifier.size(46.dp),
                    tint = HistoryBlue,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "No Journeys Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your driving history will appear here after your first trip.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
