package com.qualcomm.alvion.feature.intro

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm.alvion.R

data class IntroSlide(
    val type: String,
    val title: String,
    val subtitle: String = "",
    val description: String,
    val icon: ImageVector? = null,
    // Matches React: slide.bgGradient
    val bgGradientColors: List<Color> =
        listOf(
            Color(0x332563EB), // blue 600 @ ~20%
            Color(0x1A22D3EE), // cyan 400 @ ~10%
            Color(0x332563EB), // blue 600 @ ~20%
        ),
    // Matches React: slide.accentColor (ring + dots)
    val accentGradient: List<Color> = listOf(Color(0xFF60A5FA), Color(0xFF22D3EE)),
    // Matches React: slide.color (icon tint)
    val iconTint: Color = Color(0xFF2563EB),
    // Matches React: slide.bgColor (icon container bg)
    val iconContainerBg: Color = Color(0x1A3B82F6), // ~blue/10
)

@Composable
fun IntroScreen(onComplete: () -> Unit) {
    val slides =
        listOf(
            IntroSlide(
                type = "hero",
                title = "Welcome to ALVION",
                subtitle = "Vision Technology",
                description = "Your AI for safer driving with real-time drowsiness detection",
                bgGradientColors =
                    listOf(
                        Color(0x332563EB), // blue-500/20
                        Color(0x1A22D3EE), // cyan-400/10
                        Color(0x332563EB), // blue-600/20
                    ),
                accentGradient = listOf(Color(0xFF2563EB), Color(0xFF06B6D4)),
            ),
            IntroSlide(
                type = "feature",
                icon = Icons.Default.CameraAlt,
                title = "Real-Time Monitoring",
                description = "Advanced AI continuously monitors your alertness using your device camera",
                iconTint = Color(0xFF2563EB),
                iconContainerBg = Color(0x1A3B82F6),
                accentGradient = listOf(Color(0xFF60A5FA), Color(0xFF22D3EE)),
                bgGradientColors =
                    listOf(
                        Color(0x332563EB), // blue-500/20
                        Color(0x1A22D3EE), // cyan-400/10
                        Color(0x332563EB), // blue-600/20
                    ),
            ),
            IntroSlide(
                type = "feature",
                icon = Icons.Default.NotificationsActive,
                title = "Instant Safety Alerts",
                description = "Get immediate notifications when signs of drowsiness or fatigue are detected",
                iconTint = Color(0xFF1D4ED8), // blue-700-ish
                iconContainerBg = Color(0x1A2563EB),
                accentGradient = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)),
                bgGradientColors =
                    listOf(
                        Color(0x332563EB), // blue-500/20
                        Color(0x1A22D3EE), // cyan-400/10
                        Color(0x332563EB), // blue-600/20
                    ),
            ),
            IntroSlide(
                type = "feature",
                icon = Icons.Default.TrendingUp,
                title = "Track Your Journey",
                description = "Monitor trips, view safety statistics, and improve your driving habits over time",
                iconTint = Color(0xFF0891B2),
                iconContainerBg = Color(0x1A0891B2),
                accentGradient = listOf(Color(0xFF22D3EE), Color(0xFF2563EB)),
                bgGradientColors =
                    listOf(
                        Color(0x332563EB), // blue-500/20
                        Color(0x1A22D3EE), // cyan-400/10
                        Color(0x332563EB), // blue-600/20
                    ),
            ),
        )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    val currentSlide = slides[pagerState.currentPage]

    // Background fade like AnimatePresence
    val bgAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "bgAlpha",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // Dynamic background gradient per slide
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = bgAlpha)
                    .background(
                        Brush.linearGradient(currentSlide.bgGradientColors),
                    ),
        )

        // Decorative blobs (same as React)
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
        Blob(
            modifier = Modifier.align(Alignment.Center),
            size = 260.dp,
            color = Color(0x0D60A5FA),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    // small logo
                    Image(
                        painter = painterResource(id = R.drawable.alvion_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }

                if (pagerState.currentPage < slides.size - 1) {
                    TextButton(onClick = onComplete) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                SlideContentReactMatch(slide = slides[page])
            }

            // Bottom nav
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Progress indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(slides.size) { index ->
                        val selected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (selected) 32.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "indicatorWidth",
                        )
                        val opacity by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.3f,
                            animationSpec = tween(300),
                            label = "indicatorOpacity",
                        )

                        Box(
                            modifier =
                                Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(
                                        brush =
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF2563EB), Color(0xFF06B6D4)),
                                            ),
                                        alpha = opacity,
                                    ),
                        )
                    }
                }

                // Only show Get Started button on the last slide
                AnimatedVisibility(
                    visible = pagerState.currentPage == slides.size - 1,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Button(
                        onClick = onComplete,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White,
                            ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    ) {
                        Text(
                            "Get Started",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
fun SlideContentReactMatch(slide: IntroSlide) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(slide.title) {
        enter.snapTo(0f)
        enter.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
    }

    val contentAlpha = enter.value
    val contentOffsetY = (1f - enter.value) * 20f

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .graphicsLayer(alpha = contentAlpha, translationY = contentOffsetY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (slide.type == "hero") {
            HeroSlideReactMatch(slide)
        } else {
            FeatureSlideReactMatch(slide)
        }
    }
}

@Composable
private fun HeroSlideReactMatch(slide: IntroSlide) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Big logo
        val scale = remember { Animatable(0.8f) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scale.animateTo(1f, tween(500, delayMillis = 200, easing = FastOutSlowInEasing))
            alpha.animateTo(1f, tween(500, delayMillis = 200))
        }

        Box(
            modifier =
                Modifier.graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    alpha = alpha.value,
                ),
            contentAlignment = Alignment.Center,
        ) {
            LogoSpotlight(logoSize = 150.dp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ALVION",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black, // Extra bold
                fontFamily = FontFamily.SansSerif,
                style =
                    androidx.compose.ui.text.TextStyle(
                        brush =
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF06B6D4)),
                            ),
                        // Adding shadow for appeal
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.45f),
                                offset = Offset(10f, 4f),
                                blurRadius = 8f,
                            ),
                    ),
            )
            Text(
                text = slide.subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2563EB).copy(alpha = 0.8f),
                letterSpacing = 2.sp,
            )
        }

        Text(
            text = slide.description,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 28.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Floating icons bounce like React
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val icons = listOf(Icons.Default.CameraAlt, Icons.Default.RemoveRedEye, Icons.Default.Shield)
            icons.forEachIndexed { i, icon ->
                FloatingIconCard(
                    icon = icon,
                    delayMs = i * 300,
                )
            }
        }
    }
}

