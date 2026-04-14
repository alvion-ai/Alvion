package com.qualcomm.alvion.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.R

private val AuthBlue = Color(0xFF2563EB)
private val AuthCyan = Color(0xFF22D3EE)
private val AuthRose = Color(0xFFDC2626)
private val AuthGreen = Color(0xFF10B981)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(true) }

    val auth = FirebaseAuth.getInstance()
    val scrollState = rememberScrollState()

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
                    .verticalScroll(scrollState)
                    .imePadding()
                    .navigationBarsPadding(),
        ) {
            AuthHero(isLogin = isLogin)

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AuthFormCard(
                    isLogin = isLogin,
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    showPassword = showPassword,
                    errorMessage = errorMessage,
                    isError = isError,
                    onEmailChange = {
                        email = it
                        errorMessage = null
                    },
                    onPasswordChange = {
                        password = it
                        errorMessage = null
                    },
                    onConfirmPasswordChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    onTogglePassword = { showPassword = !showPassword },
                    onForgotPassword = {
                        if (email.isBlank()) {
                            errorMessage = "Enter your email to reset your password."
                            isError = true
                        } else {
                            auth
                                .sendPasswordResetEmail(email.trim())
                                .addOnSuccessListener {
                                    errorMessage = "Reset link sent to ${email.trim()}."
                                    isError = false
                                }.addOnFailureListener {
                                    errorMessage = it.message
                                    isError = true
                                }
                        }
                    },
                    onSubmit = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields."
                            isError = true
                            return@AuthFormCard
                        }
                        if (!isLogin && password != confirmPassword) {
                            errorMessage = "Passwords do not match."
                            isError = true
                            return@AuthFormCard
                        }
                        if (password.length < 6) {
                            errorMessage = "Use at least 6 characters."
                            isError = true
                            return@AuthFormCard
                        }

                        if (isLogin) {
                            auth
                                .signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener {
                                    errorMessage = it.message
                                    isError = true
                                }
                        } else {
                            auth
                                .createUserWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener {
                                    errorMessage = it.message
                                    isError = true
                                }
                        }
                    },
                )

                AuthModeSwitch(
                    isLogin = isLogin,
                    onToggle = {
                        isLogin = !isLogin
                        errorMessage = null
                        confirmPassword = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthHero(isLogin: Boolean) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(252.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AuthBlue,
                                Color(0xFF1D4ED8),
                                AuthCyan.copy(alpha = 0.86f),
                            ),
                        ),
                    ),
        )

        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .offset((-54).dp, (-52).dp)
                    .blur(48.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(150.dp)
                    .align(Alignment.BottomEnd)
                    .offset(36.dp, 34.dp)
                    .blur(42.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoSpotlight(logoSize = 76.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "ALVION",
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                    ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isLogin) "Welcome back. Drive safer today." else "Create your driver safety profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            AuthHeroPill(text = if (isLogin) "Secure sign in" else "Protected account setup")
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun AuthFormCard(
    isLogin: Boolean,
    email: String,
    password: String,
    confirmPassword: String,
    showPassword: Boolean,
    errorMessage: String?,
    isError: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AuthBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isLogin) Icons.AutoMirrored.Filled.Login else Icons.Default.PersonAdd,
                        null,
                        tint = AuthBlue,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = isLogin,
                        transitionSpec = { (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically()) },
                        label = "AuthTitle",
                    ) { login ->
                        Text(
                            if (login) "Sign In" else "Sign Up",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        if (isLogin) "Access your trips and alerts." else "Start monitoring your drives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AuthTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "you@example.com",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
            )

            AuthTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "Minimum 6 characters",
                leadingIcon = Icons.Default.Lock,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            if (isLogin) {
                TextButton(
                    onClick = onForgotPassword,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("Forgot Password?", color = AuthBlue, fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(
                visible = !isLogin,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AuthTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = "Confirm Password",
                    placeholder = "Re-enter password",
                    leadingIcon = Icons.Default.Shield,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (errorMessage != null) {
                    AuthStatusMessage(message = errorMessage, isError = isError)
                }
            }

            AuthPrimaryButton(
                label = if (isLogin) "Sign In" else "Create Account",
                icon = if (isLogin) Icons.AutoMirrored.Filled.Login else Icons.Default.PersonAdd,
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun AuthHeroPill(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.17f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuthPrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(AuthBlue, AuthCyan.copy(alpha = 0.9f))))
                    .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(leadingIcon, null, tint = AuthBlue) },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuthBlue,
                focusedLabelColor = AuthBlue,
                cursorColor = AuthBlue,
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
    )
}

@Composable
private fun AuthStatusMessage(
    message: String,
    isError: Boolean,
) {
    val accent = if (isError) AuthRose else AuthGreen

    Surface(
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AuthModeSwitch(
    isLogin: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLogin) "New to Alvion?" else "Already with Alvion?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(
                    text = if (isLogin) "Create account" else "Sign in",
                    fontWeight = FontWeight.Bold,
                    color = AuthBlue,
                )
            }
        }
    }
}

@Composable
private fun LogoSpotlight(logoSize: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.35f)
                    .blur(34.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        Image(
            painter = painterResource(id = R.drawable.alvion_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
    }
}
