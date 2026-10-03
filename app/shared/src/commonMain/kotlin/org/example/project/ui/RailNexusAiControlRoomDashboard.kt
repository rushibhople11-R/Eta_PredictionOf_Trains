package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import org.jetbrains.compose.resources.painterResource
import etapredictionoftrains.app.shared.generated.resources.Res
import etapredictionoftrains.app.shared.generated.resources.railandaz_logo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.data.NashikStationData
import org.example.project.data.RealTrainProfile
import org.example.project.data.RealTrainRecordRegistry
import org.example.project.engine.*
import org.example.project.model.*
import org.example.project.ui.theme.*

private val HeaderNavy = Color(0xFF0B192C)
private val ScreenBg = Color(0xFFF8FAFC)
private val NoticeBg = Color(0xFFFEF3C7)
private val NoticeBorder = Color(0xFFFDE68A)
private val NoticeTitle = Color(0xFF78350F)
private val NoticeText = Color(0xFF92400E)

private val ScenarioCardBg = Color(0xFF162E50)
private val ScenarioBlueTag = Color(0xFF93C5FD)
private val ScenarioBtnGreen = Color(0xFF10B981)

private val KpiCardBg = Color(0xFFFFFFFF)
private val KpiCardBorder = Color(0xFFE2E8F0)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

enum class ControlRoomTab(val title: String, val icon: String) {
    HOME("Home", "🏠"),
    NETWORK("Live Map", "🗺️"),
    ALERTS("Alerts", "🔔"),
    DYNAMIC_ETA("Dynamic ETA", "⏱️"),
    THROUGHPUT("Throughput", "📊"),
    REPORTS("Reports", "📑")
}

/**
 * RailNexus AI Control Room Dashboard
 * Pixel-perfect implementation matching the exact SIH Control Room layout,
 * featuring the 6-pillar decision support system (DSS) control flow.
 */
