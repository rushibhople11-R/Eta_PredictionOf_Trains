package org.example.project

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import etapredictionoftrains.app.shared.generated.resources.Res
import etapredictionoftrains.app.shared.generated.resources.railandaz_logo
import org.example.project.ui.LiveTrackerDashboard
import org.example.project.ui.SplashScreen
import org.example.project.ui.theme.RailAndazTheme

@Composable
fun rememberRailAndazAppIcon(): Painter = painterResource(Res.drawable.railandaz_logo)

enum class AppScreen {
    SPLASH,
    CONTROL_ROOM,
    INTELLIGENCE_COCKPIT,
    LEGACY_TRACKER
}

@Composable
fun App() {
    RailAndazTheme {
        var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            modifier = Modifier.fillMaxSize(),
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.SPLASH -> {
                    SplashScreen(
                        onSplashFinished = {
                            currentScreen = AppScreen.CONTROL_ROOM
                        }
                    )
                }
                AppScreen.CONTROL_ROOM -> {
                    org.example.project.ui.RailNexusAiControlRoomDashboard(
                        onNavigateToLegacyTracker = {
                            currentScreen = AppScreen.LEGACY_TRACKER
                        }
                    )
                }
                AppScreen.INTELLIGENCE_COCKPIT -> {
                    org.example.project.ui.RailNexusIntelligenceDashboard(
                        onOpenControlRoom = {
                            currentScreen = AppScreen.CONTROL_ROOM
                        },
                        onOpenStationHub = {
                            currentScreen = AppScreen.LEGACY_TRACKER
                        }
                    )
                }
                AppScreen.LEGACY_TRACKER -> {
                    LiveTrackerDashboard(
                        onBackToControlRoom = {
                            currentScreen = AppScreen.CONTROL_ROOM
                        }
                    )
                }
            }
        }
    }
}