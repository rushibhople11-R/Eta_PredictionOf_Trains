package org.example.project.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.example.project.ui.theme.*
import etapredictionoftrains.app.shared.generated.resources.Res
import etapredictionoftrains.app.shared.generated.resources.railandaz_logo

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Rotating status text
    val statusMessages = remember {
        listOf(
            "Calibrating timetable section slacks...",
            "Analyzing junction congestion & bottlenecks...",
            "Computing dynamic cascade ETAs...",
            "Ready!"
        )
    }
    var currentStatusIndex by remember { mutableStateOf(0) }

    // Logo entrance animation
    val transition = rememberInfiniteTransition()
    val pulseGlow by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val trackDotOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(Unit) {
        // Cycle status messages
        for (i in statusMessages.indices) {
            currentStatusIndex = i
            delay(550)
        }
        delay(400)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14),
                        Color(0xFF0E1626),
                        Color(0xFF070B14)
                    )
                )
            )
            .clickable { onSplashFinished() }, // Tap to skip
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Glowing halo wrapper around logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Animated neon halo ring
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RailCyan.copy(alpha = 0.35f * pulseGlow),
                                    RailAmber.copy(alpha = 0.15f * pulseGlow),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // RailAndaz Logo Image
                Image(
                    painter = painterResource(Res.drawable.railandaz_logo),
                    contentDescription = "RailAndaz Logo",
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(RailCyan, RailAmber)
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Title
            Text(
                text = "RAILअंदाज",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RailTextPrimary,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Rail + अंदाज • AI Train Intelligence",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RailGreen,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Control Room • Nashik Railway Section",
                fontSize = 12.sp,
                color = RailTextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated rail-track progress indicator
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(RailCardBorder)
            ) {
                // Moving train dot on track
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .offset(x = (172.dp * trackDotOffset))
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(RailCyan, RailAmber)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic reassuring status ticker
            Text(
                text = statusMessages.getOrElse(currentStatusIndex) { "" },
                fontSize = 12.sp,
                color = RailMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap anywhere to skip",
                fontSize = 10.sp,
                color = Color(0xFF475569)
            )
        }
    }
}
