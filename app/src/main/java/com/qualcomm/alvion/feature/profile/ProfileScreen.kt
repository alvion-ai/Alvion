package com.qualcomm.alvion.feature.profile

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.core.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────
private val Blue600 = Color(0xFF2563EB)
private val Cyan400 = Color(0xFF22D3EE)
private val Indigo500 = Color(0xFF6366F1)
private val Rose500 = Color(0xFFEF4444)

private val heroGradient = listOf(Blue600, Cyan400.copy(alpha = 0.8f))

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
    val email by viewModel.email.collectAsState()
    val alertSound by viewModel.alertSoundEnabled.collectAsState()
    val vibration by viewModel.vibrationEnabled.collectAsState()
    val darkMode by viewModel.darkModeEnabled.collectAsState()
    val profileImageUri by viewModel.profileImageUri.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

    val firebaseName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "" }
    val isNameChanged = displayName.trim() != firebaseName.trim()

    var showSignOutDialog by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }

    val profileImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some providers grant temporary read access only; still store the URI for this session.
                }
                viewModel.updateProfileImageUri(uri.toString())
            }
        }

    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is SettingsViewModel.UpdateStatus.Success -> {
                Toast.makeText(context, "Profile updated ✓", Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateStatus()
                isEditingName = false
            }
            is SettingsViewModel.UpdateStatus.Error -> {
                Toast
                    .makeText(
                        context,
                        (updateStatus as SettingsViewModel.UpdateStatus.Error).message,
                        Toast.LENGTH_SHORT,
                    ).show()
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            // ── Hero Header ──────────────────────────────────────────
            HeroHeader(
                displayName = displayName,
                email = email,
                profileImageUri = profileImageUri,
                onSelectProfileImage = { profileImageLauncher.launch(arrayOf("image/*")) },
            )

            // ── Body ─────────────────────────────────────────────────
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // ── Account Card ─────────────────────────────────────
                ProfileSectionCard(
                    title = "Account",
                    accent = Blue600,
                    iconVec = Icons.Default.Person,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimatedContent(
                            targetState = isEditingName,
                            transitionSpec = {
                                (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                            },
                            label = "EditNameAnimation",
                        ) { editing ->
                            if (editing) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StyledTextField(
                                        value = displayName,
                                        onValueChange = { viewModel.updateDisplayName(it) },
                                        label = "Display Name",
                                        leadingIcon = Icons.Default.Person,
                                        accentColor = Blue600,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateDisplayName(firebaseName)
                                                isEditingName = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Text("Cancel")
                                        }
                                        Button(
                                            onClick = { viewModel.saveProfileName() },
                                            enabled = isNameChanged && updateStatus !is SettingsViewModel.UpdateStatus.Loading,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                        ) {
                                            if (updateStatus is SettingsViewModel.UpdateStatus.Loading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            } else {
                                EditableInfoRow(
                                    label = "Display Name",
                                    value = displayName.ifEmpty { "Not set" },
                                    icon = Icons.Default.Person,
                                    accent = Blue600,
                                    onEdit = { isEditingName = true },
                                )
                            }
                        }

                        StyledTextField(
                            value = email,
                            onValueChange = {},
                            label = "Email (read-only)",
                            leadingIcon = Icons.Default.Email,
                            accentColor = Blue600,
                            enabled = false,
                        )
                    }
                }

                // ── Alert Preferences Card ───────────────────────────
                ProfileSectionCard(
                    title = "Alert Preferences",
                    accent = Cyan400,
                    iconVec = Icons.Default.NotificationsActive,
                ) {
                    Column {
                        ToggleRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = "Alert Sound",
                            subtitle = "Audio alert on detection",
                            checked = alertSound,
                            accent = Cyan400,
                            onChecked = { viewModel.toggleAlertSound(it) },
                        )
                        SectionDivider()
                        ToggleRow(
                            icon = Icons.Default.Vibration,
                            title = "Vibration",
                            subtitle = "Haptic feedback on events",
                            checked = vibration,
                            accent = Cyan400,
                            onChecked = { viewModel.toggleVibration(it) },
                        )
                    }
                }

                // ── Appearance Card ──────────────────────────────────
                ProfileSectionCard(
                    title = "Appearance",
                    accent = Indigo500,
                    iconVec = Icons.Default.Palette,
                ) {
                    ToggleRow(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Switch app theme",
                        checked = darkMode,
                        accent = Indigo500,
                        onChecked = { viewModel.toggleDarkMode(it) },
                    )
                }

                // ── Danger Zone ──────────────────────────────────────
                ProfileSectionCard(
                    title = "Account Actions",
                    accent = Rose500,
                    iconVec = Icons.Default.Shield,
                ) {
                    ActionRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Sign Out",
                        color = Rose500,
                        onClick = { showSignOutDialog = true },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Hero Header
// ─────────────────────────────────────────────────────────────
@Composable
private fun HeroHeader(
    displayName: String,
    email: String,
    profileImageUri: String,
    onSelectProfileImage: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp),
    ) {
        // Gradient background
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(heroGradient)),
        )

        // Decorative blobs
        Box(
            modifier =
                Modifier
                    .size(200.dp)
                    .offset((-60).dp, (-60).dp)
                    .blur(60.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .align(Alignment.BottomEnd)
                    .offset(50.dp, 50.dp)
                    .blur(50.dp)
                    .background(Cyan400.copy(alpha = 0.25f), CircleShape),
        )

        // Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ProfileAvatar(
                profileImageUri = profileImageUri,
                onSelectProfileImage = onSelectProfileImage,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = displayName.ifEmpty { "Driver" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        // Bottom wave cut
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ).background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun ProfileAvatar(
    profileImageUri: String,
    onSelectProfileImage: () -> Unit,
) {
    val imageBitmap by rememberProfileImage(profileImageUri)

    Box(
        modifier =
            Modifier
                .size(88.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onSelectProfileImage),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "Profile picture",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.White.copy(0.55f), CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE5E7EB), CircleShape)
                        .border(2.dp, Color.White.copy(0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Default profile picture",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .background(Blue600, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Change profile picture",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun rememberProfileImage(profileImageUri: String): State<ImageBitmap?> {
    val context = LocalContext.current

    return produceState<ImageBitmap?>(initialValue = null, profileImageUri) {
        value = null
        if (profileImageUri.isBlank()) return@produceState

        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(profileImageUri))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
}

// ─────────────────────────────────────────────────────────────
//  Editable Info Row
// ─────────────────────────────────────────────────────────────
@Composable
private fun EditableInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEdit() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit $label",
                tint = accent.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Section Card
// ─────────────────────────────────────────────────────────────
@Composable
private fun ProfileSectionCard(
    title: String,
    accent: Color,
    iconVec: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Section header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconVec,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = accent,
                    letterSpacing = 0.3.sp,
                )
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Styled TextField
// ─────────────────────────────────────────────────────────────
@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    accentColor: Color,
    enabled: Boolean = true,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(leadingIcon, null, tint = if (enabled) accentColor else subTextColor.copy(0.4f))
        },
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                disabledTextColor = textColor.copy(0.5f),
                disabledBorderColor = textColor.copy(0.1f),
                disabledLeadingIconColor = subTextColor.copy(0.3f),
                focusedLabelColor = accentColor,
                unfocusedLabelColor = subTextColor,
            ),
    )
}

// ─────────────────────────────────────────────────────────────
//  Toggle Row
// ─────────────────────────────────────────────────────────────
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (checked) {
                            accent.copy(0.12f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(0.06f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = accent,
                    checkedTrackColor = accent.copy(alpha = 0.25f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.Gray.copy(0.2f),
                    checkedBorderColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Action Row  (sign-out etc.)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = color.copy(0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Divider
// ─────────────────────────────────────────────────────────────
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        thickness = 1.dp,
    )
}

// ─────────────────────────────────────────────────────────────
//  Sign-Out Confirmation Dialog
// ─────────────────────────────────────────────────────────────
@Composable
private fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Rose500, modifier = Modifier.size(28.dp))
        },
        title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "You'll need to sign in again to access your driving history and settings.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Sign Out", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────
//  ViewModel Factory
// ─────────────────────────────────────────────────────────────
class SettingsViewModelFactory(
    private val repository: com.qualcomm.alvion.core.data.SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
