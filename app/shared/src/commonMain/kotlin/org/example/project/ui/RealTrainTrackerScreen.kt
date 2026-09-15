package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.RealStopRecord
import org.example.project.data.RealTrainProfile
import org.example.project.data.RealTrainRecordRegistry
import org.example.project.engine.HistoricalPunctualityEngine
import org.example.project.engine.HistoricalTrainRunRecord
import org.example.project.engine.RtisTelemetryEngine
import org.example.project.engine.SignalAspect

private val NavyDark = Color(0xFF0B192C)
private val CardBg = Color(0xFFFFFFFF)
private val BorderMuted = Color(0xFFE2E8F0)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val Emerald = Color(0xFF10B981)
private val EmeraldDark = Color(0xFF059669)
private val EmeraldLight = Color(0xFFECFDF5)
private val Amber = Color(0xFFD97706)
private val CoralRed = Color(0xFFEF4444)

enum class TelemetryViewMode {
    RTIS_LIVE,
    HISTORICAL_ARCHIVE
}

/**
 * Real Train Record Tracker & Historical Journey Archive Screen
 * Allows querying any real 5-digit Indian Railways train, inspecting live RTIS telemetry,
 * and reviewing 30-day historical punctuality logs with ground-truth timetable comparisons.
 */
@Composable
fun RealTrainTrackerScreen(
    initialTrainNumber: String = "12109",
    onBackToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf(initialTrainNumber) }
    var selectedTrainNumber by remember { mutableStateOf(initialTrainNumber) }
    var viewMode by remember { mutableStateOf(TelemetryViewMode.RTIS_LIVE) }
    var rtisTick by remember { mutableStateOf(0) }

    val allTrains = remember { RealTrainRecordRegistry.getAllRealTrains() }
    val currentTrain = remember(selectedTrainNumber) {
        RealTrainRecordRegistry.getTrainByNumber(selectedTrainNumber) ?: allTrains.first()
    }
    var liveSecondsTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            liveSecondsTick = (liveSecondsTick + 1) % 60
        }
    }

    val rtisData = remember(currentTrain.trainNumber, rtisTick, liveSecondsTick) {
        RtisTelemetryEngine.generateLiveTelemetry(currentTrain.trainNumber, rtisTick)
    }
    val historicalRuns = remember(currentTrain.trainNumber) {
        HistoricalPunctualityEngine.get30DayRunHistory(currentTrain.trainNumber)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. TITLE & SUBTITLE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Real Train Record Tracker & Historical Archive",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live RTIS Satellite Telemetry • CRIS/NTES 30-Day Running Ground-Truth",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.clickable { onBackToHome() }
            ) {
                Text(
                    text = "🏠 Back to Overview",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E3A8A),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // --- 2. SEARCH & QUICK TRAIN SELECTION CHIPS ---
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BorderMuted),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Input Field
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔍", fontSize = 14.sp)
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by 5-digit Train Number or Name (e.g., 12109, Vande Bharat, Rajdhani)...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    val matched = RealTrainRecordRegistry.searchTrains(query)
                                    if (matched.isNotEmpty()) {
                                        selectedTrainNumber = matched.first().trainNumber
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = TextDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.clickable {
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }

                // Quick Popular Trains Chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allTrains.forEach { train ->
                        val isSelected = currentTrain.trainNumber == train.trainNumber
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                selectedTrainNumber = train.trainNumber
                                searchQuery = train.trainNumber
                            }
                        ) {
                            Text(
                                text = "${train.trainNumber} ${train.trainName.take(18)}",
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. TRAIN PROFILE & LOCOMOTIVE SPECS HERO CARD ---
        TrainProfileHeroCard(train = currentTrain)

        // --- 4. DUAL TELEMETRY / ARCHIVE TOGGLE BAR ---
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == TelemetryViewMode.RTIS_LIVE) Color(0xFF1E3A8A) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewMode = TelemetryViewMode.RTIS_LIVE }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛰️ Live RTIS Satellite Telemetry",
                            color = if (viewMode == TelemetryViewMode.RTIS_LIVE) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == TelemetryViewMode.RTIS_LIVE) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (viewMode == TelemetryViewMode.HISTORICAL_ARCHIVE) Color(0xFF1E3A8A) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewMode = TelemetryViewMode.HISTORICAL_ARCHIVE }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📁 CRIS/NTES 30-Day Historical Archive",
                            color = if (viewMode == TelemetryViewMode.HISTORICAL_ARCHIVE) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == TelemetryViewMode.HISTORICAL_ARCHIVE) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- 5. MAIN CONTENT BASED ON ACTIVE TOGGLE ---
        when (viewMode) {
            TelemetryViewMode.RTIS_LIVE -> {
                RtisLiveTelemetryView(
                    telemetry = rtisData,
                    onSimulateTick = { rtisTick++ }
                )
            }
            TelemetryViewMode.HISTORICAL_ARCHIVE -> {
                HistoricalArchiveView(
                    trainNumber = currentTrain.trainNumber,
                    trainName = currentTrain.trainName,
                    runs = historicalRuns
                )
            }
        }

        // --- 6. STATION-BY-STATION TIMETABLE MATRIX ---
        StationScheduleMatrixCard(stops = currentTrain.stops)
    }
}

