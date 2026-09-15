package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.getCurrentEpochMillis
import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.engine.DelayPropagationEngine
import org.example.project.engine.HeuristicEtaPredictor
import org.example.project.engine.IEtaPredictor
import org.example.project.engine.QuantileEtaPredictor
import org.example.project.model.IndianStandardTime
import org.example.project.model.IstTime
import org.example.project.model.ScheduleStop
import org.example.project.model.StopPrediction
import org.example.project.model.TrainJourney
import org.example.project.network.RailNexusLiveStreamClient
import org.example.project.network.ServerConnectionStatus
import org.example.project.ui.theme.*

enum class MainTab(val title: String, val icon: String) {
    TRACKER("Live Status", "🚆"),
    NASHIK_HUB("Nashik Hub", "🏛️"),
    FIND_PNR("Find & PNR", "🔍"),
    RADAR("Connections", "🎯"),
    CORRIDOR("Analytics", "📊"),
    DISPATCHER("Dispatcher (P4)", "🎛️")
}

@Composable
fun LiveTrackerDashboard(
    onBackToControlRoom: (() -> Unit)? = null
) {
    val allTrains = remember { NashikStationData.allNashikTrains }
    var selectedTrainIndex by remember { mutableStateOf(0) }
    val currentTrain = allTrains[selectedTrainIndex]

    var currentTab by remember { mutableStateOf(MainTab.TRACKER) }

    // Interactive journey progression: Train's current station index along its route
    var currentStationIndex by remember(currentTrain) {
        mutableStateOf(1.coerceAtMost(currentTrain.stops.size - 1))
    }

    // Dynamic delay simulation state
    var simulatedDelayMinutes by remember { mutableStateOf(18f) }
    var expandedStopIndex by remember { mutableStateOf<Int?>(null) }
    var stationFilterQuery by remember { mutableStateOf("") }
    var junctionsOnlyFilter by remember { mutableStateOf(false) }

    // Live Real-Time Indian Standard Time (IST) Engine
    var currentEpochMillis by remember { mutableStateOf(getCurrentEpochMillis()) }
    val currentIst = remember(currentEpochMillis) { IndianStandardTime.fromEpochMillis(currentEpochMillis) }

    var isAutoSimulating by remember { mutableStateOf(false) }
    var simulatedSpeed by remember { mutableStateOf(112) }
    var isAlarmArmed by remember { mutableStateOf(false) }
    var activePredictor by remember { mutableStateOf<IEtaPredictor>(QuantileEtaPredictor()) }

    // Ticking real-time engine: ticks every second synchronized to true IST
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentEpochMillis = getCurrentEpochMillis()
            val second = ((currentEpochMillis + IndianStandardTime.IST_OFFSET_MILLIS) / 1000 % 60).toInt()
            // Natural dynamic speed fluctuation (104 - 118 km/h)
            simulatedSpeed = (108 + (second % 9) - 4).coerceIn(95, 130)
        }
    }

    // Auto-simulation progression: advances train to next station every 7 seconds when active
    LaunchedEffect(isAutoSimulating, currentTrain) {
        if (isAutoSimulating) {
            while (isAutoSimulating) {
                delay(7000)
                if (currentStationIndex < currentTrain.stops.size - 1) {
                    currentStationIndex++
                } else {
                    currentStationIndex = 0 // loop journey
                }
            }
        }
    }

    val liveClockStr = remember(currentIst) {
        "${currentIst.time12} IST"
    }
    val liveClockFullStr = remember(currentIst) {
        "${currentIst.dateStr} • ${currentIst.time12} IST"
    }

    // Next stop along route
    val nextStopIndex = (currentStationIndex + 1).coerceAtMost(currentTrain.stops.size - 1)
    val nextStop = currentTrain.stops[nextStopIndex]

    // Compute dynamic ETA predictions based on current station and delay
    val predictions = remember(currentTrain, currentStationIndex, simulatedDelayMinutes) {
        DelayPropagationEngine.predictJourney(
            journey = currentTrain,
            currentStopIndex = currentStationIndex,
            currentDelayMinutes = simulatedDelayMinutes.toInt()
        )
    }

    val filteredPredictions = remember(predictions, stationFilterQuery, junctionsOnlyFilter) {
        predictions.filter { pred ->
            val matchesQuery = stationFilterQuery.isEmpty() ||
                    pred.stop.station.name.contains(stationFilterQuery, ignoreCase = true) ||
                    pred.stop.station.code.contains(stationFilterQuery, ignoreCase = true)
            val matchesJunction = !junctionsOnlyFilter || pred.stop.station.isJunction
            matchesQuery && matchesJunction
        }
    }

    val currentStop = currentTrain.stops[currentStationIndex]
    val destinationPrediction = predictions.lastOrNull()

    // Fully responsive container: dynamically switches between Compact, Medium, and Expanded
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RailDark)
    ) {
        val isWide = maxWidth >= 900.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .align(Alignment.TopCenter)
        ) {
            // 1. Adaptive Header: Includes top navigation & live clock on desktop
            AdaptiveAppHeader(
                currentTab = currentTab,
                onSelectTab = { currentTab = it },
                isWide = isWide,
                liveClock = liveClockFullStr,
                liveClockCompact = liveClockStr,
                onOpenSearch = { currentTab = MainTab.FIND_PNR },
                onBackToControlRoom = onBackToControlRoom
            )

            // 2. Train Switcher Strip (Only when Tracker, Radar, or Analytics is active)
            if (currentTab == MainTab.TRACKER || currentTab == MainTab.RADAR || currentTab == MainTab.CORRIDOR) {
                TrainPickerRow(
                    trains = allTrains,
                    selectedIndex = selectedTrainIndex,
                    onSelect = {
                        selectedTrainIndex = it
                        currentStationIndex = 1.coerceAtMost(allTrains[it].stops.size - 1)
                        expandedStopIndex = null
                        stationFilterQuery = ""
                    }
                )
            }

            // 3. Tab Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    MainTab.TRACKER -> {
                        if (isWide) {
                            // Dual-Column Cockpit Layout for Desktop / Laptop
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // Left Column: Live Telemetry, Simulator & Arrival ETA Card
                                Column(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Live Telemetry Cockpit Card (Speedometer, Countdown, Active Simulation Controls)
                                    LiveTelemetryCockpitCard(
                                        train = currentTrain,
                                        currentStop = currentStop,
                                        nextStop = nextStop,
                                        speed = simulatedSpeed,
                                        currentIst = currentIst,
                                        isAutoSimulating = isAutoSimulating,
                                        onToggleAutoSimulate = { isAutoSimulating = !isAutoSimulating },
                                        canStepPrev = currentStationIndex > 0,
                                        canStepNext = currentStationIndex < currentTrain.stops.size - 1,
                                        onStepPrev = { if (currentStationIndex > 0) currentStationIndex-- },
                                        onStepNext = { if (currentStationIndex < currentTrain.stops.size - 1) currentStationIndex++ },
                                        onResetJourney = { currentStationIndex = 0 }
                                    )

                                    // Phase 5: Indian Railways Multi-Lingual Station PA Announcement System
                                    val liveStationEntry = remember(currentTrain, simulatedDelayMinutes) {
                                        NashikStationData.liveStationTrains.find { it.trainNumber == currentTrain.trainNumber }
                                            ?: NashikStationData.liveStationTrains.first()
                                    }
                                    LiveStationAnnouncementCard(
                                        train = liveStationEntry.copy(delayMinutes = simulatedDelayMinutes.toInt())
                                    )

                                    // Phase 5: Digital Boarding Pass & QR Journey Card
                                    DigitalBoardingPassCard(
                                        train = currentTrain,
                                        currentDelayMinutes = simulatedDelayMinutes.toInt()
                                    )

                                    // RailNexus Machine Learning AI Cockpit (Quantile Intervals & Explainability)
                                    RailNexusAiCockpitCard(
                                        journey = currentTrain,
                                        currentStopIndex = currentStationIndex,
                                        speedKmH = simulatedSpeed.toDouble(),
                                        currentDelayMinutes = simulatedDelayMinutes.toInt(),
                                        currentEpochMillis = currentEpochMillis,
                                        activePredictor = activePredictor,
                                        onSwitchPredictor = { activePredictor = it }
                                    )

                                    // Destination Arrival ETA & Slack Recovery Card
                                    destinationPrediction?.let { dest ->
                                        ClearDestinationEtaCard(
                                            train = currentTrain,
                                            destinationPred = dest,
                                            currentStopName = currentStop.station.name,
                                            currentDelay = simulatedDelayMinutes.toInt(),
                                            isAlarmArmed = isAlarmArmed,
                                            onToggleAlarm = { isAlarmArmed = !isAlarmArmed }
                                        )
                                    }

                                    // Interactive What-If Delay Simulator
                                    InteractiveDelayDemoCard(
                                        delayMinutes = simulatedDelayMinutes,
                                        onDelayChange = { simulatedDelayMinutes = it }
                                    )

                                    // Public Station Announcement Marquee
                                    StationAnnouncementBanner(
                                        trainNumber = currentTrain.trainNumber,
                                        trainName = currentTrain.trainName,
                                        currentStationName = currentStop.station.name,
                                        delayMinutes = simulatedDelayMinutes.toInt()
                                    )
                                }

                                // Right Column: Interactive Station Timeline & Coach Guide
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    TimelineFilterBar(
                                        query = stationFilterQuery,
                                        onQueryChange = { stationFilterQuery = it },
                                        junctionsOnly = junctionsOnlyFilter,
                                        onToggleJunctions = { junctionsOnlyFilter = !junctionsOnlyFilter },
                                        totalStops = currentTrain.stops.size,
                                        currentStopIdx = currentStationIndex
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    StationTimelineList(
                                        predictions = filteredPredictions,
                                        expandedIndex = expandedStopIndex,
                                        onToggleExpand = {
                                             expandedStopIndex = if (expandedStopIndex == it) null else it
                                        },
                                        onSelectCurrentStation = { clickedIdx ->
                                            currentStationIndex = clickedIdx
                                        }
                                    )
                                }
                            }
                        } else {
                            // Unified Single-Column Layout for Mobile / Compact Tablets
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    LiveTelemetryCockpitCard(
                                        train = currentTrain,
                                        currentStop = currentStop,
                                        nextStop = nextStop,
                                        speed = simulatedSpeed,
                                        currentIst = currentIst,
                                        isAutoSimulating = isAutoSimulating,
                                        onToggleAutoSimulate = { isAutoSimulating = !isAutoSimulating },
                                        canStepPrev = currentStationIndex > 0,
                                        canStepNext = currentStationIndex < currentTrain.stops.size - 1,
                                        onStepPrev = { if (currentStationIndex > 0) currentStationIndex-- },
                                        onStepNext = { if (currentStationIndex < currentTrain.stops.size - 1) currentStationIndex++ },
                                        onResetJourney = { currentStationIndex = 0 }
                                    )
                                }

                                item {
                                    // Phase 5: Live Station PA Announcement System (Mobile)
                                    val liveStationEntry = remember(currentTrain, simulatedDelayMinutes) {
                                        NashikStationData.liveStationTrains.find { it.trainNumber == currentTrain.trainNumber }
                                            ?: NashikStationData.liveStationTrains.first()
                                    }
                                    LiveStationAnnouncementCard(
                                        train = liveStationEntry.copy(delayMinutes = simulatedDelayMinutes.toInt())
                                    )
                                }

                                item {
                                    // Phase 5: Digital Boarding Pass & QR Journey Card (Mobile)
                                    DigitalBoardingPassCard(
                                        train = currentTrain,
                                        currentDelayMinutes = simulatedDelayMinutes.toInt()
                                    )
                                }

                                item {
                                    // RailNexus Machine Learning AI Cockpit (Quantile Intervals & Explainability)
                                    RailNexusAiCockpitCard(
                                        journey = currentTrain,
                                        currentStopIndex = currentStationIndex,
                                        speedKmH = simulatedSpeed.toDouble(),
                                        currentDelayMinutes = simulatedDelayMinutes.toInt(),
                                        currentEpochMillis = currentEpochMillis,
                                        activePredictor = activePredictor,
                                        onSwitchPredictor = { activePredictor = it }
                                    )
                                }

                                item {
                                    destinationPrediction?.let { dest ->
                                        ClearDestinationEtaCard(
                                            train = currentTrain,
                                            destinationPred = dest,
                                            currentStopName = currentStop.station.name,
                                            currentDelay = simulatedDelayMinutes.toInt(),
                                            isAlarmArmed = isAlarmArmed,
                                            onToggleAlarm = { isAlarmArmed = !isAlarmArmed }
                                        )
                                    }
                                }

                                item {
                                    InteractiveDelayDemoCard(
                                        delayMinutes = simulatedDelayMinutes,
                                        onDelayChange = { simulatedDelayMinutes = it }
                                    )
                                }

                                item {
                                    TimelineFilterBar(
                                        query = stationFilterQuery,
                                        onQueryChange = { stationFilterQuery = it },
                                        junctionsOnly = junctionsOnlyFilter,
                                        onToggleJunctions = { junctionsOnlyFilter = !junctionsOnlyFilter },
                                        totalStops = currentTrain.stops.size,
                                        currentStopIdx = currentStationIndex
                                    )
                                }

                                itemsIndexed(filteredPredictions) { index, prediction ->
                                    CleanStationRow(
                                        prediction = prediction,
                                        isFirst = index == 0,
                                        isLast = index == filteredPredictions.lastIndex,
                                        isExpanded = expandedStopIndex == index,
                                        onToggle = {
                                            expandedStopIndex = if (expandedStopIndex == index) null else index
                                        },
                                        onSetAsCurrent = {
                                            val realIdx = currentTrain.stops.indexOfFirst { it.station.code == prediction.stop.station.code }
                                            if (realIdx != -1) currentStationIndex = realIdx
                                        }
                                    )
                                }
                            }
                        }
                    }

                    MainTab.NASHIK_HUB -> {
                        NashikCityHubScreen(
                            onTrackTrain = { selectedTrain ->
                                val idx = allTrains.indexOfFirst { it.trainNumber == selectedTrain.trainNumber }
                                if (idx != -1) {
                                    selectedTrainIndex = idx
                                    currentStationIndex = 1.coerceAtMost(allTrains[idx].stops.size - 1)
                                }
                                currentTab = MainTab.TRACKER
                            }
                        )
                    }

                    MainTab.FIND_PNR -> {
                        PnrAndRouteFinderScreen(
                            onSelectTrainAndTrack = { selectedTrain ->
                                val idx = allTrains.indexOfFirst { it.trainNumber == selectedTrain.trainNumber }
                                if (idx != -1) {
                                    selectedTrainIndex = idx
                                    currentStationIndex = 1.coerceAtMost(allTrains[idx].stops.size - 1)
                                }
                                currentTab = MainTab.TRACKER
                            }
                        )
                    }

                    MainTab.RADAR -> {
                        ConnectionRadarScreen(
                            currentTrain = currentTrain,
                            predictions = predictions,
                            simulatedDelayMinutes = simulatedDelayMinutes
                        )
                    }

                    MainTab.CORRIDOR -> {
                        CorridorAnalyticsScreen(
                            currentTrain = currentTrain,
                            predictions = predictions,
                            simulatedDelayMinutes = simulatedDelayMinutes
                        )
                    }

                    MainTab.DISPATCHER -> {
                        StationMasterDispatcherScreen()
                    }
                }
            }

            // 4. Mobile Bottom Navigation Bar (Shown ONLY when !isWide, sits cleanly at bottom)
            if (!isWide) {
                MobileBottomNavBar(
                    currentTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
            }
        }
    }
}

