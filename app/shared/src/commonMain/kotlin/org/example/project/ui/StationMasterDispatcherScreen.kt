package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.example.project.engine.CorridorSimulationEngine
import org.example.project.engine.HeuristicEtaPredictor
import org.example.project.engine.HistoricalPunctualityEngine
import org.example.project.engine.QuantileEtaPredictor
import org.example.project.model.*
import org.example.project.ui.theme.*

private val DarkBackground = RailDark
private val DarkSurface = RailCard
private val DarkTextPrimary = RailTextPrimary
private val DarkTextSecondary = RailTextSecondary
private val DarkTextMuted = RailMuted
private val PrimaryColor = RailCyan
private val AccentColor = RailAmber
private val SuccessColor = RailGreen
private val WarningColor = RailAmber
private val ErrorColor = RailRed

/**
 * RailNexus Phase 4: Station Master & Network Dispatcher Cockpit
 * Multi-Train Corridor Tracking, What-If Incident Injections,
 * Platform Allocation Matrix, and Historical Punctuality Analytics.
 */
@Composable
fun StationMasterDispatcherScreen(
    modifier: Modifier = Modifier
) {
    val corridorEngine = remember { CorridorSimulationEngine() }
    var selectedTrainNumber by remember { mutableStateOf<String?>("22223") }
    var activePredictor by remember { mutableStateOf(corridorEngine.getPredictor()) }
    var mutationTrigger by remember { mutableStateOf(0) }

    // 1-second ticking clock for live corridor simulation
    val currentEpochMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    val snapshot = remember(currentEpochMillis, mutationTrigger) {
        corridorEngine.computeCorridorSnapshot(currentEpochMillis)
    }

    val punctualityReport = remember {
        HistoricalPunctualityEngine.generatePunctualityReport("Bhusawal (BSL)")
    }

    val istTime = remember(currentEpochMillis) {
        IndianStandardTime.fromEpochMillis(currentEpochMillis)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER COCKPIT ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF334155))
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
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                                ) {
                                    Text(
                                        text = "PHASE 4 ACTIVE",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Central Railway • Bhusawal Division",
                                    color = DarkTextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Station Master & Corridor Controller",
                                color = DarkTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Nashik Road (NK) • Thal Ghat Mainline Corridor (CSMT ↔ MMR)",
                                color = PrimaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // IST Digital Clock
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = istTime.time24,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "${istTime.dateStr} (IST)",
                                    color = DarkTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Model Selector Pill Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Inference Model:",
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (activePredictor is QuantileEtaPredictor) PrimaryColor.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (activePredictor is QuantileEtaPredictor) PrimaryColor else Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    val next = QuantileEtaPredictor()
                                    corridorEngine.switchPredictor(next)
                                    activePredictor = next
                                    mutationTrigger++
                                }
                            ) {
                                Text(
                                    text = "Quantile ML (p10/p90)",
                                    color = if (activePredictor is QuantileEtaPredictor) PrimaryColor else DarkTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (activePredictor is HeuristicEtaPredictor) AccentColor.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (activePredictor is HeuristicEtaPredictor) AccentColor else Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    val next = HeuristicEtaPredictor()
                                    corridorEngine.switchPredictor(next)
                                    activePredictor = next
                                    mutationTrigger++
                                }
                            ) {
                                Text(
                                    text = "Heuristic Baseline",
                                    color = if (activePredictor is HeuristicEtaPredictor) AccentColor else DarkTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tracking ${snapshot.activeTrains.size} Corridor Trains",
                            color = SuccessColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // --- 2. WHAT-IF INCIDENT INJECTION PANEL ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF475569))
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
                        Column {
                            Text(
                                text = "Interactive What-If Incident Simulator",
                                color = DarkTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inject contingencies to simulate cascading delay ripple effects on Nashik Road ETAs",
                                color = DarkTextMuted,
                                fontSize = 12.sp
                            )
                        }

                        if (snapshot.activeContingencies.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier.clickable {
                                    corridorEngine.clearAllContingencies()
                                    mutationTrigger++
                                }
                            ) {
                                Text(
                                    text = "Clear All (${snapshot.activeContingencies.size})",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Action Dispatch Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Thal Ghat Banking Delay (+20m)
                        val ghatActive = snapshot.activeContingencies.any { it.id == "CONTINGENCY_GHAT" }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (ghatActive) {
                                        corridorEngine.removeContingency("CONTINGENCY_GHAT")
                                    } else {
                                        corridorEngine.injectContingency(
                                            ContingencyEvent(
                                                id = "CONTINGENCY_GHAT",
                                                title = "Thal Ghat Banking Detach Delay",
                                                description = "Igatpuri siding congestion; brake test backlog",
                                                affectedBlockId = "BLOCK_KSRA_IGP",
                                                affectedTrainNumber = "12109",
                                                delayPenaltyMinutes = 20,
                                                speedLimitKmph = 30
                                            )
                                        )
                                    }
                                    mutationTrigger++
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (ghatActive) Color(0xFFB45309).copy(alpha = 0.3f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (ghatActive) WarningColor else Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (ghatActive) "Active (-)" else "Inject (+)",
                                    color = if (ghatActive) WarningColor else DarkTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Thal Ghat Queue",
                                    color = DarkTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "+20m on 12109",
                                    color = WarningColor,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // 2. Caution Order at Devlali (30 km/h)
                        val cautionActive = snapshot.activeContingencies.any { it.id == "CONTINGENCY_CAUTION" }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (cautionActive) {
                                        corridorEngine.removeContingency("CONTINGENCY_CAUTION")
                                    } else {
                                        corridorEngine.injectContingency(
                                            ContingencyEvent(
                                                id = "CONTINGENCY_CAUTION",
                                                title = "Devlali Track Caution Order",
                                                description = "Ballast cleaning machine work on approach track",
                                                affectedBlockId = "BLOCK_DVL_NK",
                                                delayPenaltyMinutes = 12,
                                                speedLimitKmph = 30
                                            )
                                        )
                                    }
                                    mutationTrigger++
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (cautionActive) Color(0xFF7C2D12).copy(alpha = 0.3f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (cautionActive) Color(0xFFFB923C) else Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (cautionActive) "Active (-)" else "Inject (+)",
                                    color = if (cautionActive) Color(0xFFFB923C) else DarkTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Devlali Caution",
                                    color = DarkTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "30 km/h (+12m)",
                                    color = Color(0xFFFB923C),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // 3. Vande Bharat Precedence Hold
                        val vandeActive = snapshot.activeContingencies.any { it.id == "VANDE_BHARAT_PREFERENCE" }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (vandeActive) {
                                        corridorEngine.removeContingency("VANDE_BHARAT_PREFERENCE")
                                    } else {
                                        corridorEngine.injectContingency(
                                            ContingencyEvent(
                                                id = "VANDE_BHARAT_PREFERENCE",
                                                title = "Vande Bharat Express Precedence",
                                                description = "Holding lower priority trains on loop line at Kasara/Igatpuri",
                                                affectedBlockId = "BLOCK_KSRA_IGP",
                                                delayPenaltyMinutes = 8
                                            )
                                        )
                                    }
                                    mutationTrigger++
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (vandeActive) Color(0xFF047857).copy(alpha = 0.3f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (vandeActive) SuccessColor else Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (vandeActive) "Active (-)" else "Inject (+)",
                                    color = if (vandeActive) SuccessColor else DarkTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Vande Bharat Priority",
                                    color = DarkTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Loop line holds",
                                    color = SuccessColor,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Flashing System Alert Banner if contingency active
                    val alertMessage = snapshot.systemAlert
                    if (alertMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🚨", fontSize = 16.sp)
                                Text(
                                    text = alertMessage,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. CORRIDOR SCHEMATIC TRACK RADAR ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nashik Mainline Corridor Schematic Track Radar",
                            color = DarkTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CSMT ↔ KYN ↔ KSRA ↔ IGP ↔ DVL ↔ NK ↔ MMR",
                            color = PrimaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Linear Corridor Track Diagram
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(snapshot.corridorBlocks) { block ->
                            val trainsInBlock = snapshot.activeTrains.filter { it.blockId == block.blockId }
                            val hasCaution = block.cautionOrderActive

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    hasCaution -> Color(0xFF7C2D12).copy(alpha = 0.25f)
                                    block.isGhatSection -> Color(0xFF431407).copy(alpha = 0.35f)
                                    trainsInBlock.isNotEmpty() -> Color(0xFF0F172A)
                                    else -> Color(0xFF0F172A).copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        hasCaution -> Color(0xFFFB923C)
                                        block.isGhatSection -> Color(0xFFF97316)
                                        trainsInBlock.isNotEmpty() -> PrimaryColor
                                        else -> Color(0xFF1E293B)
                                    }
                                ),
                                modifier = Modifier.width(170.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${block.fromStation} ➔ ${block.toStation}",
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (block.isGhatSection) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFF97316).copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "1:37 GHAT",
                                                    color = Color(0xFFF97316),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = block.blockName,
                                        color = DarkTextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${block.lengthKm.toInt()} km",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = if (hasCaution) "RESTRICT: ${block.speedRestrictionKmph} km/h" else "MPS: ${block.mpsKmph} km/h",
                                            color = if (hasCaution) WarningColor else DarkTextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = if (hasCaution) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Trains residing in this block
                                    if (trainsInBlock.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            trainsInBlock.forEach { t ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (t.trainType == TrainType.VANDE_BHARAT) Color(0xFF38BDF8).copy(alpha = 0.2f) else PrimaryColor.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, if (t.trainType == TrainType.VANDE_BHARAT) Color(0xFF38BDF8) else PrimaryColor)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "🚆 ${t.trainNumber}",
                                                            color = DarkTextPrimary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${t.currentSpeedKmH.toInt()}k/h",
                                                            color = if (t.isHeldForPrecedence) WarningColor else SuccessColor,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Block Clear",
                                            color = Color(0xFF10B981).copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. NASHIK ROAD (NK) PLATFORM ALLOCATION MATRIX ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF334155))
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
                        Text(
                            text = "Nashik Road (NK) Live Platform Allocation Matrix",
                            color = DarkTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Station Master Dispatch View",
                            color = DarkTextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Pf 1", "Pf 2", "Pf 3", "Pf 4").forEach { pf ->
                            val occupancy = snapshot.platformOccupancy[pf]
                            val isOccupied = occupancy != null

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isOccupied) Color(0xFF1E293B) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isOccupied) PrimaryColor else Color(0xFF1E293B))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pf,
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    if (isOccupied) WarningColor else SuccessColor,
                                                    CircleShape
                                                )
                                        )
                                    }
                                    Text(
                                        text = if (isOccupied) "APPROACHING" else "CLEAR",
                                        color = if (isOccupied) WarningColor else SuccessColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = occupancy ?: "Available for arrival",
                                        color = if (isOccupied) DarkTextPrimary else DarkTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. MULTI-TRAIN FLEET STATUS & OPERATIONAL NOTES ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Nashik Corridor Multi-Train Fleet Tracking",
                        color = DarkTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        snapshot.activeTrains.forEach { train ->
                            val isSelected = selectedTrainNumber == train.trainNumber

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isSelected) PrimaryColor else Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTrainNumber = train.trainNumber }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            Text(
                                                text = train.trainNumber,
                                                color = PrimaryColor,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = train.trainName,
                                                color = DarkTextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Delay & Platform badge
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0F172A),
                                                border = BorderStroke(1.dp, Color(0xFF334155))
                                            ) {
                                                Text(
                                                    text = train.assignedPlatform,
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    train.predictedDelayMinutes <= 5 -> SuccessColor.copy(alpha = 0.2f)
                                                    train.predictedDelayMinutes <= 15 -> WarningColor.copy(alpha = 0.2f)
                                                    else -> ErrorColor.copy(alpha = 0.2f)
                                                }
                                            ) {
                                                Text(
                                                    text = if (train.predictedDelayMinutes == 0) "ON TIME" else "+${train.predictedDelayMinutes} min",
                                                    color = when {
                                                        train.predictedDelayMinutes <= 5 -> SuccessColor
                                                        train.predictedDelayMinutes <= 15 -> WarningColor
                                                        else -> ErrorColor
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Section: ${train.currentSection} • Speed: ${train.currentSpeedKmH.toInt()} km/h",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Nashik ETA: ${train.predictedEtaAtNashik.toStandard12Hour()} (Sched: ${train.scheduledArrivalAtNashik.toStandard12Hour()})",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Operational Note
                                    Text(
                                        text = "⚡ ${train.operationalNote}",
                                        color = if (train.isHeldForPrecedence) WarningColor else DarkTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. HISTORICAL PUNCTUALITY & SLACK ABSORPTION KPIS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF334155))
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
                        Text(
                            text = "Historical Division Punctuality & Slack KPIs",
                            color = DarkTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${punctualityReport.division} • ${punctualityReport.period}",
                            color = PrimaryColor,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // On-Time %
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "ON-TIME RATE", color = DarkTextMuted, fontSize = 10.sp)
                                Text(
                                    text = "${punctualityReport.onTimePercentage.toInt()}%",
                                    color = SuccessColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(text = "Delays < 5 min", color = DarkTextSecondary, fontSize = 10.sp)
                            }
                        }

                        // Average Delay
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "AVG CORRIDOR DELAY", color = DarkTextMuted, fontSize = 10.sp)
                                Text(
                                    text = "${punctualityReport.averageDelayMinutes.toInt()}m",
                                    color = WarningColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(text = "All train categories", color = DarkTextSecondary, fontSize = 10.sp)
                            }
                        }

                        // Thal Ghat Avg
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "THAL GHAT VARIANCE", color = DarkTextMuted, fontSize = 10.sp)
                                Text(
                                    text = "${punctualityReport.thalGhatAverageDelayMinutes.toInt()}m",
                                    color = Color(0xFFF97316),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(text = "Banking & brake tests", color = DarkTextSecondary, fontSize = 10.sp)
                            }
                        }

                        // Slack Absorbed
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "SLACK RECOVERED", color = DarkTextMuted, fontSize = 10.sp)
                                Text(
                                    text = "+${punctualityReport.sectionSlackAbsorptionAvgMinutes.toInt()}m",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(text = "IGP ➔ NK section", color = DarkTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
