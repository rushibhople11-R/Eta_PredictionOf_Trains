package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.model.ConnectingService
import org.example.project.model.ConnectionEngine
import org.example.project.model.StopPrediction
import org.example.project.model.TrainJourney
import org.example.project.model.toStandard12Hour
import org.example.project.ui.theme.*

@Composable
fun ConnectionRadarScreen(
    currentTrain: TrainJourney,
    predictions: List<StopPrediction>,
    simulatedDelayMinutes: Float
) {
    val allConnections = remember { NashikStationData.nashikConnectingServices }
    val interchangeStationCodes = remember(predictions) {
        predictions.map { it.stop.station.code }.toSet()
    }

    // Filter connecting services relevant to current train's route
    val relevantConnections = remember(currentTrain, allConnections) {
        allConnections.filter { it.interchangeStationCode in interchangeStationCodes }
    }

    var selectedStationFilter by remember { mutableStateOf<String?>(null) }

    val displayedConnections = remember(relevantConnections, selectedStationFilter) {
        if (selectedStationFilter == null) relevantConnections
        else relevantConnections.filter { it.interchangeStationCode == selectedStationFilter }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RailDark)
    ) {
        val isWide = maxWidth >= 768.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1100.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Text(
                text = "TRANSFER & CONNECTION RADAR",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RailCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Will You Catch Your Connection?",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RailTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Live evaluation based on incoming train ETA + 10m platform transfer buffer",
                fontSize = 12.sp,
                color = RailTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Station Filter Chips (Scrollable so it never overflows)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions = listOf(null to "All Hubs") + relevantConnections.map { it.interchangeStationCode }.distinct().map { it to it }
                filterOptions.forEach { (code, label) ->
                    val isSelected = selectedStationFilter == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) RailCyan.copy(alpha = 0.2f) else RailCard)
                            .border(1.dp, if (isSelected) RailCyan else RailCardBorder, RoundedCornerShape(8.dp))
                            .clickable { selectedStationFilter = code }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) RailCyan else RailTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isWide) {
                // 2-column grid for wide screens
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayedConnections) { connection ->
                        val matchingPrediction = predictions.find { it.stop.station.code == connection.interchangeStationCode }
                        val incomingEta = matchingPrediction?.predictedEta ?: "00:00"

                        val evaluation = ConnectionEngine.evaluateTransfer(
                            incomingArrivalEtaStr = incomingEta,
                            connectingService = connection,
                            platformWalkBufferMinutes = 10
                        )

                        ConnectionCard(
                            evaluation = evaluation,
                            incomingStationName = matchingPrediction?.stop?.station?.name ?: connection.interchangeStationCode
                        )
                    }
                }
            } else {
                // 1-column list for mobile
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayedConnections) { connection ->
                        val matchingPrediction = predictions.find { it.stop.station.code == connection.interchangeStationCode }
                        val incomingEta = matchingPrediction?.predictedEta ?: "00:00"

                        val evaluation = ConnectionEngine.evaluateTransfer(
                            incomingArrivalEtaStr = incomingEta,
                            connectingService = connection,
                            platformWalkBufferMinutes = 10
                        )

                        ConnectionCard(
                            evaluation = evaluation,
                            incomingStationName = matchingPrediction?.stop?.station?.name ?: connection.interchangeStationCode
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionCard(
    evaluation: org.example.project.model.TransferEvaluation,
    incomingStationName: String
) {
    val service = evaluation.connectingService
    val feasibility = evaluation.feasibility
    val badgeColor = Color(feasibility.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(badgeColor.copy(alpha = 0.6f), RailCardBorder)
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Service info and Feasibility Score Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${service.serviceNumber} • ${service.serviceName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${service.serviceType.label} • At $incomingStationName (${service.interchangeStationCode})",
                        fontSize = 11.sp,
                        color = RailMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Probability Score Gauge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${evaluation.feasibilityScore}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeColor
                    )
                    Text(
                        text = feasibility.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Comparison Timeline Row: Incoming ETA vs Walk vs Departure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Your Train ETA (IST)", fontSize = 10.sp, color = RailMuted)
                    Text(
                        text = "${evaluation.incomingArrivalEta.toStandard12Hour()} IST",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailCyan
                    )
                }

                Text(
                    text = "➔ 10m walk ➔",
                    fontSize = 11.sp,
                    color = RailMuted
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Departs (${service.departurePlatform})", fontSize = 10.sp, color = RailMuted)
                    Text(
                        text = "${service.departureTime.toStandard12Hour()} IST",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cushion & Recommendation Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = evaluation.recommendation,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = badgeColor,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