@Composable
fun RailNexusAiControlRoomDashboard(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Current active primary navigation tab
    var activeTab by remember { mutableStateOf(ControlRoomTab.HOME) }
    var selectedTrainFilter by remember { mutableStateOf("ALL") }
    var alertFilter by remember { mutableStateOf("ALL") }

    // Human-in-the-Loop decision state (Pending, Approved, Rejected)
    var decisionState by remember { mutableStateOf(ControllerDecisionType.PENDING) }
    var holdMinutesValue by remember { mutableStateOf(7) }
    var showDecisionWorkflow by remember { mutableStateOf(false) }

    val alerts = remember { AiDecisionAndSafetyEngine.getDefaultAlerts() }
    val throughputList = remember { AiDecisionAndSafetyEngine.getCorridorThroughput() }
    val reportsList = remember { AiDecisionAndSafetyEngine.getPerformanceReports() }
    val allTrains = remember { NashikStationData.allNashikTrains }

    // Dynamic interactive states: Real Indian Standard Time ticking live
    var currentIstTime by remember {
        mutableStateOf(IndianStandardTime.fromEpochMillis(org.example.project.getCurrentEpochMillis()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentIstTime = IndianStandardTime.fromEpochMillis(org.example.project.getCurrentEpochMillis())
        }
    }
    val liveTimeString = currentIstTime.compactBadge

    val realActiveTrains = remember(currentIstTime) { RealTrainRecordRegistry.getActiveRealTrains(currentIstTime) }
    val activeTrainsCount = realActiveTrains.size
    val delayedTrainsCount = realActiveTrains.count { it.getScheduleStatus(currentIstTime).delayMinutes > 0 }
    val activeAlertsCount = alerts.count { it.category == AlertCategory.ACTIVE } - (if (decisionState == ControllerDecisionType.APPROVE) 1 else 0)

    val infiniteTransition = rememberInfiniteTransition()
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Recalibration interactive state
    var recalibrationTick by remember { mutableStateOf(0) }
    var showPipelineAudit by remember { mutableStateOf(false) }

    // Corridor search & filter interactive state
    var corridorSearchText by remember { mutableStateOf("") }
    var corridorFilter by remember { mutableStateOf("ACTIVE") }
    var expandedTrainNumber by remember { mutableStateOf<String?>(null) }
    var inspectingTrainNumber by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        val isWideScreen = maxWidth >= 960.dp
        val isTablet = maxWidth in 640.dp..959.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // --- 1. RESPONSIVE TOP NAVY HEADER ---
            Surface(
                color = HeaderNavy,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 1440.dp)
                        .padding(horizontal = if (isWideScreen) 24.dp else 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Logo + Title + Section label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.railandaz_logo),
                                contentDescription = "RAILअंदाज Logo",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                            )

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "RAILअंदाज",
                                        color = Color.White,
                                        fontSize = if (isWideScreen) 20.sp else 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFD97706))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "DEMO DATA",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Control Room • Nashik Railway Section",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }



                        // Right: Live Status Pill + Bell + Profile
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isWideScreen || isTablet) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF0F2B48),
                                    border = BorderStroke(1.dp, Color(0xFF1E3A8A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = livePulseAlpha))
                                        )
                                        Text(
                                            text = "LIVE TELEMETRY",
                                            color = Color(0xFF93C5FD),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = liveTimeString,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Bell button with alert badge
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .clickable { activeTab = ControlRoomTab.ALERTS },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🔔", fontSize = 16.sp)
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }

                            // Profile avatar button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "👤", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // --- 2. MAIN BODY CONTENT (TAB SWITCHER) ---
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    ControlRoomTab.HOME -> {
                        HomeScreenContent(
                            isWideScreen = isWideScreen,
                            currentIst = currentIstTime,
                            activeTrainsCount = activeTrainsCount,
                            delayedTrainsCount = delayedTrainsCount,
                            activeAlertsCount = activeAlertsCount,
                            decisionState = decisionState,
                            onLaunchWorkflow = { showDecisionWorkflow = true },
                            onResetSimulation = {
                                decisionState = ControllerDecisionType.PENDING
                                holdMinutesValue = 7
                            },
                            recalibrationTick = recalibrationTick,
                            onRecalibrationTick = {
                                recalibrationTick = (recalibrationTick + 1) % 3
                            },
                            showPipelineAudit = showPipelineAudit,
                            onTogglePipelineAudit = {
                                showPipelineAudit = !showPipelineAudit
                            },
                            corridorSearchText = corridorSearchText,
                            onCorridorSearchChange = { corridorSearchText = it },
                            corridorFilter = corridorFilter,
                            onCorridorFilterChange = { corridorFilter = it },
                            expandedTrainNumber = expandedTrainNumber,
                            onToggleExpandTrain = { trainNo ->
                                expandedTrainNumber = if (expandedTrainNumber == trainNo) null else trainNo
                            },
                            onSelectActiveTrains = {
                                selectedTrainFilter = "ACTIVE"
                                inspectingTrainNumber = null
                                activeTab = ControlRoomTab.DYNAMIC_ETA
                            },
                            onSelectDelayedTrains = {
                                selectedTrainFilter = "DELAYED"
                                inspectingTrainNumber = null
                                activeTab = ControlRoomTab.DYNAMIC_ETA
                            },
                            onSelectAlerts = { activeTab = ControlRoomTab.ALERTS },
                            onSelectNetwork = { activeTab = ControlRoomTab.NETWORK }
                        )
                    }

                    ControlRoomTab.NETWORK -> {
                        NashikCityGoogleMapScreen(
                            onBackToHome = { activeTab = ControlRoomTab.HOME }
                        )
                    }

                    ControlRoomTab.ALERTS -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1440.dp)) {
                                AlertsTabContent(
                                    alerts = alerts,
                                    currentFilter = alertFilter,
                                    onSelectFilter = { alertFilter = it },
                                    onOpenWorkflow = { showDecisionWorkflow = true }
                                )
                            }
                        }
                    }

                    ControlRoomTab.DYNAMIC_ETA -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1440.dp)) {
                                if (inspectingTrainNumber != null) {
                                    RealTrainTrackerScreen(
                                        initialTrainNumber = inspectingTrainNumber,
                                        onBackToHome = { inspectingTrainNumber = null }
                                    )
                                } else {
                                    DynamicEtaTabContent(
                                        initialFilter = selectedTrainFilter,
                                        currentIst = currentIstTime,
                                        onInspectTrain = { trainNo -> inspectingTrainNumber = trainNo }
                                    )
                                }
                            }
                        }
                    }

                    ControlRoomTab.THROUGHPUT -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1440.dp)) {
                                ThroughputTabContent(
                                    throughputList = throughputList,
                                    decisionState = decisionState,
                                    onOpenWorkflow = { showDecisionWorkflow = true }
                                )
                            }
                        }
                    }

                    ControlRoomTab.REPORTS -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1440.dp)) {
                                ReportsTabContent(reports = reportsList)
                            }
                        }
                    }
                }
            }

            // --- 3. BOTTOM CONTROL ROOM NAVIGATION BAR (Always at bottom across all screens) ---
            Surface(
                color = HeaderNavy,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 1200.dp)
                        .padding(horizontal = if (isWideScreen) 32.dp else 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlRoomTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E3A8A) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = tab }
                        ) {
                            if (isWideScreen) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = tab.icon, fontSize = 16.sp)
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(text = tab.icon, fontSize = 16.sp)
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. INTERACTIVE WHAT-IF & AI DECISION WORKFLOW MODAL ---
        if (showDecisionWorkflow) {
            DecisionWorkflowDialog(
                scenario = scenario,
                recommendation = recommendation,
                safetyAudit = safetyAudit,
                decisionState = decisionState,
                holdMinutes = holdMinutesValue,
                onHoldMinutesChange = { holdMinutesValue = it },
                onApprove = {
                    decisionState = ControllerDecisionType.APPROVE
                    showDecisionWorkflow = false
                },
                onModify = {
                    decisionState = ControllerDecisionType.MODIFY
                },
                onReject = {
                    decisionState = ControllerDecisionType.REJECT
                    showDecisionWorkflow = false
                },
                onDismiss = { showDecisionWorkflow = false }
            )
        }
    }
}

