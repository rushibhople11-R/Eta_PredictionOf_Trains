package org.example.project.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.model.TrainJourney
import org.example.project.ui.theme.*

/**
 * Phase 5: Digital Boarding Pass & Live QR Journey Card
 * Specialized for passengers boarding at Nashik Road (NK).
 */
@Composable
fun DigitalBoardingPassCard(
    train: TrainJourney,
    currentDelayMinutes: Int,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Boarding Pass Title & PNR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🎫", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "Digital Boarding Pass",
                            color = RailTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Central Railway • Indian Railways e-Ticket",
                            color = RailCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Text(
                        text = "PNR: 842-9182371",
                        color = RailCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Train & Coach Info Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${train.trainNumber} • ${train.trainName}",
                            color = RailTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RailGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "CONFIRMED (CNF)",
                                color = RailGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Journey route
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "BOARDING STATION", color = RailMuted, fontSize = 10.sp)
                            Text(text = "Nashik Road (NK)", color = RailTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Platform 2", color = RailCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(text = "➔", color = RailCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "DESTINATION", color = RailMuted, fontSize = 10.sp)
                            Text(text = train.stops.lastOrNull()?.station?.name ?: "Sainagar Shirdi", color = RailTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Scheduled Stop", color = RailTextSecondary, fontSize = 11.sp)
                        }
                    }

                    // Seat details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Coach: C2 (Executive CC)", color = RailTextSecondary, fontSize = 12.sp)
                        Text(text = "Seat: 44 (Window)", color = RailTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // QR Code Matrix Simulation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Station Gate QR Code",
                        color = RailTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan at Nashik Road Automatic Ticket Barrier",
                        color = RailMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Valid for Boarding • IRCTC Certified",
                        color = RailGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Stylized 6x6 QR pattern
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(5) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                repeat(5) { col ->
                                    val isPixelOn = (row + col) % 2 == 0 || (row == 0 || row == 4 || col == 0 || col == 4)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isPixelOn) Color.Black else Color.White)
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