@Composable
fun AdaptiveAppHeader(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    isWide: Boolean,
    liveClock: String,
    liveClockCompact: String = "",
    onOpenSearch: () -> Unit,
    onBackToControlRoom: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RailCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectTab(MainTab.TRACKER) }
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(RailCyan, RailAmber))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RA",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RailAndaz",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RailTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RailCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("AI ETA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RailCyan)
                        }
                    }
                    Text(
                        text = "Real-Time Train Arrival & Delay Cascade",
                        fontSize = 10.sp,
                        color = RailMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Desktop Navigation Tabs (in Header)
            if (isWide) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RailCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) RailCyan else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectTab(tab) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = tab.icon, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) RailCyan else RailTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Right Status Indicators (Live clock + Live badge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isWide) {
                    Text(
                        text = liveClock,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    if (liveClockCompact.isNotEmpty()) {
                        Text(
                            text = liveClockCompact,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { onOpenSearch() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(text = "🔍 PNR", fontSize = 10.sp, color = RailCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Pulsing Live Indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RailGreen.copy(alpha = 0.15f))
                        .border(1.dp, RailGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(RailGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE IST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailGreen
                    )
                }

                if (onBackToControlRoom != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0B192C))
                            .border(1.dp, RailCyan, RoundedCornerShape(8.dp))
                            .clickable { onBackToControlRoom() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🎛️ Control Room",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = RailCardBorder)
    }
}

@Composable
fun LiveTelemetryCockpitCard(
    train: TrainJourney,
    currentStop: ScheduleStop,
    nextStop: ScheduleStop,
    speed: Int,
    currentIst: IstTime,
    isAutoSimulating: Boolean,
    onToggleAutoSimulate: () -> Unit,
    canStepPrev: Boolean,
    canStepNext: Boolean,
    onStepPrev: () -> Unit,
    onStepNext: () -> Unit,
    onResetJourney: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(RailCyan.copy(alpha = 0.7f), RailGreen.copy(alpha = 0.4f)))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Telemetry Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RailGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVE TELEMETRY • ${currentIst.time12} IST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailCyan,
                        letterSpacing = 0.8.sp
                    )
                }

                // Auto-Simulation Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAutoSimulating) RailGreen.copy(alpha = 0.25f) else Color(0xFF1E293B))
                        .border(1.dp, if (isAutoSimulating) RailGreen else RailCardBorder, RoundedCornerShape(6.dp))
                        .clickable { onToggleAutoSimulate() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAutoSimulating) "⚡ Simulating (Live)" else "▶ Auto-Simulate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutoSimulating) RailGreen else RailTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Metric Live Gauges (Speedometer, Next Stop Countdown, Current Track Section)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gauge 1: Real-Time Speedometer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "CURRENT SPEED", fontSize = 9.sp, color = RailMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$speed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = RailCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "km/h",
                                fontSize = 10.sp,
                                color = RailMuted,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Max limit 130 km/h",
                            fontSize = 9.sp,
                            color = RailTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Gauge 2: Next Stop Countdown (IST Dynamic)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(RailAmber)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "NEXT STOP (IST)", fontSize = 9.sp, color = RailAmber, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val countdownText = currentIst.formatCountdownTo(nextStop.scheduledArrival)
                        Text(
                            text = countdownText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = RailAmber
                        )
                        Text(
                            text = "➔ ${nextStop.scheduledArrival} IST (${nextStop.station.code})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RailTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Gauge 3: Section & Power Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "LOCO & GRADE", fontSize = 9.sp, color = RailMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "25kV Electric",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailGreen,
                            maxLines = 1
                        )
                        Text(
                            text = if (currentStop.station.code == "IGP" || nextStop.station.code == "IGP") "1:37 Thal Ghat Grade" else "Double Track Fast",
                            fontSize = 9.sp,
                            color = RailMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time IST Synchronization Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D1829))
                    .border(1.dp, RailCyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🇮🇳", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "IST: ${currentIst.dateStr} • ${currentIst.time24} (UTC+5:30)",
                        fontSize = 10.sp,
                        color = RailCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "● Master IR Clock",
                    fontSize = 9.sp,
                    color = RailGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            // Active Interactive Journey Step Controller (Simulate train moving stop by stop!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF131D31))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📍 At:", fontSize = 11.sp, color = RailMuted)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentStop.station.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Stop button
                    Button(
                        onClick = onStepPrev,
                        enabled = canStepPrev,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            disabledContainerColor = Color(0xFF151C2A)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "⏪ Prev Stop", fontSize = 10.sp, color = if (canStepPrev) RailCyan else RailMuted)
                    }

                    // Next Stop button
                    Button(
                        onClick = onStepNext,
                        enabled = canStepNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RailCyan,
                            disabledContainerColor = Color(0xFF151C2A)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "⏩ Next Stop", fontSize = 10.sp, color = if (canStepNext) Color.Black else RailMuted, fontWeight = FontWeight.Bold)
                    }

                    // Reset button
                    Button(
                        onClick = onResetJourney,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "🔄", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TrainPickerRow(
    trains: List<TrainJourney>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RailCard)
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NASHIK TRAINS:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = RailCyan,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(end = 4.dp)
        )
        trains.forEachIndexed { index, train ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .widthIn(min = 130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) RailCyan.copy(alpha = 0.2f) else RailCardBorder)
                    .border(
                        1.dp,
                        if (isSelected) RailCyan else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = train.trainNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) RailCyan else RailTextSecondary
                        )
                        if (train.trainType.name == "VANDE_BHARAT") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "⚡", fontSize = 10.sp)
                        }
                    }
                    Text(
                        text = train.trainName.split(" ").take(2).joinToString(" "),
                        fontSize = 10.sp,
                        color = if (isSelected) RailTextPrimary else RailMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    junctionsOnly: Boolean,
    onToggleJunctions: () -> Unit,
    totalStops: Int,
    currentStopIdx: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "STATION TIMELINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RailCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Stop ${currentStopIdx + 1} of $totalStops • Tap station for coach guide & amenities",
                fontSize = 10.sp,
                color = RailMuted
            )
        }

        // Junctions only toggle chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (junctionsOnly) RailCyan.copy(alpha = 0.2f) else RailCard)
                .border(1.dp, if (junctionsOnly) RailCyan else RailCardBorder, RoundedCornerShape(8.dp))
                .clickable { onToggleJunctions() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (junctionsOnly) "✓ Junctions" else "All Stations",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (junctionsOnly) RailCyan else RailTextSecondary
            )
        }
    }
}

