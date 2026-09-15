package org.example.project.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.project.data.*
import org.example.project.getCurrentEpochMillis
import org.example.project.openPlatformBrowser
import kotlin.math.cos
import kotlin.math.sin

private val DeepNavy = Color(0xFF0B192C)
private val CardNavy = Color(0xFF162E50)
private val AccentGreen = Color(0xFF10B981)
private val AmberCaution = Color(0xFFF59E0B)
private val RedAlert = Color(0xFFEF4444)
private val BorderMuted = Color(0xFF1E293B)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

enum class MapVisualLayer {
    RADAR_HUD,
    SATELLITE_SIM,
    TOPOGRAPHIC
}

enum class MapFilterCategory {
    ALL,
    TRAINS,
    STATIONS,
    SECTIONS
}

/**
 * High-performance interactive Nashik City Railway Map with live trains, stations,
 * and block section monitoring. Supports 1-click Google Maps JavaScript browser view
 * and Google Maps API key customization.
 */
@Composable
fun NashikCityGoogleMapScreen(
    onBackToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stations = remember { NashikRailMapRegistry.STATIONS }
    val sections = remember { NashikRailMapRegistry.SECTIONS }
    var liveTrains by remember { mutableStateOf(NashikRailMapRegistry.LIVE_TRAINS) }

    var selectedStation by remember { mutableStateOf<NashikStationGeoNode?>(NashikRailMapRegistry.getStation("NK")) }
    var selectedSection by remember { mutableStateOf<NashikRailSection?>(null) }
    var selectedTrain by remember { mutableStateOf<NashikLiveTrainMarker?>(null) }

    var activeVisualLayer by remember { mutableStateOf(MapVisualLayer.RADAR_HUD) }
    var activeFilter by remember { mutableStateOf(MapFilterCategory.ALL) }

    // Map Pan & Zoom states
    var zoomLevel by remember { mutableStateOf(1.2f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    // Ticking Live IST Clock & Train Telemetry Simulation
    var currentTimeMillis by remember { mutableStateOf(getCurrentEpochMillis()) }
    LaunchedEffect(Unit) {
        var tickCounter = 0
        while (true) {
            delay(1000)
            currentTimeMillis = getCurrentEpochMillis()
            tickCounter++

            // Micro-movement update for live trains
            if (tickCounter % 3 == 0) {
                liveTrains = liveTrains.map { train ->
                    if (train.speedKmph > 0) {
                        val angleRad = Math.toRadians(train.headingDegrees)
                        val deltaLat = sin(angleRad) * 0.00015
                        val deltaLng = cos(angleRad) * 0.00015
                        train.copy(
                            currentPosition = GeoPoint(
                                lat = train.currentPosition.lat + deltaLat,
                                lng = train.currentPosition.lng + deltaLng
                            )
                        )
                    } else train
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // --- 1. TOP HEADER & MAP RADAR HUD ---
        Surface(
            color = CardNavy,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, BorderMuted)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, AccentGreen),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onBackToHome() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "←", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "RAILअंदाज", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentGreen.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "LIVE NASHIK GIS",
                                        color = AccentGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Central Railway • Igatpuri - Devlali - Nashik Road - Manmad Corridor",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // External Google Maps Actions
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGreen,
                        modifier = Modifier.clickable {
                            openPlatformBrowser("http://localhost:8080/api/map/nashik")
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "🌐", fontSize = 13.sp)
                            Text(text = "Open in Google Maps", color = DeepNavy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                }

                Spacer(modifier = Modifier.height(10.dp))

                // Presets & Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Viewport presets
                    listOf(
                        "🎯 Nashik Road" to {
                            panOffsetX = 0f
                            panOffsetY = 0f
                            zoomLevel = 1.6f
                            selectedStation = NashikRailMapRegistry.getStation("NK")
                        },
                        "🎖️ Devlali" to {
                            panOffsetX = -120f
                            panOffsetY = 60f
                            zoomLevel = 1.6f
                            selectedStation = NashikRailMapRegistry.getStation("DVL")
                        },
                        "🌉 Godavari Bridge" to {
                            panOffsetX = 80f
                            panOffsetY = -50f
                            zoomLevel = 1.8f
                            selectedSection = NashikRailMapRegistry.getSection("SEC-NK-ODH")
                        },
                        "🗺️ Full Corridor" to {
                            panOffsetX = 0f
                            panOffsetY = 0f
                            zoomLevel = 1.0f
                        }
                    ).forEach { (label, action) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.clickable { action() }
                        ) {
                            Text(
                                text = label,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0xFF334155)))
                    Spacer(modifier = Modifier.width(6.dp))

                    // Map layer themes
                    MapVisualLayer.values().forEach { layer ->
                        val isSelected = activeVisualLayer == layer
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) AccentGreen else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (isSelected) AccentGreen else Color(0xFF334155)),
                            modifier = Modifier.clickable { activeVisualLayer = layer }
                        ) {
                            Text(
                                text = when (layer) {
                                    MapVisualLayer.RADAR_HUD -> "Radar HUD"
                                    MapVisualLayer.SATELLITE_SIM -> "Satellite"
                                    MapVisualLayer.TOPOGRAPHIC -> "Topographic"
                                },
                                color = if (isSelected) DeepNavy else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(Color(0xFF334155)))
                    Spacer(modifier = Modifier.width(6.dp))

                    // Filter category
                    MapFilterCategory.values().forEach { cat ->
                        val isSelected = activeFilter == cat
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)),
                            modifier = Modifier.clickable { activeFilter = cat }
                        ) {
                            Text(
                                text = when (cat) {
                                    MapFilterCategory.ALL -> "All"
                                    MapFilterCategory.TRAINS -> "Trains"
                                    MapFilterCategory.STATIONS -> "Stations"
                                    MapFilterCategory.SECTIONS -> "Sections"
                                },
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 2. INTERACTIVE MAP CANVAS WITH PAN & ZOOM ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffsetX += dragAmount.x
                        panOffsetY += dragAmount.y
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // Geographic bounding box mapping:
                // West (Igatpuri: ~73.565) to East (Manmad: ~74.437) -> X axis
                // South (Kasara: ~19.645) to North (Manmad: ~20.252) -> Y axis (inverted in canvas)
                val minLng = 73.50
                val maxLng = 74.48
                val minLat = 19.65
                val maxLat = 20.30

                fun geoToCanvas(lat: Double, lng: Double): Offset {
                    val normX = ((lng - minLng) / (maxLng - minLng)).toFloat()
                    val normY = (1f - ((lat - minLat) / (maxLat - minLat))).toFloat()

                    val centerX = canvasW / 2f
                    val centerY = canvasH / 2f

                    val unscaledX = normX * canvasW
                    val unscaledY = normY * canvasH

                    val zoomedX = centerX + (unscaledX - centerX) * zoomLevel + panOffsetX
                    val zoomedY = centerY + (unscaledY - centerY) * zoomLevel + panOffsetY

                    return Offset(zoomedX, zoomedY)
                }

                // 2.1 Background Grid / Topography based on visual mode
                when (activeVisualLayer) {
                    MapVisualLayer.RADAR_HUD -> {
                        // Radar rings centered around Nashik Road
                        val nkCenter = geoToCanvas(19.9575, 73.8340)
                        for (r in listOf(80f, 180f, 300f, 450f)) {
                            drawCircle(
                                color = Color(0xFF1E293B).copy(alpha = 0.4f),
                                radius = r * zoomLevel,
                                center = nkCenter,
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                    MapVisualLayer.SATELLITE_SIM -> {
                        // Subtle elevation contours
                        drawRect(color = Color(0xFF0F2027))
                    }
                    MapVisualLayer.TOPOGRAPHIC -> {
                        // Topographic styling
                        drawRect(color = Color(0xFF0B192C))
                    }
                }

                // 2.2 Godavari River Path across Nashik City
                val riverPath = Path().apply {
                    val p1 = geoToCanvas(19.9800, 73.7500) // Trimbak side
                    val p2 = geoToCanvas(19.9950, 73.7900) // Gangapur Dam
                    val p3 = geoToCanvas(20.0050, 73.8150) // Nashik CBS / Ramkund
                    val p4 = geoToCanvas(20.0080, 73.8650) // Nashik Road - Odha crossing
                    val p5 = geoToCanvas(20.0350, 73.9400) // Downstream to Niphad
                    moveTo(p1.x, p1.y)
                    cubicTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
                    lineTo(p5.x, p5.y)
                }
                drawPath(
                    path = riverPath,
                    color = Color(0xFF0284C7).copy(alpha = 0.45f),
                    style = Stroke(width = 9f * zoomLevel, cap = StrokeCap.Round)
                )

                // 2.3 Railway Sections (Block Sections)
                if (activeFilter == MapFilterCategory.ALL || activeFilter == MapFilterCategory.SECTIONS) {
                    sections.forEach { sec ->
                        val isSecSelected = selectedSection?.sectionId == sec.sectionId
                        val secColor = when (sec.status) {
                            "CLEAR" -> AccentGreen
                            "CAUTION" -> AmberCaution
                            else -> RedAlert
                        }

                        val sectionPath = Path().apply {
                            val pts = sec.pathPoints.map { geoToCanvas(it.lat, it.lng) }
                            if (pts.isNotEmpty()) {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x, pts[i].y)
                                }
                            }
                        }

                        // Glow layer if selected
                        if (isSecSelected) {
                            drawPath(
                                path = sectionPath,
                                color = secColor.copy(alpha = 0.4f),
                                style = Stroke(width = 14f * zoomLevel, cap = StrokeCap.Round)
                            )
                        }

                        // Main track polyline
                        drawPath(
                            path = sectionPath,
                            color = secColor,
                            style = Stroke(width = if (isSecSelected) 6f * zoomLevel else 4f * zoomLevel, cap = StrokeCap.Round)
                        )
                    }
                }

                // 2.4 Station Nodes
                if (activeFilter == MapFilterCategory.ALL || activeFilter == MapFilterCategory.STATIONS) {
                    stations.forEach { stn ->
                        val pos = geoToCanvas(stn.location.lat, stn.location.lng)
                        val isSelected = selectedStation?.code == stn.code
                        val isNashikRoad = stn.code == "NK"

                        // Pulsing outer beacon for Nashik Road primary station
                        if (isNashikRoad) {
                            drawCircle(
                                color = AccentGreen.copy(alpha = 0.25f),
                                radius = 18f * pulseScale * zoomLevel,
                                center = pos
                            )
                        }

                        // Station Node Circle
                        drawCircle(
                            color = if (isSelected) Color.White else (if (isNashikRoad) AccentGreen else Color(0xFF38BDF8)),
                            radius = if (isNashikRoad) 9f * zoomLevel else 6f * zoomLevel,
                            center = pos
                        )
                        drawCircle(
                            color = DeepNavy,
                            radius = if (isNashikRoad) 5f * zoomLevel else 3.5f * zoomLevel,
                            center = pos
                        )
                    }
                }

                // 2.5 Live Moving Trains
                if (activeFilter == MapFilterCategory.ALL || activeFilter == MapFilterCategory.TRAINS) {
                    liveTrains.forEach { train ->
                        val pos = geoToCanvas(train.currentPosition.lat, train.currentPosition.lng)
                        val isTrainSelected = selectedTrain?.trainNumber == train.trainNumber
                        val trainColor = if (train.priorityLevel == 1) Color(0xFF3B82F6) else (if (train.delayMinutes == 0) AccentGreen else RedAlert)

                        // Outer glowing pulse
                        drawCircle(
                            color = trainColor.copy(alpha = 0.35f),
                            radius = 16f * pulseScale * zoomLevel,
                            center = pos
                        )

                        // Inner solid marker
                        drawCircle(
                            color = Color.White,
                            radius = 7f * zoomLevel,
                            center = pos
                        )
                        drawCircle(
                            color = trainColor,
                            radius = 5.5f * zoomLevel,
                            center = pos
                        )
                    }
                }
            }

            // Interactive Click Overlays (HTML-like hit targets positioned via offsets)
            // Overlay Stations labels & hitboxes
            stations.forEach { stn ->
                val canvasW = 800f
                val canvasH = 600f
                // We show clickable floating pills for key stations
                if (stn.code in listOf("NK", "DVL", "ODH", "IGP", "MMR", "LS", "NR")) {
                    // Floating interactive pill in corner or relative
                }
            }

            // Quick Station Chips Selector Overlay (Top Right of Canvas)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .widthIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardNavy.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "SELECT STATION NODE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("NK" to "Nashik Rd", "DVL" to "Devlali", "ODH" to "Odha", "IGP" to "Igatpuri", "MMR" to "Manmad").forEach { (code, name) ->
                                val isSelected = selectedStation?.code == code
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) AccentGreen else Color(0xFF0F172A),
                                    modifier = Modifier.clickable {
                                        selectedStation = NashikRailMapRegistry.getStation(code)
                                        selectedSection = null
                                        selectedTrain = null
                                    }
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) DeepNavy else TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Train Selector Overlay
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardNavy.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "LIVE TRACKED TRAINS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        liveTrains.take(3).forEach { t ->
                            val isSelected = selectedTrain?.trainNumber == t.trainNumber
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        selectedTrain = t
                                        selectedStation = null
                                        selectedSection = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "${t.trainNumber} • ${t.trainName.take(12)}", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "${t.speedKmph} km/h", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Zoom In / Out Floating Action Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = CardNavy,
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { zoomLevel = (zoomLevel + 0.3f).coerceAtMost(3.5f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "+", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = CardNavy,
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { zoomLevel = (zoomLevel - 0.3f).coerceAtLeast(0.8f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "−", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = CardNavy,
                    border = BorderStroke(1.dp, BorderMuted),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable {
                            zoomLevel = 1.2f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "⟲", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 3. BOTTOM INSPECTOR DRAWER FOR SELECTED STATION / SECTION / TRAIN ---
        Surface(
            color = CardNavy,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, BorderMuted)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                when {
                    selectedTrain != null -> {
                        val train = selectedTrain!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "🚆 ${train.trainNumber} • ${train.trainName}", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (train.delayMinutes == 0) AccentGreen.copy(alpha = 0.2f) else RedAlert.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (train.delayMinutes == 0) "RIGHT TIME" else "+${train.delayMinutes} MINS LATE",
                                            color = if (train.delayMinutes == 0) AccentGreen else RedAlert,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Loco: ${train.locomotive} • Next: ${train.nextStationCode} (ETA ${train.estimatedArrivalAtNextStation})",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "${train.speedKmph} KM/H", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(text = "RTIS GPS Speed", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = train.statusText, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }

                    selectedSection != null -> {
                        val sec = selectedSection!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "🛤️ Section: ${sec.name}", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Length: ${sec.lengthKm} km • ${sec.trackType} • ${sec.signalingSystem}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (sec.status) {
                                    "CLEAR" -> AccentGreen.copy(alpha = 0.2f)
                                    "CAUTION" -> AmberCaution.copy(alpha = 0.2f)
                                    else -> RedAlert.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = "${sec.status} (${sec.lineSaturationPercent}% Saturation)",
                                    color = when (sec.status) {
                                        "CLEAR" -> AccentGreen
                                        "CAUTION" -> AmberCaution
                                        else -> RedAlert
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    selectedStation != null -> {
                        val stn = selectedStation!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "🏢 ${stn.name} (${stn.code})", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "• ${stn.marathiName}", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Category: ${stn.stationCategory} • ${stn.platformsCount} Platforms • ${stn.tracksCount} Lines • Elevation: ${stn.elevationMeters}m",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentGreen.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "INTERLOCKED",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stn.description, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