/**
 * Train Profile Hero Card
 */
@Composable
private fun TrainProfileHeroCard(train: RealTrainProfile) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NavyDark,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E3A8A)
                    ) {
                        Text(
                            text = train.trainNumber,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = train.trainName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldDark
                ) {
                    Text(
                        text = "30-Day Punctuality: ${train.onTimePunctuality30Days}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "${train.source.name} (${train.source.code}) ➔ ${train.destination.name} (${train.destination.code})",
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = Color(0xFF1E293B))

            // Spec Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Zone & Shed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(text = "${train.operatingZone} • ${train.locomotiveShed}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Locomotive Class", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(text = train.locomotiveClass, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Distance & Speed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(text = "${train.totalDistanceKm} km • MPS ${train.maxPermissibleSpeedKmh} km/h", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Rake Composition", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(text = train.rakeType, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Live RTIS Satellite Telemetry View
 */
@Composable
private fun RtisLiveTelemetryView(
    telemetry: org.example.project.engine.RtisTelemetrySnapshot,
    onSimulateTick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderMuted),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald))
                    Text(
                        text = "LIVE SATELLITE RTIS TRANSPONDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldLight,
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Text(
                        text = telemetry.satelliteNavIcStatus,
                        fontSize = 10.sp,
                        color = EmeraldDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Key Telemetry 4-Card Cascade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Speed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "CURRENT SPEED", fontSize = 9.5.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(text = "${telemetry.currentSpeedKmh.toInt()} km/h", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextDark)
                        Text(text = "MPS: ${telemetry.maxPermissibleSpeedKmh.toInt()} km/h", fontSize = 9.sp, color = EmeraldDark)
                    }
                }

                // Kilometre Post
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "TRACK KM POST", fontSize = 9.5.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(text = telemetry.kilometerPost, fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextDark)
                        Text(text = "Down Line", fontSize = 9.sp, color = TextMuted)
                    }
                }

                // Signal Aspect
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (telemetry.signalAspect == SignalAspect.GREEN_PROCEED) EmeraldLight else Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, if (telemetry.signalAspect == SignalAspect.GREEN_PROCEED) Color(0xFFA7F3D0) else Color(0xFFFDE68A)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "SIGNAL ASPECT", fontSize = 9.5.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${telemetry.signalAspect.symbol} ${telemetry.signalAspect.label.take(14)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(telemetry.signalAspect.colorHex)
                        )
                        Text(text = "Auto-Signaled", fontSize = 9.sp, color = TextMuted)
                    }
                }

                // GPS Coordinates
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "GPS COORDINATES", fontSize = 9.5.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(text = "${(telemetry.latitude * 100).toInt() / 100.0}° N", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text(text = "${(telemetry.longitude * 100).toInt() / 100.0}° E", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }

            // Block section details & Loco Telemetry
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, BorderMuted),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Active Track Block Section:", fontSize = 10.sp, color = TextMuted)
                        Text(text = telemetry.blockSectionName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "⚡ ${telemetry.throttleNotch}", fontSize = 11.sp, color = Color(0xFF1E3A8A), fontWeight = FontWeight.SemiBold)
                        Text(text = "🔋 ${telemetry.tractionCurrentAmps.toInt()} A", fontSize = 11.sp, color = EmeraldDark, fontWeight = FontWeight.SemiBold)
                        Text(text = "🛑 BP: ${telemetry.brakePipePressureKgCm2} kg/cm²", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            // Interactive Telemetry Simulation Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Telemetry Ping: ${telemetry.timestampFormatted} • Broadcast via ISRO NavIC",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Button(
                    onClick = onSimulateTick,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "⚡ Advance RTIS Telemetry Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * CRIS / NTES 30-Day Historical Archive View
 */
@Composable
private fun HistoricalArchiveView(
    trainNumber: String,
    trainName: String,
    runs: List<HistoricalTrainRunRecord>
) {
    val totalRuns = runs.size
    val onTimeCount = runs.count { it.status == "ON_TIME" }
    val minorCount = runs.count { it.status == "MINOR_DELAY" }
    val severeCount = runs.count { it.status == "SEVERE_DELAY" }
    val onTimePct = if (totalRuns > 0) ((onTimeCount.toDouble() / totalRuns) * 100).toInt() else 100

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderMuted),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "30-Day Journey Ground-Truth Records",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Archived from CRIS National Train Enquiry System (NTES)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (onTimePct >= 90) EmeraldLight else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = "$onTimePct% On-Time Record",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (onTimePct >= 90) EmeraldDark else Amber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Summary Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Total Runs", fontSize = 10.sp, color = TextMuted)
                        Text(text = "$totalRuns", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldLight,
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "On-Time (<5m)", fontSize = 10.sp, color = EmeraldDark)
                        Text(text = "$onTimeCount ($onTimePct%)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Minor Delay (5-15m)", fontSize = 10.sp, color = Amber)
                        Text(text = "$minorCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Severe (>15m)", fontSize = 10.sp, color = CoralRed)
                        Text(text = "$severeCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    }
                }
            }

            // Historical Run Logs Table (Display last 10 entries cleanly)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                runs.take(10).forEach { run ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderMuted),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(text = run.runDateFormatted, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text(text = run.primaryDelayReason, fontSize = 10.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Row(
                                modifier = Modifier.weight(1.8f),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Thal Ghat: +${run.thalGhatDelayMinutes}m", fontSize = 10.sp, color = TextMuted)
                                Text(text = "Slack: -${run.slackRecoveredMinutes}m", fontSize = 10.sp, color = EmeraldDark)

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (run.status) {
                                        "ON_TIME" -> EmeraldLight
                                        "MINOR_DELAY" -> Color(0xFFFEF3C7)
                                        else -> Color(0xFFFEF2F2)
                                    }
                                ) {
                                    Text(
                                        text = if (run.delayMinutes == 0) "Right Time" else "+${run.delayMinutes}m",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (run.status) {
                                            "ON_TIME" -> EmeraldDark
                                            "MINOR_DELAY" -> Amber
                                            else -> CoralRed
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

/**
 * Station-by-Station Timetable Matrix Card
 */
@Composable
private fun StationScheduleMatrixCard(stops: List<RealStopRecord>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, BorderMuted),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATION SCHEDULE & LIVE TRACKING MATRIX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${stops.size} Stoppages Recorded",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // Table Header
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "STATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1.5f))
                    Text(text = "PF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.5f))
                    Text(text = "TIMETABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1f))
                    Text(text = "ACTUAL / ETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1f))
                    Text(text = "DELAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.8f))
                    Text(text = "STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1f))
                }
            }

            // Table Rows
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stops.forEach { stop ->
                    val isCurrent = stop.status == "CURRENT"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCurrent) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isCurrent) Color(0xFF93C5FD) else Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "${stop.stationCode} - ${stop.stationName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color(0xFF1E3A8A) else TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "${stop.distanceFromSourceKm} km", fontSize = 9.sp, color = TextMuted)
                            }

                            Text(text = stop.platform, fontSize = 10.sp, color = TextDark, modifier = Modifier.weight(0.5f))
                            Text(text = "${stop.scheduledArrival} / ${stop.scheduledDeparture}", fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1f))
                            Text(text = stop.actualArrivalOrEta, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.weight(1f))

                            Text(
                                text = if (stop.delayMinutes == 0) "RT" else "+${stop.delayMinutes}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stop.delayMinutes == 0) EmeraldDark else CoralRed,
                                modifier = Modifier.weight(0.8f)
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (stop.status) {
                                    "DEPARTED" -> Color(0xFFE2E8F0)
                                    "CURRENT" -> EmeraldLight
                                    else -> Color(0xFFFEF3C7)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stop.status,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (stop.status) {
                                        "DEPARTED" -> TextMuted
                                        "CURRENT" -> EmeraldDark
                                        else -> Amber
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