/**
 * 1. Responsive Home Screen Content
 * Adapts between a balanced 2-column operational layout on laptops/desktops
 * and an ergonomic single-column stacked layout on mobile screens.
 */
@Composable
private fun HomeScreenContent(
    isWideScreen: Boolean,
    activeTrainsCount: Int,
    delayedTrainsCount: Int,
    activeAlertsCount: Int,
    decisionState: ControllerDecisionType,
    onLaunchWorkflow: () -> Unit,
    onResetSimulation: () -> Unit,
    recalibrationTick: Int,
    onRecalibrationTick: () -> Unit,
    showPipelineAudit: Boolean,
    onTogglePipelineAudit: () -> Unit,
    corridorSearchText: String,
    onCorridorSearchChange: (String) -> Unit,
    corridorFilter: String,
    onCorridorFilterChange: (String) -> Unit,
    expandedTrainNumber: String?,
    onToggleExpandTrain: (String) -> Unit,
    onSelectActiveTrains: () -> Unit,
    onSelectDelayedTrains: () -> Unit,
    onSelectAlerts: () -> Unit,
    onSelectNetwork: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1440.dp)
                .padding(horizontal = if (isWideScreen) 24.dp else 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWideScreen) {
                // Wide Screen (Desktop / Laptop): 2-Column Responsive Operational Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column: Operational Simulation & Continuous Recalibration
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OperationalNoticeBanner()
                        SihScenarioHeroCard(
                            decisionState = decisionState,
                            onLaunchWorkflow = onLaunchWorkflow,
                            onResetSimulation = onResetSimulation
                        )
                        ContinuousRecalibrationCard(
                            tickIndex = recalibrationTick,
                            onTick = onRecalibrationTick,
                            showAudit = showPipelineAudit,
                            onToggleAudit = onTogglePipelineAudit
                        )
                    }

                    // Right Column: Key KPI Metrics & Corridor Live Snapshot
                    Column(
                        modifier = Modifier.weight(0.85f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        KpiGrid(
                            activeTrainsCount = activeTrainsCount,
                            delayedTrainsCount = delayedTrainsCount,
                            activeAlertsCount = activeAlertsCount,
                            decisionState = decisionState,
                            selectedFilter = corridorFilter,
                            onFilterChange = onCorridorFilterChange,
                            onSelectActiveTrains = onSelectActiveTrains,
                            onSelectDelayedTrains = onSelectDelayedTrains,
                            onSelectAlerts = onSelectAlerts,
                            onSelectNetwork = onSelectNetwork
                        )
                        CorridorQuickStatusCard(
                            searchQuery = corridorSearchText,
                            onSearchChange = onCorridorSearchChange,
                            currentFilter = corridorFilter,
                            onFilterChange = onCorridorFilterChange,
                            expandedTrain = expandedTrainNumber,
                            onToggleExpandTrain = onToggleExpandTrain,
                            onExploreCorridor = onSelectNetwork,
                            onSelectTrains = onSelectActiveTrains
                        )
                    }
                }
            } else {
                // Narrow Screen (Mobile / Tablet): Fluid Stacked Layout
                OperationalNoticeBanner()
                SihScenarioHeroCard(
                    decisionState = decisionState,
                    onLaunchWorkflow = onLaunchWorkflow,
                    onResetSimulation = onResetSimulation
                )
                KpiGrid(
                    activeTrainsCount = activeTrainsCount,
                    delayedTrainsCount = delayedTrainsCount,
                    activeAlertsCount = activeAlertsCount,
                    decisionState = decisionState,
                    selectedFilter = corridorFilter,
                    onFilterChange = onCorridorFilterChange,
                    onSelectActiveTrains = onSelectActiveTrains,
                    onSelectDelayedTrains = onSelectDelayedTrains,
                    onSelectAlerts = onSelectAlerts,
                    onSelectNetwork = onSelectNetwork
                )
                ContinuousRecalibrationCard(
                    tickIndex = recalibrationTick,
                    onTick = onRecalibrationTick,
                    showAudit = showPipelineAudit,
                    onToggleAudit = onTogglePipelineAudit
                )
                CorridorQuickStatusCard(
                    searchQuery = corridorSearchText,
                    onSearchChange = onCorridorSearchChange,
                    currentFilter = corridorFilter,
                    onFilterChange = onCorridorFilterChange,
                    expandedTrain = expandedTrainNumber,
                    onToggleExpandTrain = onToggleExpandTrain,
                    onExploreCorridor = onSelectNetwork,
                    onSelectTrains = onSelectActiveTrains
                )
            }
        }
    }
}