@Composable
private fun FloatingIconCard(
    icon: ImageVector,
    delayMs: Int,
) {
    val infinite = rememberInfiniteTransition(label = "float")

    val offset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )

    // phase-shift each icon
    val phase = (offset + (delayMs / 1000f)) % 1f

    // Smooth wave: 0..1 -> y (use cos for nice up/down)
    val y = (-14f * kotlin.math.cos(phase * 2f * Math.PI.toFloat())).toFloat()

    Box(
        modifier =
            Modifier
                .graphicsLayer(translationY = y)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0x1A3B82F6), Color(0x1A22D3EE))),
                ).padding(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun FeatureSlideReactMatch(slide: IntroSlide) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Rotating ring (20s linear)
        val rotation =
            rememberInfiniteTransition(label = "ring")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(20000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "rotation",
                ).value

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .graphicsLayer(rotationZ = rotation)
                        .blur(12.dp)
                        .background(
                            brush = Brush.linearGradient(slide.accentGradient),
                            alpha = 0.2f,
                            shape = CircleShape,
                        ),
            )

            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(slide.iconContainerBg)
                        .padding(40.dp),
            ) {
                Icon(
                    imageVector = slide.icon ?: Icons.Default.Star,
                    contentDescription = null,
                    tint = slide.iconTint,
                    modifier = Modifier.size(80.dp),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = slide.title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = slide.description,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 28.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun LogoSpotlight(
    modifier: Modifier = Modifier,
    logoSize: Dp = 150.dp,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 1) Big soft glow that blends into the background
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.2f)
                    .blur(55.dp)
                    .background(Color(0xFF2563EB).copy(alpha = 0.22f), CircleShape),
        )

        // 2) Slight secondary cyan glow for depth (optional but looks nice)
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.6f)
                    .blur(45.dp)
                    .background(Color(0xFF06B6D4).copy(alpha = 0.14f), CircleShape),
        )

        // 3) The actual circle "badge" behind the logo
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.15f)
                    .clip(CircleShape)
                    .background(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFF2563EB).copy(alpha = 0.25f),
                                        Color(0xFF2563EB).copy(alpha = 0.12f),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )

        // 4) Put the logo on top
        Image(
            painter = painterResource(id = R.drawable.alvion_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
    }
}