@Composable
fun ClearDestinationEtaCard(
    train: TrainJourney,
    destinationPred: StopPrediction,
    currentStopName: String,
    currentDelay: Int,
    isAlarmArmed: Boolean,
    onToggleAlarm: () -> Unit
) {
    val destStop = destinationPred.stop
    val finalDelay = destinationPred.delayMinutes
    val isDelayed = finalDelay > 0
    val recoveredMinutes = (currentDelay - finalDelay).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(RailCyan.copy(alpha = 0.7f), RailAmber.copy(alpha = 0.4f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Location status & Alarm Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📍 Live Location: ", fontSize = 11.sp, color = RailMuted)
                    Text(
                        text = currentStopName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Alarm Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAlarmArmed) RailAmber.copy(alpha = 0.25f) else Color(0xFF1E293B))
                        .border(1.dp, if (isAlarmArmed) RailAmber else RailCardBorder, RoundedCornerShape(6.dp))
                        .clickable { onToggleAlarm() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAlarmArmed) "🔔 Alarm Armed" else "🔕 Wakeup Alarm",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlarmArmed) RailAmber else RailTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Main Destination Arrival Time Block
            Text(
                text = "WHEN WILL IT REACH YOUR DESTINATION?",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = RailCyan,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${destStop.station.name} (${destStop.station.code})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = RailTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${destinationPred.predictedEta} IST",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDelayed) RailAmber else RailGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDelayed) "Predicted IST" else "On Time IST",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDelayed) RailAmber else RailGreen,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (isDelayed) {
                        Text(
                            text = "Scheduled Arrival: ${destStop.scheduledArrival} IST",
                            fontSize = 11.sp,
                            textDecoration = TextDecoration.LineThrough,
                            color = RailMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Delay Badge
                Text(
                    text = if (isDelayed) "+$finalDelay MIN DELAY" else "ON TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDelayed) RailAmber else RailGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            (if (isDelayed) RailAmber else RailGreen).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plain-English Cascade Explanation Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, RailCardBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = if (recoveredMinutes > 0) "💡 Delay Absorption Engine:" else "💡 Status:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (recoveredMinutes > 0) RailGreen else RailCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (recoveredMinutes > 0) {
                            "The route has built-in timetable slack buffers. Even though current delay is $currentDelay min, the train is predicted to recover $recoveredMinutes minutes and reach destination only $finalDelay min late!"
                        } else if (currentDelay == 0) {
                            "The train is running punctually on schedule. No upstream bottleneck delays detected."
                        } else {
                            "Heavy traffic on the corridor means the current $currentDelay min delay will carry through to destination."
                        },
                        fontSize = 11.sp,
                        color = RailTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveDelayDemoCard(
    delayMinutes: Float,
    onDelayChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "🎮 Test What-If Delay Scenarios",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Slide to simulate delay and watch future arrival times adjust live",
                        fontSize = 10.sp,
                        color = RailMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${delayMinutes.toInt()} min late",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (delayMinutes == 0f) RailGreen else RailAmber
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = delayMinutes,
                onValueChange = onDelayChange,
                valueRange = 0f..90f,
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = RailAmber,
                    activeTrackColor = RailAmber,
                    inactiveTrackColor = RailCardBorder
                )
            )

            // Preset Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0 to "On Time", 15 to "+15m", 30 to "+30m", 60 to "+60m").forEach { (min, label) ->
                    val isSelected = delayMinutes.toInt() == min
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) RailAmber.copy(alpha = 0.25f) else Color(0xFF1E293B))
                            .clickable { onDelayChange(min.toFloat()) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) RailAmber else RailTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StationAnnouncementBanner(
    trainNumber: String,
    trainName: String,
    currentStationName: String,
    delayMinutes: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📢", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE STATION ANNOUNCEMENT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailCyan
                )
                Text(
                    text = if (delayMinutes > 0)
                        "Attention: Train $trainNumber $trainName is arriving at $currentStationName running late by $delayMinutes mins. Track clear ahead."
                    else
                        "Attention: Train $trainNumber $trainName is approaching $currentStationName on time. Platform clearance granted.",
                    fontSize = 10.sp,
                    color = RailTextSecondary,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
fun StationTimelineList(
    predictions: List<StopPrediction>,
    expandedIndex: Int?,
    onToggleExpand: (Int) -> Unit,
    onSelectCurrentStation: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(predictions) { index, prediction ->
            CleanStationRow(
                prediction = prediction,
                isFirst = index == 0,
                isLast = index == predictions.lastIndex,
                isExpanded = expandedIndex == index,
                onToggle = { onToggleExpand(index) },
                onSetAsCurrent = { onSelectCurrentStation(index) }
            )
        }
    }
}

@Composable
fun CleanStationRow(
    prediction: StopPrediction,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSetAsCurrent: () -> Unit
) {
    val stop = prediction.stop
    val isDelayed = prediction.delayMinutes > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        // Track Node Line & Pulsing Dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(if (isFirst) Color.Transparent else if (prediction.isCurrentOrPassed) RailCyan else RailCardBorder)
            )

            if (prediction.isCurrentStation) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(RailCyan.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RailCyan)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (prediction.isCurrentOrPassed) RailCyan else if (isDelayed) RailAmber else RailGreen)
                )
            }

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (isExpanded) 120.dp else 36.dp)
                    .background(if (isLast) Color.Transparent else if (prediction.isCurrentOrPassed) RailCyan else RailCardBorder)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Station Info Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = RailCard),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (prediction.isCurrentStation) RailCyan.copy(alpha = 0.6f) else RailCardBorder
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stop.station.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${stop.station.code})",
                                fontSize = 11.sp,
                                color = RailMuted
                            )
                            if (prediction.isCurrentStation) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HERE NOW",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RailCyan)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "${stop.distanceKm} km • Platform ${if (stop.station.platformCount > 1) "1" else "1"} • Slack ${stop.sectionSlackMinutes}m",
                            fontSize = 10.sp,
                            color = RailMuted
                        )
                    }

                    // Times
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${prediction.predictedEta} IST",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDelayed) RailAmber else RailGreen
                        )
                        if (isDelayed) {
                            Text(
                                text = "Sched: ${stop.scheduledArrival} IST",
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.LineThrough,
                                color = RailMuted
                            )
                        }
                    }
                }

                // Interactive Expandable Sheet (Coach Guide, Platform Amenities & Details)
                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        HorizontalDivider(color = RailCardBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Platform Amenities Icons
                        Text(
                            text = "PLATFORM AMENITIES & SERVICES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("📶 High-Speed WiFi", "♿ Wheelchair", "☕ Food Court", "🧳 Cloak Room", "🚕 EV Taxi").forEach { amenity ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(text = amenity, fontSize = 9.sp, color = RailTextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Coach Position Guide
                        Text(
                            text = "COACH POSITION INDICATOR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("ENG", "C1", "C2", "C3", "E1", "E2", "C4", "C5", "C6").forEach { coach ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (coach.startsWith("E")) RailAmber.copy(alpha = 0.2f) else Color(0xFF131D31))
                                        .border(1.dp, if (coach.startsWith("E")) RailAmber else RailCardBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = coach,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (coach.startsWith("E")) RailAmber else RailCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Simulation Action: Set as current location
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prediction.explanation,
                                fontSize = 10.sp,
                                color = RailTextSecondary,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )

                            Button(
                                onClick = onSetAsCurrent,
                                colors = ButtonDefaults.buttonColors(containerColor = RailCyan),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(text = "Track from here", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileBottomNavBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = RailCard,
        shadowElevation = 8.dp
    ) {
        Column {
            HorizontalDivider(color = RailCardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val tabColor = if (isSelected) RailCyan else RailMuted

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) RailCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = tab.icon, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = tabColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
