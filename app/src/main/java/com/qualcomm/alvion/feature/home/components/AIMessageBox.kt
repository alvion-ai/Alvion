package com.qualcomm.alvion.feature.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qualcomm.alvion.feature.home.AIMessage
import com.qualcomm.alvion.feature.home.MessageType

@Composable
fun AIMessageBox(
    message: AIMessage?,
    modifier: Modifier = Modifier,
    /** Rotate banner 180° so it reads correctly when the device is physically upside down. */
    invertForUpsideDownReading: Boolean = false,
) {
    val primaryBlue = Color(0xFF2563EB)

    Crossfade(
        targetState = message,
        animationSpec = tween(500),
        modifier =
            modifier.graphicsLayer {
                rotationZ = if (invertForUpsideDownReading) 180f else 0f
                clip = false
            },
    ) { msg ->
        val (containerColor, contentColor, icon, borderStroke) =
            when (msg?.type) {
                MessageType.WARNING ->
                    MessageStyle(
                        containerColor = Color(0xFFFEF2F2),
                        contentColor = Color(0xFFEF4444),
                        icon = Icons.Default.Warning,
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(0.3f)),
                    )
                MessageType.INFO ->
                    MessageStyle(
                        containerColor = primaryBlue.copy(alpha = 0.1f),
                        contentColor = primaryBlue,
                        icon = Icons.Default.Info,
                        border = BorderStroke(1.dp, primaryBlue.copy(0.3f)),
                    )
                MessageType.SUCCESS ->
                    MessageStyle(
                        containerColor = Color(0xFFF0FDF4),
                        contentColor = Color(0xFF22C55E),
                        icon = Icons.Default.CheckCircle,
                        border = BorderStroke(1.dp, Color(0xFF22C55E).copy(0.3f)),
                    )
                else ->
                    MessageStyle(
                        containerColor = primaryBlue.copy(alpha = 0.05f),
                        contentColor = primaryBlue.copy(0.7f),
                        icon = Icons.Default.Security,
                        border = null,
                    )
            }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            border = borderStroke,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (msg == null) 16.dp else 20.dp),
                )
                Spacer(Modifier.width(if (msg == null) 8.dp else 12.dp))
                Text(
                    text = msg?.text ?: "System Monitoring Active",
                    style = if (msg == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontWeight = if (msg == null) FontWeight.Medium else FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class MessageStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val border: BorderStroke?,
)
