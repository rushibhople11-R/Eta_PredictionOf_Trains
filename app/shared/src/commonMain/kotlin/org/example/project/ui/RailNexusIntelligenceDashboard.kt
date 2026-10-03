package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.data.NashikStationData
import org.example.project.engine.*
import org.example.project.model.*
import org.example.project.network.LiveStreamMessage
import org.example.project.network.RailNexusLiveStreamClient
import org.example.project.network.ServerConnectionStatus
import org.example.project.ui.theme.*
import org.example.project.getCurrentEpochMillis

// Centralized Traffic Control (CTC) Palette
private val CtcDarkBg = Color(0xFF080E1A)
private val CtcPanelBg = Color(0xFF0E1726)
private val CtcCardBg = Color(0xFF131F33)
private val CtcCardBorder = Color(0xFF1E2D4A)
private val CtcHeaderBg = Color(0xFF0B1322)

private val SignalGreen = Color(0xFF10B981)
private val CautionAmber = Color(0xFFF59E0B)
private val DangerRed = Color(0xFFEF4444)
private val AiCyan = Color(0xFF06B6D4)
private val AiBlue = Color(0xFF38BDF8)
private val RailwayGold = Color(0xFFFBBF24)

private val TextWhite = Color(0xFFF8FAFC)
private val TextMutedSlate = Color(0xFF94A3B8)
private val TextDarkMuted = Color(0xFF64748B)

/**
 * RailNexus Professional Railway Intelligence Dashboard
 * Implements all 14 mission-critical modules for Smart India Hackathon:
 * 1. Live Train Overview
 * 2. Train Search
 * 3. Current Location
 * 4. Current Delay
 * 5. Predicted ETA
 * 6. Station-wise ETA Table (with p10/p50/p90 bounds)
 * 7. Delay Propagation Timeline Visualization
 * 8. Route Map Track Topology
 * 9. ML Confidence Dial (87%)
 * 10. "WHY ETA CHANGED" Explainability Engine
 * 11. Active Corridor Alerts
 * 12. Historical vs Predicted Trajectory
 * 13. Network / Operational Status
 * 14. Interactive What-If Scenario Sandbox
 */
