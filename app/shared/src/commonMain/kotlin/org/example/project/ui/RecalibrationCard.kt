package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.engine.ContinuousRecalibrationEngine
import org.example.project.model.ContinuousRecalibrationReport
import org.example.project.model.LiveOperationalUpdate
import org.example.project.model.TrainJourney
import org.example.project.model.toStandard12Hour
import org.example.project.ui.theme.*

/**
 * Continuous Recalibration Dashboard Card.
 *
 * Displays:
 * - ETA
 * - Last updated
 * - Previous ETA
 * - Change (e.g. +3 minutes)
 * - Reason (e.g. "Current segment travel time increased.")
 * - Real-time 10-Step Pipeline verification audit log
 */
@Composable
fun RecalibrationCard(
    train: TrainJourney,
    currentDelayMinutes: Int,
    modifier: Modifier = Modifier,
    onRecalibrated: ((ContinuousRecalibrationReport) -> Unit)? = null
) {
    var updateCycle by remember { mutableStateOf(0) }
    var showPipelineAudit by remember { mutableStateOf(false) }

    // Execute or simulate continuous recalibration for current train & delay
    val report = remember(train.trainNumber, currentDelayMinutes, updateCycle) {
        val currentStop = train.stops.getOrNull(1) ?: train.stops.first()
        val update = LiveOperationalUpdate(
            updateId = "CYCLE-${System.currentTimeMillis()}-$updateCycle",
            trainNumber = train.trainNumber,
            stationCode = currentStop.station.code,
            speedKmph = if (updateCycle % 2 == 1) 42.0 else 76.0,
            observedDelayMinutes = currentDelayMinutes + (if (updateCycle % 2 == 1) 3 else 0),
            timestampEpoch = System.currentTimeMillis() - ((3 - (updateCycle % 4)) * 60_000L)
        )
        val rep = ContinuousRecalibrationEngine.processLiveUpdate(update, train)
        onRecalibrated?.invoke(rep)
        rep
    }

    val isDelayIncrease = report.changeMinutes > 0
    val isDelayRecovery = report.changeMinutes < 0
    val changeBadgeColor = when {
        isDelayIncrease -> RailAmber
        isDelayRecovery -> RailGreen
        else -> RailCyan
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    RailCyan.copy(alpha = 0.7f),
                    changeBadgeColor.copy(alpha = 0.5f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title + Live Sync Pulsing Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RailGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CONTINUOUS RECALIBRATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Real-Time Adaptive ETA Pipeline",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RailTextPrimary
                        )
                    }
                }

                // Interactive Telemetry Ingest Trigger Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RailCyan.copy(alpha = 0.15f))
                        .border(1.dp, RailCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { updateCycle++ }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ingest Telemetry Tick",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Metric Grid: ETA, Last Updated, Previous ETA, Change
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: ETA
                RecalibrationMetricBox(
                    title = "ETA",
                    value = report.currentEtaHHmm.toStandard12Hour(),
                    highlightColor = RailCyan,
                    caption = "Target ${report.nextStationCode}",
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Last Updated
                RecalibrationMetricBox(
                    title = "LAST UPDATED",
                    value = report.lastUpdatedHHmm.toStandard12Hour(),
                    highlightColor = RailTextPrimary,
                    caption = "Telemetry Fresh",
                    modifier = Modifier.weight(1f)
                )

                // Metric 3: Previous ETA
                RecalibrationMetricBox(
                    title = "PREVIOUS ETA",
                    value = report.previousEtaHHmm.toStandard12Hour(),
                    highlightColor = RailMuted,
                    caption = "Prior Baseline",
                    modifier = Modifier.weight(1f)
                )

                // Metric 4: Change
                RecalibrationMetricBox(
                    title = "CHANGE",
                    value = if (report.changeMinutes > 0) "+${report.changeMinutes} min"
                    else if (report.changeMinutes < 0) "${report.changeMinutes} min"
                    else "0 min",
                    highlightColor = changeBadgeColor,
                    caption = if (isDelayIncrease) "Slipped" else if (isDelayRecovery) "Recovered" else "Stable",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reason Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RailDark.copy(alpha = 0.75f))
                    .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REASON",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Confidence: ${report.confidencePercent}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${report.changeReason}\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RailTextPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            // Triggered Alerts (if any)
            if (report.triggeredAlerts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                report.triggeredAlerts.forEach { alert ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(RailAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${alert.title}: ${alert.message}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 10-Step Pipeline Audit Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPipelineAudit = !showPipelineAudit },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10-Step Recalibration Flow Audit",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailCyan
                )
                Text(
                    text = if (showPipelineAudit) "▲ Hide Pipeline" else "▼ View 10 Steps",
                    fontSize = 10.sp,
                    color = RailMuted
                )
            }

            AnimatedVisibility(visible = showPipelineAudit) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = RailCardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    report.stepAuditLogs.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(RailGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${step.stepNumber}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RailGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step.stepName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailTextPrimary,
                                modifier = Modifier.width(160.dp)
                            )
                            Text(
                                text = step.detail,
                                fontSize = 9.sp,
                                color = RailTextSecondary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${step.durationMs}ms",
                                fontSize = 9.sp,
                                color = RailMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecalibrationMetricBox(
    title: String,
    value: String,
    highlightColor: Color,
    caption: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RailDark.copy(alpha = 0.7f))
            .border(1.dp, RailCardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = RailMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = highlightColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                fontSize = 9.sp,
                color = RailTextSecondary
            )
        }
    }
}
