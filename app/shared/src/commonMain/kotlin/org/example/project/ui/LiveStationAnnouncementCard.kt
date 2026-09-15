package org.example.project.ui

import androidx.compose.animation.*
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
import kotlinx.coroutines.delay
import org.example.project.data.NashikStationData
import org.example.project.engine.AnnouncementLanguage
import org.example.project.engine.StationAnnouncementEngine
import org.example.project.model.StationTrainEntry
import org.example.project.ui.theme.*

/**
 * Phase 5: Live Station Audio & Public Address Announcement Card
 * Tri-lingual (Marathi, Hindi, English) station announcements with acoustic visualizer for Nashik Road (NK).
 */
@Composable
fun LiveStationAnnouncementCard(
    train: StationTrainEntry = NashikStationData.liveStationTrains.first(),
    modifier: Modifier = Modifier
) {
    var selectedLanguage by remember { mutableStateOf(AnnouncementLanguage.MARATHI) }
    var isPlaying by remember { mutableStateOf(false) }

    // Auto-advance announcement playback animation
    val infiniteTransition = rememberInfiniteTransition(label = "SoundWave")
    val waveHeight1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "Wave1"
    )
    val waveHeight2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "Wave2"
    )
    val waveHeight3 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse),
        label = "Wave3"
    )
    val waveHeight4 by infiniteTransition.animateFloat(
        initialValue = 22f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "Wave4"
    )

    val currentAnnouncement = remember(train, selectedLanguage) {
        StationAnnouncementEngine.generateArrivalAnnouncement(train, selectedLanguage)
    }

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
            // Header Row: PA system title & Audio Play Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "📢", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "Station Public Address (PA) Audio",
                            color = RailTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nashik Road (NK) • Station Broadcast System",
                            color = RailCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Play / Pause Sound Wave Trigger
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPlaying) RailGreen.copy(alpha = 0.2f) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (isPlaying) RailGreen else Color(0xFF334155)),
                    modifier = Modifier.clickable { isPlaying = !isPlaying }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isPlaying) "🔊 BROADCASTING" else "▶ PLAY CHIME",
                            color = if (isPlaying) RailGreen else RailCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Language Selector Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnnouncementLanguage.values().forEach { lang ->
                    val isSelected = selectedLanguage == lang
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLanguage = lang },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) RailCyan.copy(alpha = 0.2f) else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) RailCyan else Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = lang.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lang.nativeName,
                                color = if (isSelected) RailTextPrimary else RailMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sound Wave Graphic (Animated when playing)
            if (isPlaying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔔 Ding • Dong • Ding • Dong", color = RailAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight1.dp).background(RailCyan, CircleShape))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight2.dp).background(RailCyan, CircleShape))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight3.dp).background(RailCyan, CircleShape))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight4.dp).background(RailCyan, CircleShape))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight2.dp).background(RailCyan, CircleShape))
                    Box(modifier = Modifier.width(4.dp).height(waveHeight1.dp).background(RailCyan, CircleShape))
                }
            }

            // Authentic Announcement Broadcast Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
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
                        Text(
                            text = "OFFICIAL STATION BROADCAST",
                            color = RailAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Plat: ${train.platformNumber} • Train: ${train.trainNumber}",
                            color = RailTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = currentAnnouncement.text,
                        color = RailTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spoken Digits: ${currentAnnouncement.phoneticCallout}",
                            color = RailCyan,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            text = if (train.delayMinutes > 5) "⚠ Delay Broadcast" else "✔ On-Time Arrival",
                            color = if (train.delayMinutes > 5) RailAmber else RailGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