@Composable
fun RailNexusIntelligenceDashboard(
    onOpenControlRoom: () -> Unit = {},
    onOpenStationHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allTrains = remember { NashikStationData.allNashikTrains }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTrainIndex by remember { mutableStateOf(0) }

    val filteredTrains = remember(searchQuery) {
        if (searchQuery.isBlank()) allTrains
        else allTrains.filter {
            it.trainNumber.contains(searchQuery, ignoreCase = true) ||
                    it.trainName.contains(searchQuery, ignoreCase = true) ||
                    it.stops.any { s -> s.station.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val currentTrain = remember(filteredTrains, selectedTrainIndex) {
        if (filteredTrains.isNotEmpty()) {
            filteredTrains[selectedTrainIndex.coerceIn(0, filteredTrains.size - 1)]
        } else allTrains.first()
    }

    // Interactive Delay Simulation & Location
    var currentStationIndex by remember(currentTrain) { mutableStateOf(1.coerceAtMost(currentTrain.stops.size - 1)) }
    var simulatedDelayMinutes by remember(currentTrain) { mutableStateOf(18f) }

    // What-If Simulation Sandbox State
    var whatIfHoldMinutes by remember { mutableStateOf(0f) }
    var isWhatIfActive by remember { mutableStateOf(false) }

    val effectiveCurrentDelay = if (isWhatIfActive) simulatedDelayMinutes + whatIfHoldMinutes else simulatedDelayMinutes

    // Predictions calculated via core Delay Propagation Engine
    val predictions = remember(currentTrain, currentStationIndex, effectiveCurrentDelay) {
        DelayPropagationEngine.predictJourney(
            journey = currentTrain,
            currentStopIndex = currentStationIndex,
            currentDelayMinutes = effectiveCurrentDelay.toInt()
        )
    }

    val currentStop = currentTrain.stops[currentStationIndex]
    val nextStopIndex = (currentStationIndex + 1).coerceAtMost(currentTrain.stops.size - 1)
    val nextStop = currentTrain.stops[nextStopIndex]
    val nextStopPrediction = predictions.getOrNull(nextStopIndex)
    val finalPrediction = predictions.lastOrNull()

    // 4-Box Delay Cascade Metrics
    val curDelayInt = effectiveCurrentDelay.toInt()
    val nextPredDelay = nextStopPrediction?.delayMinutes ?: (curDelayInt + 3)
    val finalImpactDelay = finalPrediction?.delayMinutes ?: (curDelayInt + 9)
    val confidencePct = 87

    // Live IST Clock
    var currentEpoch by remember { mutableStateOf(getCurrentEpochMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentEpoch = getCurrentEpochMillis()
        }
    }
    val istTime = remember(currentEpoch) { IndianStandardTime.fromEpochMillis(currentEpoch) }

    // Real-Time WebSocket Streaming Client & Events
    val liveStreamClient = remember { RailNexusLiveStreamClient() }
    val connectionStatus by liveStreamClient.status.collectAsState()
    val recentMovements = remember { mutableStateListOf<LiveStreamMessage.ActualMovement>() }
    val recentDelayEvents = remember { mutableStateListOf<LiveStreamMessage.DelayEvent>() }
    var autoSyncLiveFeed by remember { mutableStateOf(true) }
    var lastPushedToast by remember { mutableStateOf<String?>(null) }

    // Start WebSocket listener and collect events
    LaunchedEffect(liveStreamClient) {
        liveStreamClient.startListening(this)
        liveStreamClient.events.collect { msg ->
            when (msg) {
                is LiveStreamMessage.ActualMovement -> {
                    if (recentMovements.none { it.movementId == msg.movementId }) {
                        recentMovements.add(0, msg)
                        if (recentMovements.size > 20) recentMovements.removeAt(recentMovements.size - 1)
                    }
                    if (autoSyncLiveFeed && msg.trainNumber == currentTrain.trainNumber) {
                        val idx = currentTrain.stops.indexOfFirst { it.station.code == msg.stationCode }
                        if (idx >= 0) {
                            currentStationIndex = idx
                            simulatedDelayMinutes = msg.observedDelayMinutes.toFloat()
                        }
                    }
                    lastPushedToast = "Train ${msg.trainNumber} at ${msg.stationName} (${msg.stationCode}) • Arr: ${msg.actualArrival} (Delay: +${msg.observedDelayMinutes}m)"
                }
                is LiveStreamMessage.DelayEvent -> {
                    if (recentDelayEvents.none { it.eventId == msg.eventId }) {
                        recentDelayEvents.add(0, msg)
                        if (recentDelayEvents.size > 20) recentDelayEvents.removeAt(recentDelayEvents.size - 1)
                    }
                    lastPushedToast = "Delay Event: ${msg.stationCode} (+${msg.delayDurationMinutes}m) • ${msg.primaryReasonCategory}"
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(lastPushedToast) {
        if (lastPushedToast != null) {
            delay(4500)
            lastPushedToast = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CtcDarkBg)
    ) {
        val isWideScreen = maxWidth >= 1080.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // 1. TOP EXECUTIVE CTC COMMAND BAR (Header + Search + WS Status)
            // ==========================================
            CtcTopCommandBar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                istTime = istTime,
                connectionStatus = connectionStatus,
                autoSync = autoSyncLiveFeed,
                onToggleAutoSync = { autoSyncLiveFeed = it },
                onOpenControlRoom = onOpenControlRoom,
                onOpenStationHub = onOpenStationHub
            )

            // Dynamic Live WebSocket Push Toast Banner
            AnimatedVisibility(
                visible = lastPushedToast != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF064E3B),
                    border = BorderStroke(1.dp, SignalGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ LIVE PUSH:", color = SignalGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lastPushedToast ?: "", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ==========================================
            // CONTEXTUAL TRAIN SELECTION STRIP
            // ==========================================
            CtcTrainSelectorStrip(
                trains = filteredTrains,
                selectedIndex = selectedTrainIndex,
                onSelectTrain = {
                    selectedTrainIndex = it
                    currentStationIndex = 1
                    whatIfHoldMinutes = 0f
                    isWhatIfActive = false
                }
            )

            // ==========================================
            // MAIN DASHBOARD COCKPIT (Dual Column or Stacked)
            // ==========================================
            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN (7 Cols): Train Intelligence, Cascade Boxes, Explainability, Table
                    Column(
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Live Train Hero Card (Number, Speed, Location)
                        LiveTrainOverviewCard(
                            train = currentTrain,
                            currentStop = currentStop,
                            nextStop = nextStop,
                            speedKmph = 102
                        )

                        // Continuous Recalibration Engine (ETA, Last Updated, Previous ETA, Change, Reason)
                        RecalibrationCard(
                            train = currentTrain,
                            currentDelayMinutes = curDelayInt
                        )

                        // 2. The 4-Box Delay Cascade Cards
                        FourBoxDelayCascadeGrid(
                            currentDelay = curDelayInt,
                            nextStationDelay = nextPredDelay,
                            finalImpactDelay = finalImpactDelay,
                            confidencePct = confidencePct
                        )

                        // 3. "WHY ETA CHANGED" Explainability Card
                        WhyEtaChangedExplainabilityCard(
                            currentDelay = curDelayInt,
                            train = currentTrain,
                            currentStop = currentStop
                        )

                        // 4. Station-Wise Dynamic ETA Table
                        StationWiseDynamicEtaTable(
                            predictions = predictions,
                            currentStopIndex = currentStationIndex
                        )

                        // 5. Delay Propagation Timeline Visualization
                        DelayPropagationTimelineCard(
                            predictions = predictions,
                            currentStopIndex = currentStationIndex
                        )
                    }

                    // RIGHT COLUMN (5 Cols): Route Map, What-If, Alerts, Network Status
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 6. Interactive Route Map Track Topology
                        RouteMapTopologyCard(
                            train = currentTrain,
                            currentStopIndex = currentStationIndex
                        )

                        // 6b. REAL-TIME WEBSOCKET PUSH FEED CARD (Actual Movements & Delay Events)
                        RealTimeCorridorWebSocketFeedCard(
                            connectionStatus = connectionStatus,
                            recentMovements = recentMovements,
                            recentDelayEvents = recentDelayEvents,
                            autoSync = autoSyncLiveFeed,
                            onToggleAutoSync = { autoSyncLiveFeed = it },
                            onSimulateMovement = {
                                val nextStopIdx = (currentStationIndex + 1) % currentTrain.stops.size
                                val stop = currentTrain.stops[nextStopIdx]
                                liveStreamClient.dispatchSyntheticMovement(
                                    trainNumber = currentTrain.trainNumber,
                                    stationCode = stop.station.code,
                                    stationName = stop.station.name,
                                    delayMin = (effectiveCurrentDelay.toInt() + (-1..2).random()).coerceAtLeast(0),
                                    platform = if (stop.station.code == "NK") 2 else 1
                                )
                            },
                            onSimulateDelayEvent = {
                                val stop = currentTrain.stops[currentStationIndex]
                                liveStreamClient.dispatchSyntheticDelayEvent(
                                    trainNumber = currentTrain.trainNumber,
                                    stationCode = stop.station.code,
                                    delayMin = 14,
                                    category = "THAL_GHAT_CONGESTION",
                                    explanation = "Heavy trailing headway behind freight consist on 1:37 Kasara-Igatpuri gradient"
                                )
                            }
                        )

                        // 7. Interactive What-If Scenario Sandbox
                        WhatIfScenarioSandboxCard(
                            isWhatIfActive = isWhatIfActive,
                            onToggleWhatIf = { isWhatIfActive = it },
                            holdMinutes = whatIfHoldMinutes,
                            onHoldChange = { whatIfHoldMinutes = it },
                            baseDelay = simulatedDelayMinutes.toInt(),
                            recalculatedFinal = finalImpactDelay
                        )

                        // 8. Historical vs Predicted Run Time Comparison
                        HistoricalVsPredictedCard(
                            scheduledMinutes = 195,
                            historicalTypicalMinutes = 208,
                            todayPredictedMinutes = 195 + finalImpactDelay
                        )

                        // 9. Active Corridor Alerts & Operational Status
                        NetworkAndAlertsCard()
                    }
                }
            } else {
                // Single Column Adaptive Flow for Mobile & Tablets
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LiveTrainOverviewCard(
                        train = currentTrain,
                        currentStop = currentStop,
                        nextStop = nextStop,
                        speedKmph = 102
                    )

                    // Continuous Recalibration Engine (ETA, Last Updated, Previous ETA, Change, Reason)
                    RecalibrationCard(
                        train = currentTrain,
                        currentDelayMinutes = curDelayInt
                    )

                    FourBoxDelayCascadeGrid(
                        currentDelay = curDelayInt,
                        nextStationDelay = nextPredDelay,
                        finalImpactDelay = finalImpactDelay,
                        confidencePct = confidencePct
                    )

                    WhyEtaChangedExplainabilityCard(
                        currentDelay = curDelayInt,
                        train = currentTrain,
                        currentStop = currentStop
                    )

                    RouteMapTopologyCard(
                        train = currentTrain,
                        currentStopIndex = currentStationIndex
                    )

                    RealTimeCorridorWebSocketFeedCard(
                        connectionStatus = connectionStatus,
                        recentMovements = recentMovements,
                        recentDelayEvents = recentDelayEvents,
                        autoSync = autoSyncLiveFeed,
                        onToggleAutoSync = { autoSyncLiveFeed = it },
                        onSimulateMovement = {
                            val nextStopIdx = (currentStationIndex + 1) % currentTrain.stops.size
                            val stop = currentTrain.stops[nextStopIdx]
                            liveStreamClient.dispatchSyntheticMovement(
                                trainNumber = currentTrain.trainNumber,
                                stationCode = stop.station.code,
                                stationName = stop.station.name,
                                delayMin = (effectiveCurrentDelay.toInt() + (-1..2).random()).coerceAtLeast(0),
                                platform = if (stop.station.code == "NK") 2 else 1
                            )
                        },
                        onSimulateDelayEvent = {
                            val stop = currentTrain.stops[currentStationIndex]
                            liveStreamClient.dispatchSyntheticDelayEvent(
                                trainNumber = currentTrain.trainNumber,
                                stationCode = stop.station.code,
                                delayMin = 14,
                                category = "THAL_GHAT_CONGESTION",
                                explanation = "Heavy trailing headway behind freight consist on 1:37 Kasara-Igatpuri gradient"
                            )
                        }
                    )

                    StationWiseDynamicEtaTable(
                        predictions = predictions,
                        currentStopIndex = currentStationIndex
                    )

                    WhatIfScenarioSandboxCard(
                        isWhatIfActive = isWhatIfActive,
                        onToggleWhatIf = { isWhatIfActive = it },
                        holdMinutes = whatIfHoldMinutes,
                        onHoldChange = { whatIfHoldMinutes = it },
                        baseDelay = simulatedDelayMinutes.toInt(),
                        recalculatedFinal = finalImpactDelay
                    )

                    DelayPropagationTimelineCard(
                        predictions = predictions,
                        currentStopIndex = currentStationIndex
                    )

                    HistoricalVsPredictedCard(
                        scheduledMinutes = 195,
                        historicalTypicalMinutes = 208,
                        todayPredictedMinutes = 195 + finalImpactDelay
                    )

                    NetworkAndAlertsCard()
                }
            }
        }
    }
}

// =========================================================================
// SUB-COMPONENTS IMPLEMENTATION
// =========================================================================

/**
 * 1. Top Executive Command Bar
 */
@Composable
private fun CtcTopCommandBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    istTime: IstTime,
    connectionStatus: ServerConnectionStatus,
    autoSync: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    onOpenControlRoom: () -> Unit,
    onOpenStationHub: () -> Unit
) {
    Surface(
        color = CtcHeaderBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Section Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF0284C7)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("RN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RAILNEXUS",
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CautionAmber.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "SIH INTEL",
                                color = CautionAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "Indian Railways Centralized Intelligence • Nashik Corridor",
                        color = TextDarkMuted,
                        fontSize = 10.sp
                    )
                }
            }

            // Quick Train Search Input
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CtcCardBg,
                border = BorderStroke(1.dp, CtcCardBorder),
                modifier = Modifier.width(280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search train (e.g. 12109, NK)...", color = TextDarkMuted, fontSize = 11.sp)
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = TextMutedSlate,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onSearchChange("") }
                        )
                    }
                }
            }

            // Navigation Actions + IST Live Clock
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, CtcCardBorder),
                    modifier = Modifier.clickable { onOpenStationHub() }
                ) {
                    Text(
                        text = "🏛️ Nashik Hub",
                        color = AiCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F2537),
                    border = BorderStroke(1.dp, AiCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onOpenControlRoom() }
                ) {
                    Text(
                        text = "🎛️ DSS Control Room",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // Live IST Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SignalGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SignalGreen))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${istTime.time12} IST",
                            color = SignalGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Live WebSocket Status Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (connectionStatus == ServerConnectionStatus.ONLINE_STREAMING) SignalGreen.copy(alpha = 0.15f) else CautionAmber.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (connectionStatus == ServerConnectionStatus.ONLINE_STREAMING) SignalGreen.copy(alpha = 0.4f) else CautionAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (connectionStatus == ServerConnectionStatus.ONLINE_STREAMING) SignalGreen else CautionAmber)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (connectionStatus == ServerConnectionStatus.ONLINE_STREAMING) "WS LIVE (8080)" else "LOCAL SIM",
                            color = if (connectionStatus == ServerConnectionStatus.ONLINE_STREAMING) SignalGreen else CautionAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Train Picker Horizontal Strip
 */
@Composable
private fun CtcTrainSelectorStrip(
    trains: List<TrainJourney>,
    selectedIndex: Int,
    onSelectTrain: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CtcPanelBg)
            .border(BorderStroke(1.dp, CtcCardBorder))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ACTIVE CORRIDOR TRAINS:", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)

        trains.forEachIndexed { idx, train ->
            val isSelected = idx == selectedIndex
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) Color(0xFF1E3A8A) else CtcCardBg,
                border = BorderStroke(1.dp, if (isSelected) AiBlue else CtcCardBorder),
                modifier = Modifier.clickable { onSelectTrain(idx) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = train.trainNumber,
                        color = if (isSelected) RailwayGold else TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = train.trainName,
                        color = if (isSelected) TextWhite else TextMutedSlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 2. Live Train Overview Hero Card
 */
@Composable
private fun LiveTrainOverviewCard(
    train: TrainJourney,
    currentStop: ScheduleStop,
    nextStop: ScheduleStop,
    speedKmph: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = train.trainNumber,
                            color = RailwayGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = train.trainName,
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${train.source.name} (${train.source.code}) ➔ ${train.destination.name} (${train.destination.code})",
                            color = TextMutedSlate,
                            fontSize = 11.sp
                        )
                    }
                }

                // Speed & Interlocking Badge
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SignalGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "⚡ $speedKmph km/h",
                            color = SignalGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, CtcCardBorder)
                    ) {
                        Text(
                            text = "TRACK: DOWN MAIN",
                            color = TextMutedSlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = CtcCardBorder)

            // Current Location & Next Destination Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AiCyan))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LAST CLEARED: ${currentStop.station.name} (${currentStop.station.code}) • Platform ${(currentStop.station.code.hashCode().let { if (it < 0) -it else it } % 3 + 1)}",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "NEXT STOP: ${nextStop.station.name} (${nextStop.station.code}) • Dist: 18 km",
                    color = AiBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 3. The 4-Box Delay Cascade Grid
 * Requested UX:
 * CURRENT DELAY: +18 min
 * PREDICTED AT NEXT STATION: +21 min
 * EXPECTED FINAL IMPACT: +27 min
 * CONFIDENCE: 87%
 */
@Composable
private fun FourBoxDelayCascadeGrid(
    currentDelay: Int,
    nextStationDelay: Int,
    finalImpactDelay: Int,
    confidencePct: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Box 1: Current Delay
        DelayBoxCard(
            modifier = Modifier.weight(1f),
            label = "CURRENT DELAY",
            value = if (currentDelay >= 0) "+$currentDelay min" else "$currentDelay min",
            subtext = "At present block section",
            accentColor = if (currentDelay > 15) DangerRed else CautionAmber
        )

        // Box 2: Predicted at Next Station
        DelayBoxCard(
            modifier = Modifier.weight(1f),
            label = "PREDICTED AT NEXT STATION",
            value = if (nextStationDelay >= 0) "+$nextStationDelay min" else "$nextStationDelay min",
            subtext = "Arrival after headway slack",
            accentColor = CautionAmber
        )

        // Box 3: Expected Final Impact
        DelayBoxCard(
            modifier = Modifier.weight(1f),
            label = "EXPECTED FINAL IMPACT",
            value = if (finalImpactDelay >= 0) "+$finalImpactDelay min" else "$finalImpactDelay min",
            subtext = "Cumulative destination impact",
            accentColor = if (finalImpactDelay > 20) DangerRed else CautionAmber
        )

        // Box 4: Confidence Score
        DelayBoxCard(
            modifier = Modifier.weight(1f),
            label = "CONFIDENCE",
            value = "$confidencePct%",
            subtext = "Quantile Regressor (p90: 94%)",
            accentColor = SignalGreen
        )
    }
}

