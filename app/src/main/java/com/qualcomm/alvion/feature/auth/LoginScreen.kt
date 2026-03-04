package com.qualcomm.alvion.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.qualcomm.alvion.R

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(true) } // To toggle between error and success styling

    val auth = FirebaseAuth.getInstance()
    val scrollState = rememberScrollState()

    // Matching IntroScreen Colors
    val bgGradientColors =
        listOf(
            Color(0x332563EB), // blue 600 @ ~20%
            Color(0x1A22D3EE), // cyan 400 @ ~10%
            Color(0x332563EB), // blue 600 @ ~20%
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // 1. Dynamic Background Gradient
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(bgGradientColors)),
        )

        // 2. Decorative Blobs
        Blob(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset((-140).dp, (-140).dp),
            size = 380.dp,
            color = Color(0x1A3B82F6),
        )
        Blob(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset((140).dp, (140).dp),
            size = 380.dp,
            color = Color(0x1A22D3EE),
        )

        // Main Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding() // Ensures UI moves up when keyboard shows
                    .verticalScroll(scrollState)
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Add a spacer at the top to allow Arrangement.Center to work properly with scrolling
            Spacer(modifier = Modifier.height(32.dp))

            // 3. Logo Spotlight
            LogoSpotlight(logoSize = 100.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ALVION",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                style =
                    androidx.compose.ui.text.TextStyle(
                        brush =
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF06B6D4)),
                            ),
                    ),
            )
            Text(
                text = "Stay safe on every journey",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), // Reduced spacing slightly to fit Forgot Password
                ) {
                    Text(
                        text = if (isLogin) "Welcome Back" else "Create Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email") },
                        placeholder = { Text("your.email@example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    if (isLogin) {
                        TextButton(
                            onClick = {
                                if (email.isBlank()) {
                                    errorMessage = "Please enter your email to reset password"
                                    isError = true
                                } else {
                                    auth
                                        .sendPasswordResetEmail(email.trim())
                                        .addOnSuccessListener {
                                            errorMessage = "Reset link sent to $email"
                                            isError = false
                                        }.addOnFailureListener {
                                            errorMessage = it.message
                                            isError = true
                                        }
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = "Forgot Password?",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2563EB),
                            )
                        }
                    }

                    if (!isLogin) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                errorMessage = null
                            },
                            label = { Text("Confirm Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    if (errorMessage != null) {
                        val containerColor =
                            if (isError) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            } else {
                                Color(0xFFE0F2F1).copy(alpha = 0.8f) // Light teal for success
                            }
                        val contentColor =
                            if (isError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                Color(0xFF00695C)
                            }

                        Surface(
                            color = containerColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please fill in all fields"
                                isError = true
                                return@Button
                            }
                            if (!isLogin && password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                isError = true
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Minimum 6 characters required"
                                isError = true
                                return@Button
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    ) {
                        Text(if (isLogin) "Sign In" else "Get Started", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLogin) "Don't have an account?" else "Already have an account?",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    isLogin = !isLogin
                    errorMessage = null
                }) {
                    Text(
                        text = if (isLogin) "Sign Up" else "Sign In",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                    )
                }
            }

            // Bottom spacer to ensure scrolling works well with keyboard
            Spacer(modifier = Modifier.height(32.dp))
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
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.4f)
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
