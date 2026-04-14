package com.qualcomm.alvion.feature.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qualcomm.alvion.R
import kotlinx.coroutines.launch

private val IntroBlue = Color(0xFF2563EB)
private val IntroBlueDeep = Color(0xFF1D4ED8)
private val IntroCyan = Color(0xFF22D3EE)
private val IntroGreen = Color(0xFF10B981)

data class IntroSlide(
    val eyebrow: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color = IntroBlue,
)

@Composable
fun IntroScreen(onComplete: () -> Unit) {
    val slides =
        listOf(
            IntroSlide(
                eyebrow = "Vision Technology",
                title = "Welcome to ALVION",
                description = "Your companion for calmer, safer driving with real-time driver monitoring.",
                icon = Icons.Default.Shield,
                accent = IntroBlue,
            ),
            IntroSlide(
                eyebrow = "Live Awareness",
                title = "Real-Time Monitoring",
                description = "Use your camera to detect signs of drowsiness and distraction while you drive.",
                icon = Icons.Default.CameraAlt,
                accent = IntroBlue,
            ),
            IntroSlide(
                eyebrow = "Fast Feedback",
                title = "Instant Safety Alerts",
                description = "Get clear audio, vibration, and on-screen prompts when your attention needs support.",
                icon = Icons.Default.NotificationsActive,
                accent = IntroBlueDeep,
            ),
            IntroSlide(
                eyebrow = "Drive Insights",
                title = "Track Your Journey",
                description = "Review trips, understand alerts, and build safer habits over time.",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accent = Color(0xFF0891B2),
            ),
        )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                IntroBlue.copy(alpha = 0.12f),
                                IntroCyan.copy(alpha = 0.06f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            IntroTopBar(
                currentPage = pagerState.currentPage,
                totalPages = slides.size,
                onSkip = onComplete,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                PremiumIntroSlide(slide = slides[page], page = page)
            }

            IntroBottomBar(
                currentPage = pagerState.currentPage,
                totalPages = slides.size,
                isLastPage = isLastPage,
                onPrimaryClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun IntroTopBar(
    currentPage: Int,
    totalPages: Int,
    onSkip: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.alvion_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("ALVION", fontWeight = FontWeight.Black, color = IntroBlue, fontSize = 14.sp)
        }

        AnimatedVisibility(visible = currentPage < totalPages - 1) {
            Surface(
                onClick = onSkip,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Text(
                    "Skip",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PremiumIntroSlide(
    slide: IntroSlide,
    page: Int,
) {
    val enter = remember(page) { Animatable(0f) }
    LaunchedEffect(page) {
        enter.snapTo(0f)
        enter.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .graphicsLayer(
                    alpha = enter.value,
                    translationY = (1f - enter.value) * 24f,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IntroVisualCard(slide = slide, isHero = page == 0)

        Spacer(Modifier.height(26.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IntroPill(text = slide.eyebrow, accent = slide.accent)
                Spacer(Modifier.height(14.dp))
                Text(
                    slide.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun IntroVisualCard(
    slide: IntroSlide,
    isHero: Boolean,
) {
    val infinite = rememberInfiniteTransition(label = "introVisual")
    val lift by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "introVisualLift",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(248.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(218.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            shadowElevation = 10.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    IntroBlue,
                                    IntroBlueDeep,
                                    IntroCyan.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(160.dp)
                            .offset((-34).dp, (-40).dp)
                            .blur(42.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(140.dp)
                            .offset(34.dp, 34.dp)
                            .blur(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape),
                )
            }
        }

        if (isHero) {
            LogoSpotlight(
                logoSize = 128.dp,
                modifier = Modifier.graphicsLayer(translationY = lift),
            )
        } else {
            Surface(
                modifier =
                    Modifier
                        .size(132.dp)
                        .graphicsLayer(translationY = lift),
                shape = RoundedCornerShape(30.dp),
                color = Color.White.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = slide.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(68.dp),
                    )
                }
            }
        }

        if (!isHero) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = IntroGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Built for safer trips",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPill(
    text: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Security, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IntroBottomBar(
    currentPage: Int,
    totalPages: Int,
    isLastPage: Boolean,
    onPrimaryClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(totalPages) { index ->
                val selected = currentPage == index
                val width by animateDpAsState(
                    targetValue = if (selected) 30.dp else 8.dp,
                    animationSpec = tween(260),
                    label = "introIndicatorWidth",
                )
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    Brush.horizontalGradient(listOf(IntroBlue, IntroCyan))
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                        ),
                                    )
                                },
                            ),
                )
            }
        }

        Surface(
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(IntroBlue, IntroCyan.copy(alpha = 0.9f))))
                        .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isLastPage) "Get Started" else "Next",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LogoSpotlight(
    modifier: Modifier = Modifier,
    logoSize: Dp = 128.dp,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.35f)
                    .blur(42.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(logoSize * 1.1f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
        )
        Image(
            painter = painterResource(id = R.drawable.alvion_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize),
        )
    }
}