@Composable
private fun DelayBoxCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtext: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                color = TextDarkMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtext,
                color = TextMutedSlate,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 4. "WHY ETA CHANGED" Explainability Card
 */
@Composable
private fun WhyEtaChangedExplainabilityCard(
    currentDelay: Int,
    train: TrainJourney,
    currentStop: ScheduleStop
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16233B),
        border = BorderStroke(1.dp, Color(0xFF253959)),
        modifier = Modifier.fillMaxWidth()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WHY ETA CHANGED",
                        color = RailwayGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, CtcCardBorder)
                ) {
                    Text(
                        text = "SHAP Feature Attribution",
                        color = AiCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Primary requested narrative:
            Text(
                text = "\"Current delay increased and downstream congestion is detected.\"",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Waterfall Root-Cause Decomposition Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExplainabilityFactorChip(
                    modifier = Modifier.weight(1f),
                    icon = "⛰️",
                    cause = "Thal Ghat Banking Detachment",
                    delta = "+11 min",
                    isNegative = true
                )
                ExplainabilityFactorChip(
                    modifier = Modifier.weight(1f),
                    icon = "🚦",
                    cause = "Downstream Headway (Trailing 22223)",
                    delta = "+6 min",
                    isNegative = true
                )
                ExplainabilityFactorChip(
                    modifier = Modifier.weight(1f),
                    icon = "⚡",
                    cause = "Timetable Slack Recovery Buffer",
                    delta = "-4 min",
                    isNegative = false
                )
            }
        }
    }
}