/**
 * Notice Banner: Operational Corridor Simulation Context
 */
@Composable
private fun OperationalNoticeBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NoticeBg,
        border = BorderStroke(1.dp, NoticeBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(NoticeTitle),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Operational Corridor Simulation • Nashik Section",
                    color = NoticeTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Operating with simulated Central Railway telemetry (Igatpuri - Manmad). RAILअंदाज acts as an operational decision-support system; human controller approval is mandatory.",
                    color = NoticeText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * SIH Scenario Hero Card (Interactive Regulation & Signal State)
 */
@Composable
private fun SihScenarioHeroCard(
    decisionState: ControllerDecisionType,
    onLaunchWorkflow: () -> Unit,
    onResetSimulation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ScenarioCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Text(
                        text = "OPERATIONAL REGULATION SCENARIO",
                        color = ScenarioBlueTag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Interactive Signal State Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF065F46) else Color(0xFF7F1D1D)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF34D399) else Color(0xFFF87171))
                        )
                        Text(
                            text = if (decisionState == ControllerDecisionType.APPROVE) "Signal Clear (Proceed)" else "Signal Restrictive (Caution)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Odha Section Congestion & Dynamic ETA Recalculation",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp
            )

            Text(
                text = if (decisionState == ControllerDecisionType.APPROVE) {
                    "✔ Controller Decision APPROVED: 12109 regulated at Devlali. Precedence granted to 22223. Odha bottleneck resolved, 19m delay saved."
                } else {
                    "Train 12109 Panchavati Express facing restrictive signal at Devlali - Nashik Road. Test the complete AI Recommendation -> Safety Engine -> Controller Decision loop."
                },
                color = if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLaunchWorkflow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF047857) else ScenarioBtnGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "▶", fontSize = 12.sp, color = Color.White)
                        Text(
                            text = if (decisionState == ControllerDecisionType.APPROVE) "Review Decision & Safety Logs" else "Launch What-If & AI Decision Workflow",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (decisionState == ControllerDecisionType.APPROVE) {
                    OutlinedButton(
                        onClick = onResetSimulation,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD))
                    ) {
                        Text(
                            text = "↺ Reset",
                            color = Color(0xFF93C5FD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2x2 Key Metric KPI Grid with Interactive Filtering
 */
@Composable
private fun KpiGrid(
    activeTrainsCount: Int,
    delayedTrainsCount: Int,
    activeAlertsCount: Int,
    decisionState: ControllerDecisionType,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onSelectActiveTrains: () -> Unit,
    onSelectDelayedTrains: () -> Unit,
    onSelectAlerts: () -> Unit,
    onSelectNetwork: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiMetricCard(
                icon = "🚆",
                iconBg = Color(0xFFD1FAE5),
                value = "$activeTrainsCount",
                title = "Active Trains",
                subtitle = "Coaching services in corridor",
                footerText = "All Tracking Normal",
                footerColor = Color(0xFF059669),
                isSelected = selectedFilter == "ACTIVE",
                modifier = Modifier.weight(1f).clickable {
                    onFilterChange(if (selectedFilter == "ACTIVE") "ALL" else "ACTIVE")
                }
            )

            KpiMetricCard(
                icon = "⚠️",
                iconBg = Color(0xFFFFEDD5),
                value = "$delayedTrainsCount",
                title = "Delayed Trains",
                subtitle = "Trains needing regulation",
                footerText = if (decisionState == ControllerDecisionType.APPROVE) "Delay Regulated" else "Cascading Risk",
                footerColor = if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF059669) else Color(0xFFEA580C),
                isSelected = selectedFilter == "DELAYED",
                modifier = Modifier.weight(1f).clickable {
                    onFilterChange(if (selectedFilter == "DELAYED") "ALL" else "DELAYED")
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiMetricCard(
                icon = "🔔",
                iconBg = Color(0xFFFEE2E2),
                value = "$activeAlertsCount",
                title = "Active Alerts",
                subtitle = "1 critical / restrictive",
                footerText = if (decisionState == ControllerDecisionType.APPROVE) "Alert Cleared" else "Odha Halt Flagged",
                footerColor = if (decisionState == ControllerDecisionType.APPROVE) Color(0xFF059669) else Color(0xFFDC2626),
                isSelected = false,
                modifier = Modifier.weight(1f).clickable { onSelectAlerts() }
            )

            KpiMetricCard(
                icon = "🗺️",
                iconBg = Color(0xFFCCFBF1),
                value = "5 Sec",
                title = "Live Network",
                subtitle = "1 congested bottleneck",
                footerText = "Interlocking Active",
                footerColor = Color(0xFF059669),
                isSelected = false,
                modifier = Modifier.weight(1f).clickable { onSelectNetwork() }
            )
        }
    }
}

/**
 * Continuous Recalibration Engine Card (Interactive Telemetry Simulation & 10-Step AI Audit)
 */
@Composable
private fun ContinuousRecalibrationCard(
    tickIndex: Int,
    onTick: () -> Unit,
    showAudit: Boolean,
    onToggleAudit: () -> Unit
) {
    var nowEpoch by remember { mutableStateOf(org.example.project.getCurrentEpochMillis()) }
    LaunchedEffect(tickIndex) {
        nowEpoch = org.example.project.getCurrentEpochMillis()
    }

    val istNow = IndianStandardTime.fromEpochMillis(nowEpoch)
    val lastUpdatedStr = istNow.time12Standard

    fun formatOffsetTime(minutesOffset: Int): String {
        val t = IndianStandardTime.fromEpochMillis(nowEpoch + (minutesOffset * 60_000L))
        return t.time12Standard
    }

    val sampleStates = listOf(
        Triple(formatOffsetTime(24), lastUpdatedStr, Pair(formatOffsetTime(21), "+3 minutes")),
        Triple(formatOffsetTime(27), lastUpdatedStr, Pair(formatOffsetTime(24), "+3 minutes")),
        Triple(formatOffsetTime(21), lastUpdatedStr, Pair(formatOffsetTime(27), "▲ -6 min (Recovery)"))
    )
    val sampleReasons = listOf(
        "Current segment travel time increased due to Devlali - Nashik Road speed restriction (30 km/h). Downstream delay cascaded by +3 min.",
        "Odha single-line track occupancy extended by slow freight clearance. Headway separation enforced.",
        "Caution order lifted early at Devlali Down line. High-speed tractive recovery achieved."
    )

    val currentData = sampleStates[tickIndex % sampleStates.size]
    val currentReason = sampleReasons[tickIndex % sampleReasons.size]

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, KpiCardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "LIVE RECALIBRATION ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Text(
                        text = "Live Telemetry Cycle #${(tickIndex % 3) + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Train Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "12109 Panchavati Express",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Target Station: NK / Nashik Road",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = "In Corridor",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Main ETA & Last Updated Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "PREDICTED ETA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = currentData.first,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last updated",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = currentData.second,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            // Previous ETA vs Delta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Previous ETA",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = currentData.third.first,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                val isRecovery = currentData.third.second.contains("Recovery")
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRecovery) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, if (isRecovery) Color(0xFFA7F3D0) else Color(0xFFFECACA)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "ETA Change",
                            fontSize = 10.sp,
                            color = if (isRecovery) Color(0xFF065F46) else Color(0xFF991B1B)
                        )
                        Text(
                            text = currentData.third.second,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecovery) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                }
            }

            // Recalibration Reason
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "💡", fontSize = 14.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Recalibration Reason",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = currentReason,
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Interactive Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(text = "⚡ Simulate Live Telemetry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onToggleAudit,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(0.9f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(text = if (showAudit) "Hide Audit" else "🔍 AI Audit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 10-Step AI Recalibration Pipeline Drawer
            AnimatedVisibility(visible = showAudit) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "10-Step Pipeline Execution Audit (Latency: 2.4 ms)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        listOf(
                            "1. Data Validation: GPS & Speed Telemetry" to "0.2 ms",
                            "2. State Comparison: Devlali - Nashik Section" to "0.1 ms",
                            "3. Feature Extraction: Headway & Slacks" to "0.4 ms",
                            "4. Quantile ETA Predictor (p10, p50, p90)" to "0.7 ms",
                            "5. Delay Propagation Modeling to Manmad" to "0.3 ms",
                            "6. Confidence Recalibration: 89%" to "0.2 ms",
                            "7. Root-Cause Explanation Synthesizer" to "0.2 ms",
                            "8. Safety Interlocking & Signal Verification" to "0.1 ms",
                            "9. Webhook & WebSocket Corridor Broadcast" to "0.1 ms",
                            "10. Dashboard Telemetry Push [SYNCHRONIZED]" to "0.1 ms"
                        ).forEach { (step, time) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "✔", fontSize = 10.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                    Text(text = step, fontSize = 10.sp, color = Color(0xFF334155))
                                }
                                Text(text = time, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Corridor Quick Status & Active Train Summary Card (Interactive Search & Inline Inspection)
 */
@Composable
private fun CorridorQuickStatusCard(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    expandedTrain: String?,
    onToggleExpandTrain: (String) -> Unit,
    onExploreCorridor: () -> Unit,
    onSelectTrains: () -> Unit
) {
    val masterList = listOf(
        Triple("12109 Panchavati Exp", "Devlali (DVL) • PF 2", "+18m (Holding)"),
        Triple("22223 CSMT SNSI Vande Bharat", "Approaching Odha", "On-Time (Priority)"),
        Triple("17617 Tapovan Express", "Nashik Road (NK) • PF 1", "+6m (Departed)"),
        Triple("12139 Sevagram Express", "Igatpuri (IGP) • Thal Ghat", "+12m (Banking)")
    )

    val filteredList = masterList.filter { (train, location, status) ->
        val matchesSearch = searchQuery.isBlank() ||
                train.contains(searchQuery, ignoreCase = true) ||
                location.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (currentFilter) {
            "DELAYED" -> !status.contains("On-Time")
            "ACTIVE" -> status.contains("On-Time") || status.contains("Departed")
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, KpiCardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CORRIDOR ACTIVE MONITOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Igatpuri ➔ Manmad",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            // Interactive Search Field
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🔍", fontSize = 12.sp)
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(text = "Search train (12109, Vande Bharat, NK)...", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF0F172A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "✕",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.clickable { onSearchChange("") }
                        )
                    }
                }
            }

            // Interactive Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "ALL" to "All (${masterList.size})",
                    "DELAYED" to "Delayed (3)",
                    "ACTIVE" to "On-Time (1)"
                ).forEach { (filterKey, label) ->
                    val isSelected = currentFilter == filterKey
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                        modifier = Modifier.clickable { onFilterChange(filterKey) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Filtered Train List with Expandable Inspection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filteredList.isEmpty()) {
                    Text(
                        text = "No trains match the filter criteria.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                filteredList.forEach { (train, location, status) ->
                    val trainNo = train.substringBefore(" ")
                    val isExpanded = expandedTrain == trainNo

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isExpanded) Color(0xFF93C5FD) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExpandTrain(trainNo) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = train,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = location,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when {
                                            status.contains("On-Time") -> Color(0xFFD1FAE5)
                                            status.contains("Holding") -> Color(0xFFFEE2E2)
                                            else -> Color(0xFFFEF3C7)
                                        }
                                    ) {
                                        Text(
                                            text = status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                status.contains("On-Time") -> Color(0xFF065F46)
                                                status.contains("Holding") -> Color(0xFF991B1B)
                                                else -> Color(0xFF92400E)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(text = if (isExpanded) "▲" else "▼", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                            }

                            // Expanded Inspection Drawer
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Speed: 102 km/h", fontSize = 10.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                        Text(text = "Track: DOWN MAIN Line", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Loco: WAP-7 #30482", fontSize = 10.sp, color = Color(0xFF64748B))
                                        Text(text = "AI Confidence: 91%", fontSize = 10.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = onSelectTrains,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text(text = "Open Full Dynamic Schedule Matrix ➔", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick corridor navigation actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExploreCorridor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(text = "View Topology", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onSelectTrains,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(text = "Dynamic ETAs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * Clean White KPI Metric Card matching screenshot
 */
@Composable
private fun KpiMetricCard(
    icon: String,
    iconBg: Color,
    value: String,
    title: String,
    subtitle: String,
    footerText: String,
    footerColor: Color,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFFF0F9FF) else KpiCardBg,
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF1E3A8A)) else BorderStroke(1.dp, KpiCardBorder),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 16.sp)
                }

                // Big bold numeric value
                Text(
                    text = value,
                    color = TextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column {
                Text(
                    text = title,
                    color = TextDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Footer dot + status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(footerColor)
                )
                Text(
                    text = footerText,
                    color = footerColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 2. Full 5-Step AI Recommendation -> Safety Engine -> Controller Decision Modal
 */
@Composable
private fun DecisionWorkflowDialog(
    scenario: WhatIfScenario,
    recommendation: AiRecommendation,
    safetyAudit: SafetyEngineAudit,
    decisionState: ControllerDecisionType,
    holdMinutes: Int,
    onHoldMinutesChange: (Int) -> Unit,
    onApprove: () -> Unit,
    onModify: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(18.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Decision Support & Safety Loop",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                // Step 1: Alert Details
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "STEP 1: ALERT DETAILS", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = scenario.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = scenario.scenarioDescription, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }

                // Step 2: What-If Scenario
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "STEP 2: WHAT-IF PRECEDENCE CONFLICT", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Leading: ${scenario.conflictTrainNumber} ${scenario.conflictTrainName}", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Trailing: ${scenario.affectedTrainNumber} ${scenario.affectedTrainName} (+${scenario.initialDelayMinutes}m)", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Step 3: AI Recommendation
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF064E3B),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "STEP 3: AI RECOMMENDATION", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Confidence: ${recommendation.confidencePercent}%", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = recommendation.recommendedAction, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Regulate at: ${recommendation.regulationStation} • Hold: $holdMinutes min", color = Color(0xFF6EE7B7), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = recommendation.rationale, color = Color(0xFFD1FAE5), fontSize = 11.sp)
                        Text(text = "Expected Net Delay Savings: +${recommendation.expectedSavingsMinutes} mins across corridor", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Step 4: Safety Engine Audit
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "STEP 4: SAFETY ENGINE AUDIT", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Score: ${safetyAudit.safetyScorePercent}% (CERTIFIED SAFE)", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        safetyAudit.ruleChecks.forEach { check ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "✔", color = Color(0xFF10B981), fontSize = 12.sp)
                                Column {
                                    Text(text = check.ruleName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = check.safetyMargin, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Step 5: Controller Decision Actions
                Text(text = "STEP 5: CONTROLLER DECISION", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Approve Button
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "✔ Approve", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Modify Button
                    Button(
                        onClick = {
                            onHoldMinutesChange(if (holdMinutes == 7) 10 else 7)
                            onModify()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "✎ Modify", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Reject Button
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "✖ Reject", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

/**
 * 3. Network Tab Content (Live Map, Station details, Section details)
 */
@Composable
private fun NetworkMapContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Live Railway Network Topology (Nashik Section)", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = "3-Level Operational Drilldown: Train Details ➔ Station Details ➔ Section Details", color = TextMuted, fontSize = 12.sp)

        // Visual Mainline Station Nodes
        listOf(
            Triple("Kalyan (KYN)", "Platform 1-8 • Junction • BB Division", "Clear Mainline"),
            Triple("Kasara (KSRA)", "Platform 1-4 • Thal Ghat Base", "Banking Locomotives Attached"),
            Triple("Igatpuri (IGP)", "Platform 1-4 • Thal Ghat Summit", "Brake Safety Tests Active"),
            Triple("Devlali (DVL)", "Platform 1-3 • Military Siding", "Loop Line 2 Regulation Ready"),
            Triple("Nashik Road (NK)", "Platform 1-4 • Primary Passenger Hub", "Automatic Block Signaling"),
            Triple("Odha (ODH)", "Platform 1-2 • Single-line Bridge", "Caution Order 30 km/h"),
            Triple("Manmad (MMR)", "Platform 1-6 • BSL Division Junction", "Clear Lines")
        ).forEach { (station, details, status) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KpiCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = station, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = details, color = TextMuted, fontSize = 11.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (status.contains("Caution") || status.contains("Banking")) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                    ) {
                        Text(
                            text = status,
                            color = if (status.contains("Caution") || status.contains("Banking")) Color(0xFFB45309) else Color(0xFF065F46),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. Alerts Tab Content (Active, Warning, Resolved)
 */
@Composable
private fun AlertsTabContent(
    alerts: List<ControlRoomAlert>,
    currentFilter: AlertCategory,
    onSelectFilter: (AlertCategory) -> Unit,
    onOpenWorkflow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Operational Safety & Signalling Alerts", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AlertCategory.values().forEach { cat ->
                val isSelected = currentFilter == cat
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSelectFilter(cat) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) HeaderNavy else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) HeaderNavy else KpiCardBorder)
                ) {
                    Text(
                        text = cat.label,
                        color = if (isSelected) Color.White else TextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Filtered Alert List
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(alerts.filter { it.category == currentFilter }) { alert ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        when (alert.severity) {
                            "CRITICAL" -> Color(0xFFEF4444)
                            "WARNING" -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = alert.title, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (alert.severity) {
                                    "CRITICAL" -> Color(0xFFFEE2E2)
                                    "WARNING" -> Color(0xFFFEF3C7)
                                    else -> Color(0xFFD1FAE5)
                                }
                            ) {
                                Text(
                                    text = alert.severity,
                                    color = when (alert.severity) {
                                        "CRITICAL" -> Color(0xFFDC2626)
                                        "WARNING" -> Color(0xFFD97706)
                                        else -> Color(0xFF059669)
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = alert.timestampFormatted, color = TextMuted, fontSize = 11.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🚆 ${alert.trainNumber} - ${alert.trainName}",
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (alert.delayMinutes > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "+${alert.delayMinutes}m delay",
                                        color = Color(0xFFDC2626),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(text = "📍 Section: ${alert.section}", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(text = alert.description, color = TextDark, fontSize = 11.sp)

                        if (alert.hasWhatIfWorkflow) {
                            Button(
                                onClick = onOpenWorkflow,
                                colors = ButtonDefaults.buttonColors(containerColor = ScenarioBtnGreen),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(text = "Launch What-If Decision Loop", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. Dynamic ETA Tab Content (Upcoming Stations & Explainability)
 */
@Composable
private fun DynamicEtaTabContent(
    filter: String,
    allTrains: List<TrainJourney>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Dynamic ETA & AI Delay Explainability", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = "Showing live trains traversing Nashik Road corridor ($filter)", color = TextMuted, fontSize = 12.sp)

        allTrains.forEach { train ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KpiCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${train.trainNumber} • ${train.trainName}", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Nashik: ${train.stops.find { it.station.code == "NK" }?.scheduledArrival ?: "09:30"}", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "Route: ${train.source.name} ➔ ${train.destination.name}", color = TextMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "⚡ Speed: 104 km/h", color = Color(0xFF059669), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "⏱ ETA Confidence: 94% (High)", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 6. Throughput Tab Content (Section Performance & Optimization)
 */
@Composable
private fun ThroughputTabContent(
    throughputList: List<SectionThroughput>,
    decisionState: ControllerDecisionType,
    onOpenWorkflow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "Corridor Throughput & Section Capacity Analysis", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = "Central Railway • Bhusawal Division Mainline", color = TextMuted, fontSize = 12.sp)

        throughputList.forEach { section ->
            val isCongested = section.bottleneckStatus == "BOTTLENECK" && decisionState != ControllerDecisionType.APPROVE
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (isCongested) Color(0xFFEF4444) else KpiCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = section.sectionName, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCongested) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)
                        ) {
                            Text(
                                text = if (isCongested) "BOTTLENECK (${section.utilizationPercent}%)" else "CAPACITY OK (${section.utilizationPercent}%)",
                                color = if (isCongested) Color(0xFFDC2626) else Color(0xFF065F46),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Capacity: ${section.capacityTrainsPerHour} trains/hr • Current Load: ${section.currentTrainsPerHour} trains/hr • Avg Speed: ${section.averageSpeedKmH} km/h",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "💡 Optimization: ${section.optimizationRecommendation}",
                        color = Color(0xFF1D4ED8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (isCongested) {
                        Button(
                            onClick = onOpenWorkflow,
                            colors = ButtonDefaults.buttonColors(containerColor = ScenarioBtnGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Resolve Bottleneck via AI Decision Loop", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. Reports Tab Content
 */
@Composable
private fun ReportsTabContent(
    reports: List<ReportSummary>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Punctuality & Throughput Reports", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(text = "📥 Export PDF", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        reports.forEach { r ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, KpiCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "${r.trainNumber} ${r.trainName}", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Nashik: Sched ${r.scheduledArrivalNashik} ➔ Actual ${r.predictedArrivalNashik} (+${r.delayMinutes}m)", color = TextMuted, fontSize = 11.sp)
                        Text(text = "Notes: ${r.thalGhatPerformance}", color = Color(0xFF059669), fontSize = 10.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFD1FAE5)
                    ) {
                        Text(
                            text = "Grade ${r.punctualityGrade}",
                            color = Color(0xFF065F46),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
