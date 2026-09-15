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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.getCurrentEpochMillis
import org.example.project.data.NashikStationData
import org.example.project.model.*
import org.example.project.ui.theme.*

enum class CityHubTab {
    LIVE_BOARD,
    TRANSIT_GUIDE
}

@Composable
fun NashikCityHubScreen(
    onTrackTrain: (TrainJourney) -> Unit
) {
    val hubProfile = remember { NashikStationData.hubProfile }
    val allStationTrains = remember { NashikStationData.liveStationTrains }

    // Live Real-Time Indian Standard Time Engine
    var currentEpochMillis by remember { mutableStateOf(getCurrentEpochMillis()) }
    val currentIst = remember(currentEpochMillis) { IndianStandardTime.fromEpochMillis(currentEpochMillis) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentEpochMillis = getCurrentEpochMillis()
        }
    }

    var selectedCityTab by remember { mutableStateOf(CityHubTab.LIVE_BOARD) }
    var selectedPlatformFilter by remember { mutableStateOf<String?>(null) }
    var showOnlyDelayed by remember { mutableStateOf(false) }

    val filteredTrains = remember(allStationTrains, selectedPlatformFilter, showOnlyDelayed) {
        allStationTrains.filter { train ->
            val matchesPf = selectedPlatformFilter == null || train.platformNumber == selectedPlatformFilter
            val matchesDelay = !showOnlyDelayed || train.delayMinutes > 0
            matchesPf && matchesDelay
        }
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

            // City Header Banner with Live IST
            NashikCityHeader(hubProfile = hubProfile, currentIst = currentIst)

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RailCard)
                    .border(1.dp, RailCardBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedCityTab == CityHubTab.LIVE_BOARD) RailCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedCityTab = CityHubTab.LIVE_BOARD }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📡 Live Station Board • ${currentIst.time12} IST (${allStationTrains.size} Trains)",
                        fontSize = 12.sp,
                        fontWeight = if (selectedCityTab == CityHubTab.LIVE_BOARD) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedCityTab == CityHubTab.LIVE_BOARD) RailCyan else RailTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedCityTab == CityHubTab.TRANSIT_GUIDE) RailCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedCityTab = CityHubTab.TRANSIT_GUIDE }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛕 City Transit & Pilgrimage Guide",
                        fontSize = 13.sp,
                        fontWeight = if (selectedCityTab == CityHubTab.TRANSIT_GUIDE) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedCityTab == CityHubTab.TRANSIT_GUIDE) RailCyan else RailTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedCityTab) {
                CityHubTab.LIVE_BOARD -> {
                    // Filter Chips (Platforms & Delay toggle in scrollable row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pfOptions = listOf(null to "All Platforms", "Pf 1" to "Pf 1", "Pf 2" to "Pf 2", "Pf 3" to "Pf 3", "Pf 4" to "Pf 4")
                        pfOptions.forEach { (pf, label) ->
                            val isSelected = selectedPlatformFilter == pf
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) RailCyan.copy(alpha = 0.2f) else RailCard)
                                    .border(1.dp, if (isSelected) RailCyan else RailCardBorder, RoundedCornerShape(6.dp))
                                    .clickable { selectedPlatformFilter = pf }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) RailCyan else RailTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Delay toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showOnlyDelayed) RailAmber.copy(alpha = 0.25f) else RailCard)
                                .border(1.dp, if (showOnlyDelayed) RailAmber else RailCardBorder, RoundedCornerShape(6.dp))
                                .clickable { showOnlyDelayed = !showOnlyDelayed }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (showOnlyDelayed) "✓ Delayed Only" else "Show All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showOnlyDelayed) RailAmber else RailTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isWide) {
                        // 2-column grid for wide screens
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredTrains) { train ->
                                LiveStationTrainCard(
                                    train = train,
                                    currentIst = currentIst,
                                    onTrack = {
                                        // If it's Vande Bharat, select it; otherwise wrap or track
                                        if (train.trainNumber == "22223") {
                                            onTrackTrain(NashikStationData.trainVandeBharat22223)
                                        } else {
                                            // Fallback to Vande Bharat or current
                                            onTrackTrain(NashikStationData.trainVandeBharat22223)
                                        }
                                    }
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
                            items(filteredTrains) { train ->
                                LiveStationTrainCard(
                                    train = train,
                                    currentIst = currentIst,
                                    onTrack = {
                                        if (train.trainNumber == "22223") {
                                            onTrackTrain(NashikStationData.trainVandeBharat22223)
                                        } else {
                                            onTrackTrain(NashikStationData.trainVandeBharat22223)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                CityHubTab.TRANSIT_GUIDE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            Text(
                                text = "NASHIK CONNECTIVITY & TRANSIT HUBS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        items(hubProfile.transitServices) { transit ->
                            TransitGuideCard(transit = transit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NashikCityHeader(
    hubProfile: CityHubProfile,
    currentIst: IstTime
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(RailCyan.copy(alpha = 0.6f), RailAmber.copy(alpha = 0.4f)))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${hubProfile.city} Road (${hubProfile.primaryStation.code})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RailTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${hubProfile.state}",
                            fontSize = 12.sp,
                            color = RailMuted
                        )
                    }
                    Text(
                        text = "${hubProfile.zone} • ${hubProfile.division} • ${hubProfile.totalPlatforms} Platforms",
                        fontSize = 11.sp,
                        color = RailCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Station Code Chip & Live IST Clock Chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, RailCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🕒 ${currentIst.time12} IST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailCyan.copy(alpha = 0.15f))
                            .border(1.dp, RailCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(text = "NK", fontSize = 11.sp, fontWeight = FontWeight.Black, color = RailCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Thal Ghat Alert Banner
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "⛰️", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Thal Ghat (Kasara - Igatpuri) Gradient Monitor",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailAmber
                    )
                    Text(
                        text = hubProfile.ghatSectionAlert,
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
fun LiveStationTrainCard(
    train: StationTrainEntry,
    currentIst: IstTime,
    onTrack: () -> Unit
) {
    val isDelayed = train.delayMinutes > 0
    val statusColor = Color(train.status.colorHex)
    val countdownStr = currentIst.formatCountdownTo(train.predictedArrivalEta)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrack() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Train Number, Name & Platform Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${train.trainNumber} • ${train.trainName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RailTextPrimary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${train.origin.name} ➔ ${train.destination.name}",
                        fontSize = 11.sp,
                        color = RailMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Platform Badge & Live countdown status
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailCyan.copy(alpha = 0.2f))
                            .border(1.dp, RailCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = train.platformNumber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RailCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = countdownStr,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (countdownStr.startsWith("Departed")) RailMuted else RailAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Timings & ETA Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Scheduled Time (IST)", fontSize = 10.sp, color = RailMuted)
                    Text(
                        text = "${train.scheduledArrival} Arr / ${train.scheduledDeparture} Dep",
                        fontSize = 12.sp,
                        color = RailTextSecondary,
                        textDecoration = if (isDelayed) TextDecoration.LineThrough else TextDecoration.None
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Predicted ETA (IST)", fontSize = 10.sp, color = RailCyan)
                    Text(
                        text = "${train.predictedArrivalEta} Arr",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDelayed) RailAmber else RailGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason & Live Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (train.delayReason.isNotEmpty()) "• ${train.delayReason}" else "• Punctual run",
                    fontSize = 11.sp,
                    color = RailMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (isDelayed) "+${train.delayMinutes}m (${train.status.label})" else "ON TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun TransitGuideCard(transit: CityTransitLink) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = transit.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RailTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "➔ ${transit.destination}", fontSize = 12.sp, color = RailCyan)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Boarding: ${transit.pickupLocation}", fontSize = 11.sp, color = RailMuted)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = transit.transitType,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RailAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = transit.frequency, fontSize = 11.sp, color = RailGreen)
            }
        }
    }
}
