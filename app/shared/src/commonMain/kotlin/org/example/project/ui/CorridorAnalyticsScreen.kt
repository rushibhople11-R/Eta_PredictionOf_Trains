package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.engine.NetworkAwareForecastingEngine
import org.example.project.model.*
import org.example.project.ui.theme.*

@Composable
fun CorridorAnalyticsScreen(
    currentTrain: TrainJourney,
    predictions: List<StopPrediction>,
    simulatedDelayMinutes: Float
) {
    val networkReport = remember(currentTrain, simulatedDelayMinutes) {
        NetworkAwareForecastingEngine.forecastJourney(
            journey = currentTrain,
            currentStopIndex = 1,
            observedDelayMinutes = simulatedDelayMinutes.toInt()
        )
    }
    val totalSlackMinutes = remember(currentTrain) {
        currentTrain.stops.sumOf { it.sectionSlackMinutes }
    }
    val totalDistance = remember(currentTrain) {
        currentTrain.stops.lastOrNull()?.distanceKm ?: 0
    }
    val finalPrediction = predictions.lastOrNull()
    val netDelay = finalPrediction?.delayMinutes ?: 0
    val netRecovered = (simulatedDelayMinutes.toInt() - netDelay).coerceAtLeast(0)

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
                text = "CORRIDOR INTELLIGENCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RailCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Delay Progression & Slack Map",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RailTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Analyzing where ${currentTrain.trainNumber} gains or loses time",
                fontSize = 12.sp,
                color = RailTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Metric Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Route Distance",
                    value = "$totalDistance km",
                    color = RailCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Total Buffer Slack",
                    value = "${totalSlackMinutes}m",
                    color = RailGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Net Recovery",
                    value = "${netRecovered}m",
                    color = if (netRecovered > 0) RailGreen else RailAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isWide) {
                // Wide 2-column scrollable layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Network-Aware Chained Corridor Progression
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            NetworkAwareChainedCorridorCard(report = networkReport)
                            CorridorHotspotsCard()
                        }
                    }

                    // Right Column: Trajectory List
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DelayTrajectoryCard(predictions = predictions)
                    }
                }
            } else {
                // Mobile stacked layout
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        NetworkAwareChainedCorridorCard(report = networkReport)
                    }
                    item {
                        DelayTrajectoryCard(predictions = predictions)
                    }
                    item {
                        CorridorHotspotsCard()
                    }
                }
            }
        }
    }
}

@Composable
fun DelayTrajectoryCard(predictions: List<StopPrediction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DELAY PROPAGATION TRAJECTORY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RailMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            predictions.forEach { pred ->
                val stop = pred.stop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(110.dp)) {
                        Text(
                            text = stop.station.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${stop.distanceKm} km",
                            fontSize = 10.sp,
                            color = RailMuted
                        )
                    }

                    // Visual bar representing remaining delay
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(RailCardBorder)
                    ) {
                        val maxDelay = 90f
                        val fraction = (pred.delayMinutes / maxDelay).coerceIn(0f, 1f)
                        if (fraction > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (pred.delayMinutes > 30) RailAmber else RailGreen
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Delay label
                    Text(
                        text = if (pred.delayMinutes > 0) "+${pred.delayMinutes}m" else "0m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pred.delayMinutes > 0) RailAmber else RailGreen,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CorridorHotspotsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ENGINEERING SLACK & BOTTLENECK ZONES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RailMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            HotspotItem(
                icon = "⚡",
                title = "Terminal Recovery Buffer",
                subtitle = "25 mins scheduled slack before Patna Jn absorbs upstream delays.",
                color = RailGreen
            )
            HorizontalDivider(color = RailCardBorder, modifier = Modifier.padding(vertical = 10.dp))

            HotspotItem(
                icon = "⚡",
                title = "DDU Yard Transition Buffer",
                subtitle = "20 mins slack engineered before Pt. Deen Dayal Upadhyaya Jn.",
                color = RailGreen
            )
            HorizontalDivider(color = RailCardBorder, modifier = Modifier.padding(vertical = 10.dp))

            HotspotItem(
                icon = "⚠️",
                title = "Prayagraj Convergence Bottleneck",
                subtitle = "Junction conflict multiplier (1.25x) during peak evening train crossings.",
                color = RailAmber
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RailCard)
            .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = title, fontSize = 10.sp, color = RailMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
fun HotspotItem(
    icon: String,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = RailTextSecondary, lineHeight = 15.sp)
        }
    }
}

@Composable
fun NetworkAwareChainedCorridorCard(report: NetworkAwareForecastReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NETWORK-AWARE CHAINED TOPOLOGY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Train → Station → Segment → Station",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RailTextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RailGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Kinematic Bound",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation Callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RailDark.copy(alpha = 0.7f))
                    .padding(10.dp)
            ) {
                Text(
                    text = report.networkAdvantageExplanation,
                    fontSize = 11.sp,
                    color = RailTextSecondary,
                    lineHeight = 15.sp
                )
            }

            // Multi-Train Precedence Alerts (if any)
            if (report.multiTrainConflicts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                report.multiTrainConflicts.forEach { conflict ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailAmber.copy(alpha = 0.15f))
                            .border(1.dp, RailAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🚦", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Cross Precedence: Train ${conflict.higherPriorityTrainNumber} > ${conflict.lowerPriorityTrainNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RailAmber
                                )
                                Text(
                                    text = "${conflict.reason} • Train ${conflict.lowerPriorityTrainNumber} holds at ${conflict.crossingStationCode} (+${conflict.lowerPriorityDelayPenaltyMinutes}m)",
                                    fontSize = 10.sp,
                                    color = RailTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Chained Stations and Segments pipeline
            report.stationForecasts.forEachIndexed { idx, stForecast ->
                val node = stForecast.stationNode

                // Station Node Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (node.isJunction) RailCyan else RailMuted.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${node.sequence}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (node.isJunction) RailDark else RailTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${node.stationName} (${node.stationCode})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailTextPrimary
                            )
                            if (node.isJunction) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "JUNCTION (${node.platformCount} Pfs)",
                                    fontSize = 9.sp,
                                    color = RailCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Sched: ${stForecast.predictedArrivalHHmm} • Dwell: ${stForecast.dwellDurationMinutes}m (${stForecast.dwellExplanation})",
                            fontSize = 10.sp,
                            color = RailTextSecondary
                        )
                    }

                    if (stForecast.arrivalDelayMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RailAmber.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${stForecast.arrivalDelayMinutes}m",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailAmber
                            )
                        }
                    } else {
                        Text(text = "ON TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RailGreen)
                    }
                }

                // Intermediate Segment (if not the last station)
                if (idx < report.segmentTransits.size) {
                    val transit = report.segmentTransits[idx]
                    val seg = transit.segment

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 11.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vertical dashed connector
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(38.dp)
                                .background(RailCardBorder)
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RailDark.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${seg.distanceKm} km @ MPS ${seg.speedLimitKmh.toInt()} km/h",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RailCyan
                                )
                                if (seg.isGhatSection) {
                                    Text(
                                        text = "⛰️ Thal Ghat 1:37 (${seg.gradientFactor}x)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RailAmber
                                    )
                                } else if (seg.isSingleTrack) {
                                    Text(
                                        text = "Single Track Token Block",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RailRed
                                    )
                                }
                            }
                            Text(
                                text = "${transit.effectiveRuntimeMinutes.toInt()}m transit • ${transit.transitExplanation}",
                                fontSize = 9.sp,
                                color = RailMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
