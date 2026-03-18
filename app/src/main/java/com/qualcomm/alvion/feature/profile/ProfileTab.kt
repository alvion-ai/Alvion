package com.qualcomm.alvion.feature.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.core.data.SettingsRepository

@Composable
fun ProfileTab(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(SettingsRepository(LocalContext.current)),
        ),
) {
    val context = LocalContext.current
    val displayName by viewModel.displayName.collectAsState()
    val email by viewModel.email.collectAsState()
    val alertSound by viewModel.alertSoundEnabled.collectAsState()
    val vibration by viewModel.vibrationEnabled.collectAsState()
    val darkMode by viewModel.darkModeEnabled.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

    // Compare with current Firebase name to show/hide update button
    val firebaseName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "" }
    val isNameChanged = displayName.trim() != firebaseName.trim()

    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is SettingsViewModel.UpdateStatus.Success -> {
                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateStatus()
            }
            is SettingsViewModel.UpdateStatus.Error -> {
                Toast.makeText(context, (updateStatus as SettingsViewModel.UpdateStatus.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateStatus()
            }
            else -> {}
        }
    }

    // Use theme colors directly to respond to the custom dark mode toggle
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "Profile Settings",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                modifier = Modifier.padding(top = 16.dp),
            )

            // Account Identity Card
            GlassCard(title = "Account Identity", titleColor = Color(0xFF2563EB), backgroundColor = cardBackground) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { viewModel.updateDisplayName(it) },
                        label = { Text("Profile Name", color = subTextColor) },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            ),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2563EB)) },
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        label = { Text("Email (Read-Only)", color = subTextColor.copy(alpha = 0.7f)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                disabledTextColor = textColor.copy(alpha = 0.6f),
                                disabledBorderColor = textColor.copy(alpha = 0.1f),
                                disabledLeadingIconColor = textColor.copy(alpha = 0.4f),
                            ),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    )

                    AnimatedVisibility(
                        visible = isNameChanged,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Button(
                            onClick = { viewModel.saveProfileName() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = updateStatus !is SettingsViewModel.UpdateStatus.Loading,
                        ) {
                            if (updateStatus is SettingsViewModel.UpdateStatus.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Update Profile", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Alert Preferences Card
            GlassCard(title = "Alert Preferences", titleColor = Color(0xFF06B6D4), backgroundColor = cardBackground) {
                Column {
                    SettingToggle(
                        title = "Alert Sound",
                        subtitle = "Play audio during detections",
                        checked = alertSound,
                        onCheckedChange = { viewModel.toggleAlertSound(it) },
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        textColor = textColor,
                        subTextColor = subTextColor,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = textColor.copy(alpha = 0.1f))
                    SettingToggle(
                        title = "Vibration",
                        subtitle = "Haptic feedback on events",
                        checked = vibration,
                        onCheckedChange = { viewModel.toggleVibration(it) },
                        icon = Icons.Default.Vibration,
                        textColor = textColor,
                        subTextColor = subTextColor,
                    )
                }
            }

            // Appearance Card
            GlassCard(title = "Appearance", titleColor = Color(0xFF8B5CF6), backgroundColor = cardBackground) {
                SettingToggle(
                    title = "Dark Mode",
                    subtitle = "Switch app theme",
                    checked = darkMode,
                    onCheckedChange = { viewModel.toggleDarkMode(it) },
                    icon = Icons.Default.DarkMode,
                    textColor = textColor,
                    subTextColor = subTextColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign Out", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GlassCard(
    title: String,
    titleColor: Color,
    backgroundColor: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(titleColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        color = titleColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
            )
        }
        content()
    }
}

@Composable
fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    textColor: Color,
    subTextColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.Medium)
            Text(subtitle, color = subTextColor, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2563EB),
                    checkedTrackColor = Color(0xFF2563EB).copy(alpha = 0.3f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f),
                    checkedBorderColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
