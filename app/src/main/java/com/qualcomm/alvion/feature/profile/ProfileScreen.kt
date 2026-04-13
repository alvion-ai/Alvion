package com.qualcomm.alvion.feature.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.core.data.SettingsRepository

// ─────────────────────────────────────────────────────────────
//  Design tokens (Uniform with History/Home)
// ─────────────────────────────────────────────────────────────
private val PrimaryBlue = Color(0xFF2563EB)
private val SecondaryCyan = Color(0xFF06B6D4)
private val DangerRed = Color(0xFFEF4444)

// ─────────────────────────────────────────────────────────────
//  Root Screen
// ─────────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(SettingsRepository(LocalContext.current)),
        ),
) {
    val context = LocalContext.current

    val displayName by viewModel.displayName.collectAsState()
    val alertSound by viewModel.alertSoundEnabled.collectAsState()
    val vibration by viewModel.vibrationEnabled.collectAsState()
    val darkMode by viewModel.darkModeEnabled.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

    val firebaseName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "" }
    val isNameChanged = displayName.trim() != firebaseName.trim()

    var showSignOutDialog by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }

    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is SettingsViewModel.UpdateStatus.Success -> {
                Toast.makeText(context, "Profile updated ✓", Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateStatus()
                isEditingName = false
            }
            is SettingsViewModel.UpdateStatus.Error -> {
                Toast.makeText(context, (updateStatus as SettingsViewModel.UpdateStatus.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateStatus()
            }
            else -> {}
        }
    }

    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // --- MODERN MINIMALIST HEADER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    "PREFERENCES",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    "Settings",
                    style =
                        TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Account Section ─────────────────────────────────────
            ProfileSectionCard(
                title = "ACCOUNT",
                accent = PrimaryBlue,
                iconVec = Icons.Rounded.Person,
            ) {
                AnimatedContent(
                    targetState = isEditingName,
                    transitionSpec = {
                        (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                    },
                    label = "EditNameAnimation",
                ) { editing ->
                    if (editing) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { viewModel.updateDisplayName(it) },
                                label = { Text("Display Name") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryBlue,
                                        focusedLabelColor = PrimaryBlue,
                                    ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.updateDisplayName(firebaseName)
                                        isEditingName = false
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { viewModel.saveProfileName() },
                                    enabled = isNameChanged && updateStatus !is SettingsViewModel.UpdateStatus.Loading,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                ) {
                                    if (updateStatus is SettingsViewModel.UpdateStatus.Loading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Save Changes")
                                    }
                                }
                            }
                        }
                    } else {
                        SettingsRow(
                            icon = Icons.Rounded.Badge,
                            title = "Display Name",
                            subtitle = displayName.ifEmpty { "Not set" },
                            accent = PrimaryBlue,
                            onClick = { isEditingName = true },
                            trailing = {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp), tint = PrimaryBlue.copy(0.6f))
                            },
                        )
                    }
                }
            }

            // ── System Preferences ───────────────────────────
            ProfileSectionCard(
                title = "ALERTS & SYSTEM",
                accent = SecondaryCyan,
                iconVec = Icons.Rounded.Tune,
            ) {
                ToggleRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "Alert Audio",
                    subtitle = "Sound feedback on detection",
                    checked = alertSound,
                    accent = SecondaryCyan,
                    onChecked = { viewModel.toggleAlertSound(it) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ToggleRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Haptic Alerts",
                    subtitle = "Vibrate device on warnings",
                    checked = vibration,
                    accent = SecondaryCyan,
                    onChecked = { viewModel.toggleVibration(it) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ToggleRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Toggle application theme",
                    checked = darkMode,
                    accent = SecondaryCyan,
                    onChecked = { viewModel.toggleDarkMode(it) },
                )
            }

            // ── Actions ──────────────────────────────────────
            ProfileSectionCard(
                title = "DANGER ZONE",
                accent = DangerRed,
                iconVec = Icons.Rounded.GppMaybe,
            ) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "Sign Out",
                    subtitle = "Log out of your Alvion account",
                    accent = DangerRed,
                    onClick = { showSignOutDialog = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Version 1.0.1",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            )
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    accent: Color,
    iconVec: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        ) {
            Icon(iconVec, null, modifier = Modifier.size(14.dp), tint = accent)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 1.sp,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = accent)
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = accent)
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = DangerRed) },
        title = { Text("Sign Out", fontWeight = FontWeight.ExtraBold) },
        text = {
            Text(
                "Are you sure you want to log out? Your driving safety data will remain saved, but you'll need to sign back in to access it.",
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Sign Out", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
