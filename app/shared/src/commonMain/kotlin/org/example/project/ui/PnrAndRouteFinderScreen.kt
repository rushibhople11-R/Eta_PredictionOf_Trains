package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.data.NashikStationData
import org.example.project.data.SamplePnrData
import org.example.project.data.SampleRailwayData
import org.example.project.model.*
import org.example.project.ui.theme.*

enum class SearchMode {
    PNR_STATUS,
    STATION_TO_STATION
}

@Composable
fun PnrAndRouteFinderScreen(
    onSelectTrainAndTrack: (TrainJourney) -> Unit
) {
    var searchMode by remember { mutableStateOf(SearchMode.PNR_STATUS) }

    // PNR state - Defaults to authentic 22223 CSMT - Nashik Road Vande Bharat booking
    var pnrInput by remember { mutableStateOf("4529184029") }
    var currentPnrDetails by remember { mutableStateOf<PnrDetails?>(SamplePnrData.findPnr("4529184029")) }
    var pnrError by remember { mutableStateOf<String?>(null) }

    // Station to Station state - Exclusively Nashik Road and connecting corridor junctions
    val availableStations = remember { NashikStationData.allNashikCorridorStations }

    var fromStation by remember { mutableStateOf(NashikStationData.CSMT) }
    var toStation by remember { mutableStateOf(NashikStationData.NK) }

    val allTrains = remember { NashikStationData.allNashikTrains }
    val routeResults = remember(fromStation, toStation) {
        RouteSearchEngine.findTrainsBetweenStations(
            trains = allTrains,
            fromStationCode = fromStation.code,
            toStationCode = toStation.code
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RailDark)
    ) {
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
                text = "SEARCH & JOURNEY PLANNER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RailCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Find Routes & Check Live PNR",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RailTextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Top Segment Switcher (PNR vs Station Route)
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
                        .background(if (searchMode == SearchMode.PNR_STATUS) RailCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { searchMode = SearchMode.PNR_STATUS }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎫 Check PNR Status",
                        fontSize = 13.sp,
                        fontWeight = if (searchMode == SearchMode.PNR_STATUS) FontWeight.Bold else FontWeight.Medium,
                        color = if (searchMode == SearchMode.PNR_STATUS) RailCyan else RailTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (searchMode == SearchMode.STATION_TO_STATION) RailCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { searchMode = SearchMode.STATION_TO_STATION }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚉 Station to Station",
                        fontSize = 13.sp,
                        fontWeight = if (searchMode == SearchMode.STATION_TO_STATION) FontWeight.Bold else FontWeight.Medium,
                        color = if (searchMode == SearchMode.STATION_TO_STATION) RailCyan else RailTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Content
            when (searchMode) {
                SearchMode.PNR_STATUS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // PNR Input Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = RailCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "ENTER 10-DIGIT PNR NUMBER",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RailMuted,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = pnrInput,
                                            onValueChange = {
                                                pnrInput = it.take(10)
                                                pnrError = null
                                            },
                                            placeholder = { Text("e.g. 2849103842") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = RailCyan,
                                                unfocusedBorderColor = RailCardBorder
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Button(
                                            onClick = {
                                                val found = SamplePnrData.findPnr(pnrInput)
                                                if (found != null) {
                                                    currentPnrDetails = found
                                                    pnrError = null
                                                } else {
                                                    pnrError = "PNR not found in sample database. Try one of the sample numbers below."
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = RailCyan),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = "Check", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    pnrError?.let {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = it, fontSize = 11.sp, color = RailRed)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quick Test Pills (Scrollable)
                                    Text(text = "Try sample PNRs:", fontSize = 11.sp, color = RailMuted)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SamplePnrData.allSamplePnrs.forEach { sample ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1E293B))
                                                    .clickable {
                                                        pnrInput = sample.pnrNumber
                                                        currentPnrDetails = sample
                                                        pnrError = null
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "${sample.pnrNumber} (${sample.trainNumber})",
                                                    fontSize = 11.sp,
                                                    color = RailCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // PNR Result Card
                        currentPnrDetails?.let { pnr ->
                            item {
                                PnrResultCard(
                                    pnr = pnr,
                                    onTrackLive = {
                                        val targetTrain = allTrains.find { it.trainNumber == pnr.trainNumber }
                                        if (targetTrain != null) {
                                            onSelectTrainAndTrack(targetTrain)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                SearchMode.STATION_TO_STATION -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Station Selector Box
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = RailCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RailCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // From Station Box
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "FROM STATION", fontSize = 10.sp, color = RailMuted, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            StationDropdownSelector(
                                                selectedStation = fromStation,
                                                stations = availableStations,
                                                onSelect = { fromStation = it }
                                            )
                                        }

                                        // Swap Button
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E293B))
                                                .clickable {
                                                    val temp = fromStation
                                                    fromStation = toStation
                                                    toStation = temp
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "⇄", fontSize = 18.sp, color = RailCyan, fontWeight = FontWeight.Bold)
                                        }

                                        // To Station Box
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "TO STATION", fontSize = 10.sp, color = RailMuted, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            StationDropdownSelector(
                                                selectedStation = toStation,
                                                stations = availableStations,
                                                onSelect = { toStation = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Results Header
                        item {
                            Text(
                                text = "AVAILABLE TRAINS (${routeResults.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RailCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        if (routeResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No direct trains found between ${fromStation.name} and ${toStation.name}. Try changing the destination or swapping direction.",
                                        fontSize = 13.sp,
                                        color = RailMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(routeResults) { result ->
                                RouteResultCard(
                                    result = result,
                                    onTrackLive = { onSelectTrainAndTrack(result.train) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PnrResultCard(
    pnr: PnrDetails,
    onTrackLive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RailCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, RailCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PNR: ${pnr.pnrNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailCyan
                    )
                    Text(
                        text = "${pnr.trainNumber} • ${pnr.trainName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary
                    )
                }

                Text(
                    text = pnr.trainType.displayName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RailAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Journey Route & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Boarding At", fontSize = 10.sp, color = RailMuted)
                    Text(text = "${pnr.sourceStation.name} (${pnr.sourceStation.code})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RailTextPrimary)
                    Text(text = "Departs: ${pnr.departureTime}", fontSize = 11.sp, color = RailCyan)
                }

                Text(text = "➔", fontSize = 16.sp, color = RailMuted, modifier = Modifier.padding(top = 10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Destination", fontSize = 10.sp, color = RailMuted)
                    Text(text = "${pnr.destinationStation.name} (${pnr.destinationStation.code})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RailTextPrimary)
                    Text(text = "Arrival: ${pnr.arrivalTime}", fontSize = 11.sp, color = RailGreen)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Passenger Details Table
            Text(text = "PASSENGER BOOKING DETAILS", fontSize = 10.sp, color = RailMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            pnr.passengers.forEach { pax ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "${pax.passengerNumber}. ${pax.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RailTextPrimary)
                        Text(text = "Coach ${pax.coach} • Berth ${pax.berth} (${pax.berthType})", fontSize = 11.sp, color = RailMuted)
                    }

                    Text(
                        text = pax.status.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(pax.status.colorHex),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(pax.status.colorHex).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button: Track Live Train
            Button(
                onClick = onTrackLive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RailCyan)
            ) {
                Text(
                    text = "⚡ Track Live Train & Predictive ETA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun RouteResultCard(
    result: RouteQueryResult,
    onTrackLive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackLive() },
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
                Column {
                    Text(
                        text = "${result.train.trainNumber} • ${result.train.trainName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RailTextPrimary
                    )
                    Text(
                        text = "${result.distanceKm} km • Duration ${result.durationFormatted}",
                        fontSize = 11.sp,
                        color = RailMuted
                    )
                }

                Text(
                    text = result.train.trainType.displayName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailAmber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RailAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = RailCardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Departs (${result.fromStation.code})", fontSize = 10.sp, color = RailMuted)
                    Text(text = result.departureTime, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RailCyan)
                }

                Text(
                    text = "➔ ${result.durationFormatted} ➔",
                    fontSize = 11.sp,
                    color = RailMuted
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Arrives (${result.toStation.code})", fontSize = 10.sp, color = RailMuted)
                    Text(text = result.arrivalTime, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RailGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Avg section delay: ~${result.typicalDelayMinutes}m",
                    fontSize = 11.sp,
                    color = RailTextSecondary
                )

                Text(
                    text = "Tap to Track Live ➔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailCyan
                )
            }
        }
    }
}

@Composable
fun StationDropdownSelector(
    selectedStation: Station,
    stations: List<Station>,
    onSelect: (Station) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedStation.code} - ${selectedStation.name.take(10)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RailTextPrimary,
                    maxLines = 1
                )
                Text(text = "▼", fontSize = 9.sp, color = RailMuted)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(RailCard)
        ) {
            stations.forEach { station ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${station.code} - ${station.name}",
                            fontSize = 12.sp,
                            color = RailTextPrimary
                        )
                    },
                    onClick = {
                        onSelect(station)
                        expanded = false
                    }
                )
            }
        }
    }
}