@Composable
private fun ExplainabilityFactorChip(
    modifier: Modifier = Modifier,
    icon: String,
    cause: String,
    delta: String,
    isNegative: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0D1829),
        border = BorderStroke(1.dp, Color(0xFF1E2F4A)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(icon, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = cause,
                    color = TextMutedSlate,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = delta,
                color = if (isNegative) DangerRed else SignalGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * 5. Station-Wise Dynamic ETA Table
 */
@Composable
private fun StationWiseDynamicEtaTable(
    predictions: List<StopPrediction>,
    currentStopIndex: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATION-WISE DYNAMIC ETA & HEADWAY SCHEDULE",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "p10 • p50 • p90 Quantiles",
                    color = AiCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1A2C))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("STATION", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("PF / LINE", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("SCHED", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("PREDICTED ETA", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("DELTA", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("STATUS", color = TextDarkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            }

            // Table Rows
            predictions.forEachIndexed { idx, pred ->
                val isPast = idx < currentStopIndex
                val isCurrent = idx == currentStopIndex
                val isNext = idx == currentStopIndex + 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isCurrent -> Color(0xFF1E3A8A).copy(alpha = 0.35f)
                                isNext -> Color(0xFF0F2537)
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Station Code & Name
                    Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (pred.stop.station.isJunction) Color(0xFFB45309) else Color(0xFF1E293B)
                        ) {
                            Text(
                                text = pred.stop.station.code,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pred.stop.station.name,
                            color = if (isCurrent) Color.White else if (isPast) TextDarkMuted else TextWhite,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Platform & Line
                    Text(
                        text = "PF ${(pred.stop.station.code.hashCode().let { if (it < 0) -it else it } % 3 + 1)} (Main)",
                        color = TextMutedSlate,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1.2f)
                    )

                    // Scheduled
                    Text(
                        text = pred.stop.scheduledArrival.toStandard12Hour(),
                        color = TextMutedSlate,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1.2f)
                    )

                    // Predicted ETA (p50 with p10-p90 spread)
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = pred.predictedEta.toStandard12Hour(),
                            color = if (pred.delayMinutes > 15) DangerRed else CautionAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "p10-p90: ±2m",
                            color = TextDarkMuted,
                            fontSize = 9.sp
                        )
                    }

                    // Delta
                    Text(
                        text = if (pred.delayMinutes >= 0) "+${pred.delayMinutes}m" else "${pred.delayMinutes}m",
                        color = if (pred.delayMinutes > 15) DangerRed else CautionAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    // Status Flag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            isPast -> Color(0xFF1E293B)
                            isCurrent -> Color(0xFF2563EB)
                            isNext -> Color(0xFF059669)
                            else -> Color(0xFF131F33)
                        },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(
                            text = when {
                                isPast -> "DEPARTED"
                                isCurrent -> "AT STATION"
                                isNext -> "APPROACHING"
                                else -> "ON SCHEDULE"
                            },
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 6. Delay Propagation Timeline Visualization
 */
@Composable
private fun DelayPropagationTimelineCard(
    predictions: List<StopPrediction>,
    currentStopIndex: Int
) {
    val currentStop = predictions.getOrNull(currentStopIndex)
    val observedDelay = currentStop?.delayMinutes ?: 18
    val downstreamStops = predictions.filterIndexed { index, _ -> index > currentStopIndex }
    val maxDownstreamDelay = downstreamStops.maxOfOrNull { it.delayMinutes } ?: observedDelay
    val finalStop = predictions.lastOrNull()

    val severity = when {
        maxDownstreamDelay >= 30 -> DelaySeverity.CRITICAL
        maxDownstreamDelay >= 20 -> DelaySeverity.HIGH
        maxDownstreamDelay >= 10 -> DelaySeverity.MODERATE
        else -> DelaySeverity.LOW
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header with title and severity badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DELAY PROPAGATION ENGINE (CORRIDOR CASCADE)",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Multi-station delay trajectory • Dwell inflation & single-track contention",
                        color = TextMutedSlate,
                        fontSize = 10.sp
                    )
                }

                // Severity Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(severity.colorHex).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(severity.colorHex).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "SEVERITY: ${severity.label.uppercase()}",
                        color = Color(severity.colorHex),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // High-Impact Distinction Pills: OBSERVED vs PREDICTED DOWNSTREAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // OBSERVED DELAY PILL
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F2338),
                    border = BorderStroke(1.dp, AiCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AiCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OBSERVED DELAY",
                                color = AiCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "+$observedDelay min",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "At ${currentStop?.stop?.station?.name ?: "Current Station"}",
                            color = TextMutedSlate,
                            fontSize = 9.sp
                        )
                    }
                }

                // PREDICTED DOWNSTREAM DELAY PILL
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF26180E),
                    border = BorderStroke(1.dp, CautionAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CautionAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PREDICTED DOWNSTREAM",
                                color = CautionAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "+${finalStop?.delayMinutes ?: (observedDelay + 9)} min",
                            color = CautionAmber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "At ${finalStop?.stop?.station?.name ?: "Destination"}",
                            color = TextMutedSlate,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Timeline Route Progression Arrow Chips
            Text(
                text = "ROUTE DELAY PROGRESSION TIMELINE",
                color = TextMutedSlate,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                predictions.forEachIndexed { idx, pred ->
                    val isPast = idx < currentStopIndex
                    val isCurrent = idx == currentStopIndex
                    val isDownstream = idx > currentStopIndex

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isCurrent -> AiCyan.copy(alpha = 0.15f)
                            isDownstream -> Color(0xFF1E293B)
                            else -> Color(0xFF0F172A)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isCurrent -> AiCyan
                                isDownstream -> CautionAmber.copy(alpha = 0.4f)
                                else -> Color(0xFF334155)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pred.stop.station.code,
                                color = if (isCurrent) AiCyan else TextWhite,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isPast -> "OK"
                                    isCurrent -> "+${pred.delayMinutes}m [OBS]"
                                    else -> "+${pred.delayMinutes}m"
                                },
                                color = when {
                                    isPast -> SignalGreen
                                    isCurrent -> AiCyan
                                    pred.delayMinutes > observedDelay -> CautionAmber
                                    else -> SignalGreen
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (idx < predictions.lastIndex) {
                        Text(
                            text = "➔",
                            color = TextMutedSlate,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Bar Chart Visualization with clear visual distinction
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(Color(0xFF09111E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                predictions.forEachIndexed { idx, pred ->
                    val delayVal = pred.delayMinutes.coerceAtLeast(0)
                    val barHeight = ((delayVal.toFloat() / 35f) * 44f).coerceIn(4f, 44f).dp
                    val isPast = idx < currentStopIndex
                    val isCurrent = idx == currentStopIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isPast) "-" else "+$delayVal",
                            color = when {
                                isPast -> Color(0xFF64748B)
                                isCurrent -> AiCyan
                                else -> CautionAmber
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(if (isPast) 4.dp else barHeight)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(
                                    when {
                                        isPast -> Color(0xFF334155)
                                        isCurrent -> AiCyan
                                        pred.delayMinutes >= 20 -> DangerRed
                                        else -> CautionAmber
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = pred.stop.station.code,
                            color = if (isCurrent) AiCyan else TextMutedSlate,
                            fontSize = 8.sp,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Key operational causes & factor breakdown footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Affected Stations: ${downstreamStops.count { it.delayMinutes >= 5 }} | Confidence: 88%",
                    color = TextMutedSlate,
                    fontSize = 9.sp
                )
                Text(
                    text = "Dwell Rush (+2m) • Single Track (+4m) • Slack (-3m)",
                    color = CautionAmber.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 7. Route Map & Topology Card
 */
@Composable
private fun RouteMapTopologyCard(
    train: TrainJourney,
    currentStopIndex: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CORRIDOR ROUTE MAP & TRACK TOPOLOGY",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Double Line Auto-Signaled",
                    color = SignalGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Schematic Railway Track Drawing
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                val width = size.width
                val y = size.height / 2f
                val stationsCount = train.stops.size
                val stepX = width / (stationsCount - 1).coerceAtLeast(1)

                // Track Bed Lines (Double Track)
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, y - 4f),
                    end = Offset(width, y - 4f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, y + 4f),
                    end = Offset(width, y + 4f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                // Active Section Highlight
                val activeX = currentStopIndex * stepX
                drawLine(
                    color = Color(0xFF2563EB),
                    start = Offset(0f, y),
                    end = Offset(activeX, y),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )

                // Station Dots
                for (i in 0 until stationsCount) {
                    val cx = i * stepX
                    val isPast = i < currentStopIndex
                    val isCur = i == currentStopIndex

                    drawCircle(
                        color = if (isCur) AiCyan else if (isPast) Color(0xFF64748B) else Color(0xFF334155),
                        radius = if (isCur) 8f else 5f,
                        center = Offset(cx, y)
                    )
                    if (isCur) {
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = Offset(cx, y)
                        )
                    }
                }
            }

            // Station Labels below canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                train.stops.forEachIndexed { i, stop ->
                    Text(
                        text = stop.station.code,
                        color = if (i == currentStopIndex) AiCyan else TextDarkMuted,
                        fontSize = 9.sp,
                        fontWeight = if (i == currentStopIndex) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 8. Interactive What-If Scenario Sandbox
 */
@Composable
private fun WhatIfScenarioSandboxCard(
    isWhatIfActive: Boolean,
    onToggleWhatIf: (Boolean) -> Unit,
    holdMinutes: Float,
    onHoldChange: (Float) -> Unit,
    baseDelay: Int,
    recalculatedFinal: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF122036),
        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎛️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WHAT-IF SCENARIO SIMULATOR",
                        color = RailwayGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Switch(
                    checked = isWhatIfActive,
                    onCheckedChange = onToggleWhatIf,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SignalGreen,
                        uncheckedThumbColor = TextDarkMuted,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Text(
                text = "Simulate dispatching actions: Hold train at Devlali Loop Line to let Vande Bharat overtake.",
                color = TextMutedSlate,
                fontSize = 11.sp
            )

            if (isWhatIfActive) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Simulated Regulation Hold:", color = TextWhite, fontSize = 11.sp)
                        Text("+${holdMinutes.toInt()} min", color = CautionAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = holdMinutes,
                        onValueChange = onHoldChange,
                        valueRange = 0f..20f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = RailwayGold,
                            activeTrackColor = RailwayGold,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )

                    // Real-time Impact Delta Preview
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0C1726),
                        border = BorderStroke(1.dp, Color(0xFF1A2B42)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DOWNSTREAM CORRIDOR IMPACT", color = TextDarkMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (holdMinutes > 5) "Corridor Delay Saved: 19 min (Overtake Successful)"
                                    else "Delay Increased: Downstream bottleneck",
                                    color = if (holdMinutes > 5) SignalGreen else CautionAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Final: +$recalculatedFinal min",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Toggle switch to inject regulation actions or caution orders.",
                    color = TextDarkMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 9. Historical vs Predicted Run Time Comparison
 */
@Composable
private fun HistoricalVsPredictedCard(
    scheduledMinutes: Int,
    historicalTypicalMinutes: Int,
    todayPredictedMinutes: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "HISTORICAL VS PREDICTED ARRIVAL TRAJECTORY",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoricalBarItem(
                    modifier = Modifier.weight(1f),
                    label = "TIMETABLE RUN",
                    timeStr = "${scheduledMinutes / 60}h ${scheduledMinutes % 60}m",
                    barColor = SignalGreen
                )
                HistoricalBarItem(
                    modifier = Modifier.weight(1f),
                    label = "HISTORICAL AVG",
                    timeStr = "${historicalTypicalMinutes / 60}h ${historicalTypicalMinutes % 60}m",
                    barColor = Color(0xFF38BDF8)
                )
                HistoricalBarItem(
                    modifier = Modifier.weight(1f),
                    label = "TODAY'S AI PREDICTED",
                    timeStr = "${todayPredictedMinutes / 60}h ${todayPredictedMinutes % 60}m",
                    barColor = DangerRed
                )
            }
        }
    }
}

@Composable
private fun HistoricalBarItem(
    modifier: Modifier = Modifier,
    label: String,
    timeStr: String,
    barColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F1A2A),
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, color = TextDarkMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(timeStr, color = barColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * 10. Network Operational Status & Alerts Card
 */
@Composable
private fun NetworkAndAlertsCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETWORK STATUS & ACTIVE ALERTS",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SignalGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "INTERLOCKING NORMAL",
                        color = SignalGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Alert Item 1: 12109 Panchavati Express
            AlertRowItem(
                badgeColor = DangerRed,
                title = "🚆 Train 12109 Panchavati Exp • Restrictive Signal (+18m)",
                desc = "Facing restrictive double-yellow aspect at Devlali outer. Preceding freight train clearing Odha block."
            )

            // Alert Item 2: 12859 Gitanjali Express
            AlertRowItem(
                badgeColor = DangerRed,
                title = "🚆 Train 12859 Gitanjali Exp • Platform 2 Loop Hold (+22m)",
                desc = "Superfast 12859 regulated at Nashik Road loop line to grant clear through path for down mail."
            )

            // Alert Item 3: 22223 Vande Bharat
            AlertRowItem(
                badgeColor = CautionAmber,
                title = "🚆 Train 22223 Vande Bharat • OHE Voltage Dip (+8m)",
                desc = "Automated pantograph cycle engaged near Ghoti neutral section; speed regulated to 75 km/h."
            )

            // Alert Item 4: 12137 Punjab Mail
            AlertRowItem(
                badgeColor = CautionAmber,
                title = "🚆 Train 12137 Punjab Mail • Thal Ghat Banker Queue (+14m)",
                desc = "Triple WAG-9 banking locomotive detachment queue at Igatpuri siding after 1:37 ghat climb."
            )

            // Alert Item 5: BOXN-6022 Agro Freight
            AlertRowItem(
                badgeColor = CautionAmber,
                title = "🚆 Rake BOXN-6022 Freight • Lahavit Siding Regulation (+25m)",
                desc = "58 loaded grain/onion wagons held on Lahavit loop line to clear passenger express corridor."
            )
        }
    }
}

@Composable
private fun AlertRowItem(
    badgeColor: Color,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F1A2A),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(top = 4.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = TextMutedSlate, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 11. Real-Time Corridor WebSocket Feed Card (Live Actual Movements & Delay Events)
 */
@Composable
private fun RealTimeCorridorWebSocketFeedCard(
    connectionStatus: ServerConnectionStatus,
    recentMovements: List<LiveStreamMessage.ActualMovement>,
    recentDelayEvents: List<LiveStreamMessage.DelayEvent>,
    autoSync: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    onSimulateMovement: () -> Unit,
    onSimulateDelayEvent: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Actual Movements, 1 = Delay Events

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CtcCardBg,
        border = BorderStroke(1.dp, CtcCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title + WS Status + Auto-Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REAL-TIME WEBSOCKET PUSH STREAM",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (autoSync) SignalGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, if (autoSync) SignalGreen else TextDarkMuted),
                        modifier = Modifier.clickable { onToggleAutoSync(!autoSync) }
                    ) {
                        Text(
                            text = if (autoSync) "SYNC: ON" else "SYNC: OFF",
                            color = if (autoSync) SignalGreen else TextDarkMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Interactive Trigger Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E2D4A),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).clickable { onSimulateMovement() }
                ) {
                    Text(
                        text = "▶ Push Movement",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2E1A2A),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).clickable { onSimulateDelayEvent() }
                ) {
                    Text(
                        text = "⚡ Inject Delay Event",
                        color = DangerRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }

            // Sub-Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (activeTab == 0) Color(0xFF1E293B) else Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (activeTab == 0) SignalGreen else CtcCardBorder),
                    modifier = Modifier.weight(1f).clickable { activeTab = 0 }
                ) {
                    Text(
                        text = "Actual Movements (${recentMovements.size})",
                        color = if (activeTab == 0) SignalGreen else TextMutedSlate,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (activeTab == 1) Color(0xFF1E293B) else Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (activeTab == 1) CautionAmber else CtcCardBorder),
                    modifier = Modifier.weight(1f).clickable { activeTab = 1 }
                ) {
                    Text(
                        text = "Delay Events (${recentDelayEvents.size})",
                        color = if (activeTab == 1) CautionAmber else TextMutedSlate,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // List of Events
            if (activeTab == 0) {
                if (recentMovements.isEmpty()) {
                    Text("Listening for live station arrivals/departures...", color = TextDarkMuted, fontSize = 11.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentMovements.take(4).forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0B1322),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF1E3A8A)
                                        ) {
                                            Text(
                                                text = m.trainNumber,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "${m.stationName} (${m.stationCode}) • PF ${m.platformUsed}",
                                                color = TextWhite,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Arr: ${m.actualArrival} • Dep: ${m.actualDeparture}",
                                                color = TextDarkMuted,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (m.observedDelayMinutes > 15) DangerRed.copy(alpha = 0.2f) else CautionAmber.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "+${m.observedDelayMinutes}m",
                                            color = if (m.observedDelayMinutes > 15) DangerRed else CautionAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (recentDelayEvents.isEmpty()) {
                    Text("No active corridor delay events detected.", color = TextDarkMuted, fontSize = 11.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentDelayEvents.take(4).forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1A121E),
                                border = BorderStroke(1.dp, Color(0xFF3B1E2B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CautionAmber.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = d.primaryReasonCategory,
                                                    color = CautionAmber,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Train ${d.trainNumber} @ ${d.stationCode}",
                                                color = TextWhite,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "+${d.delayDurationMinutes} min",
                                            color = DangerRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    Text(
                                        text = d.detailedExplanation,
                                        color = TextMutedSlate,
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
