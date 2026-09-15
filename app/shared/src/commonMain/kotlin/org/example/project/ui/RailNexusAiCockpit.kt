package org.example.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import org.example.project.data.NashikStationData
import org.example.project.data.SampleRailwayData
import org.example.project.engine.*
import org.example.project.model.*
import org.example.project.ui.theme.*

/**
 * RailNexus AI Cockpit - Real-Time Machine Learning ETA Prediction,
 * Asymmetric Quantile Intervals (p10/p90), Confidence Scoring, and Operational Explainability.
 */
@Composable
fun RailNexusAiCockpitCard(
    journey: TrainJourney,
    currentStopIndex: Int,
    speedKmH: Double,
    currentDelayMinutes: Int,
    currentEpochMillis: Long,
    activePredictor: IEtaPredictor,
    onSwitchPredictor: (IEtaPredictor) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeStopIndex = currentStopIndex.coerceIn(0, journey.stops.lastIndex)
    val targetStopIndex = (safeStopIndex + 1).coerceAtMost(journey.stops.lastIndex)
    val targetStop = journey.stops[targetStopIndex]

    // 1. Build live telemetry snapshot
    val telemetry = remember(journey.trainNumber, speedKmH, safeStopIndex, currentDelayMinutes, currentEpochMillis) {
        TrainTelemetrySnapshot(
            trainNumber = journey.trainNumber,
            latitude = 19.85, // Regional Central Railway coordinates
            longitude = 73.65,
            speedKmH = speedKmH,
            headingDegrees = 45.0,
            currentStopIndex = safeStopIndex,
            currentDelayMinutes = currentDelayMinutes,
            timestampEpochMillis = currentEpochMillis
        )
    }

    // 2. Extract normalized feature vector
    val features = remember(journey, safeStopIndex, targetStopIndex, telemetry) {
        FeatureEngineeringEngine.extractFeatures(
            journey = journey,
            currentStopIndex = safeStopIndex,
            targetStopIndex = targetStopIndex,
            telemetry = telemetry
        )
    }

    // 3. Approximate scheduled arrival epoch millis
    val scheduledTargetMillis = remember(targetStop.scheduledArrival, currentEpochMillis) {
        val istTime = IndianStandardTime.fromEpochMillis(currentEpochMillis)
        val remainingSecs = istTime.secondsUntil(targetStop.scheduledArrival)
        currentEpochMillis + (remainingSecs * 1000L)
    }

    // 4. Execute active ML / Heuristic prediction
    val prediction = remember(activePredictor, features, scheduledTargetMillis) {
        activePredictor.predictStationArrival(
            features = features,
            scheduledArrivalEpochMillis = scheduledTargetMillis
        )
    }

    // 5. Calculate uncertainty and confidence score
    val confidence = remember(prediction, currentEpochMillis) {
        ConfidenceCalculationEngine.computeConfidence(
            prediction = prediction,
            telemetryTimestampEpochMillis = currentEpochMillis,
            currentEpochMillis = currentEpochMillis
        )
    }

    // 6. Synthesize plain-English operational explainability
    val explanations = remember(prediction, features) {
        ExplainabilityEngine.synthesizeExplanation(prediction, features)
    }

    // 7. Evaluate proactive alerts
    val wakeupAlert = remember(journey.trainNumber, journey.destination.code, prediction.pointEtaEpochMillis, currentEpochMillis) {
        AlertEngine.evaluateWakeupAlert(
            trainNumber = journey.trainNumber,
            destinationStationCode = journey.destination.code,
            pointEtaEpochMillis = prediction.pointEtaEpochMillis,
            currentEpochMillis = currentEpochMillis
        )
    }

    val ghatAlert = remember(journey.trainNumber, journey.stops[safeStopIndex].station.code, currentEpochMillis) {
        AlertEngine.evaluateGhatAlert(
            trainNumber = journey.trainNumber,
            currentStationCode = journey.stops[safeStopIndex].station.code,
            currentEpochMillis = currentEpochMillis
        )
    }

    // 8. Track block conflict & precedence arbitration
    val precedenceResolution = remember(journey, safeStopIndex) {
        val isNearGhat = journey.stops.any { it.station.code == "KSRA" || it.station.code == "IGP" }
        if (isNearGhat) {
            val conflictBlock = TrackBlockOccupancy(
                blockId = "BLOCK_KSRA_IGP",
                blockName = "Kasara - Igatpuri Thal Ghat Incline",
                isSingleTrack = true,
                currentTrainNumber = journey.trainNumber
            )
            val otherTrain = SampleRailwayData.allSampleTrains.find { it.trainNumber != journey.trainNumber }
                ?: SampleRailwayData.train12424
            NetworkAwareAnalysisEngine.resolveCrossingPrecedence(
                trainA = journey,
                trainB = otherTrain,
                conflictBlock = conflictBlock,
                crossingStationCode = "KSRA"
            )
        } else null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(RailCyan.copy(alpha = 0.8f), RailGreen.copy(alpha = 0.5f)))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Module Title & Model Hot-Swap Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(RailCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RAILNEXUS AI PREDICTION ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = RailCyan,
                        letterSpacing = 1.sp
                    )
                }

                // Interactive Model Switcher Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            val nextModel = if (activePredictor is QuantileEtaPredictor) {
                                HeuristicEtaPredictor()
                            } else {
                                QuantileEtaPredictor()
                            }
                            onSwitchPredictor(nextModel)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (activePredictor is QuantileEtaPredictor) "🤖 Quantile ML (p10-p90)" else "⚡ Heuristic Baseline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activePredictor is QuantileEtaPredictor) RailCyan else RailAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⇄ Switch",
                            fontSize = 9.sp,
                            color = RailTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantile ETA Banner & Confidence Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Point ETA + Quantile Interval
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B132B))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT: ${targetStop.station.name.uppercase()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val delayColor = if (prediction.predictedDelayMinutes <= 5) RailGreen else if (prediction.predictedDelayMinutes <= 15) RailAmber else RailRed
                            Text(
                                text = if (prediction.predictedDelayMinutes == 0) "ON TIME" else "+${prediction.predictedDelayMinutes}m delay",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = delayColor
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Predicted Point ETA
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = prediction.predictedEtaHHmm,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = RailTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IST",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailCyan,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(Sch ${targetStop.scheduledArrival})",
                                fontSize = 11.sp,
                                color = RailMuted,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quantile Interval Display (p10 Best Case vs p90 Worst Case)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "p10 (BEST CASE)", fontSize = 8.sp, color = RailGreen, fontWeight = FontWeight.Bold)
                                Text(text = "+${prediction.quantileLowerMinutes}m", fontSize = 11.sp, color = RailTextPrimary, fontWeight = FontWeight.ExtraBold)
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(RailCardBorder)
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "SPREAD", fontSize = 8.sp, color = RailTextSecondary, fontWeight = FontWeight.Bold)
                                Text(text = "±${confidence.spreadMinutes}m", fontSize = 11.sp, color = RailCyan, fontWeight = FontWeight.ExtraBold)
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(RailCardBorder)
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "p90 (WORST CASE)", fontSize = 8.sp, color = RailRed, fontWeight = FontWeight.Bold)
                                Text(text = "+${prediction.quantileUpperMinutes}m", fontSize = 11.sp, color = RailTextPrimary, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // Confidence Gauge Box
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B132B))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CONFIDENCE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Large Percentage Score with Colored Arc
                        val levelColor = Color(confidence.level.colorHex)
                        Text(
                            text = "${confidence.scorePercent}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = levelColor
                        )

                        // Confidence Level Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(levelColor.copy(alpha = 0.15f))
                                .border(1.dp, levelColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = confidence.level.label,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = levelColor,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Fresh: ${confidence.dataFreshnessSeconds}s ago",
                            fontSize = 8.sp,
                            color = RailMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explainability Insights (Transparent AI Operational Reasoning)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💡", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OPERATIONAL EXPLAINABILITY (XAI)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Physics & Signal Attribution",
                        fontSize = 8.sp,
                        color = RailMuted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                explanations.forEach { explanation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "•", fontSize = 10.sp, color = RailCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = explanation,
                            fontSize = 10.sp,
                            color = RailTextPrimary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Proactive Alert Banner (Wakeup / Ghat / Collision Risk)
            val activeAlert = wakeupAlert ?: ghatAlert
            AnimatedVisibility(visible = activeAlert != null) {
                activeAlert?.let { alert ->
                    val alertColor = when (alert.severity) {
                        AlertSeverity.CRITICAL -> RailRed
                        AlertSeverity.WARNING -> RailAmber
                        AlertSeverity.INFO -> RailCyan
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(alertColor.copy(alpha = 0.15f))
                            .border(1.dp, alertColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (alert.type) {
                                AlertType.WAKEUP -> "⏰"
                                AlertType.GHAT_SECTION_ALERT -> "🏔️"
                                AlertType.CONNECTION_RISK -> "⚠️"
                                AlertType.DELAY_SPIKE -> "📈"
                                AlertType.PLATFORM_CONFLICT -> "🚉"
                            },
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = alertColor
                            )
                            Text(
                                text = alert.message,
                                fontSize = 9.sp,
                                color = RailTextPrimary
                            )
                        }
                    }
                }
            }

            // Precedence Arbitration Banner (Network-Aware Single-Line Analysis)
            precedenceResolution?.let { res ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.4f))
                        .border(1.dp, RailCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🚦", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Single-Line Precedence: ${res.reason}",
                        fontSize = 9.sp,
                        color = RailTextSecondary,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}
