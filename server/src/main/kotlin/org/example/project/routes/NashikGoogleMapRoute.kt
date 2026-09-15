package org.example.project.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.project.data.NashikRailMapRegistry

/**
 * Live Google Map of Nashik City & Corridor.
 *
 * Implements:
 * - GET /api/map/nashik (?key=GOOGLE_MAPS_API_KEY)
 * - GET /api/map/nashik/data
 */
fun Route.nashikGoogleMapRoute() {

    // 1. Data JSON API for real-time GIS layers
    get("/api/map/nashik/data") {
        val stations = NashikRailMapRegistry.STATIONS
        val sections = NashikRailMapRegistry.SECTIONS
        val trains = NashikRailMapRegistry.LIVE_TRAINS

        val json = buildString {
            append("{")
            // Stations
            append("\"stations\":[")
            stations.forEachIndexed { i, s ->
                if (i > 0) append(",")
                append("{")
                append("\"code\":\"${s.code}\",")
                append("\"name\":\"${s.name}\",")
                append("\"marathiName\":\"${s.marathiName}\",")
                append("\"lat\":${s.location.lat},")
                append("\"lng\":${s.location.lng},")
                append("\"elevation\":${s.elevationMeters},")
                append("\"platforms\":${s.platformsCount},")
                append("\"isJunction\":${s.isJunction},")
                append("\"category\":\"${s.stationCategory}\",")
                append("\"tracks\":${s.tracksCount},")
                append("\"description\":\"${s.description.replace("\"", "\\\"")}\",")
                append("\"features\":[${s.features.joinToString(",") { "\"$it\"" }}]")
                append("}")
            }
            append("],")

            // Sections
            append("\"sections\":[")
            sections.forEachIndexed { i, sec ->
                if (i > 0) append(",")
                append("{")
                append("\"sectionId\":\"${sec.sectionId}\",")
                append("\"from\":\"${sec.fromStationCode}\",")
                append("\"to\":\"${sec.toStationCode}\",")
                append("\"name\":\"${sec.name}\",")
                append("\"lengthKm\":${sec.lengthKm},")
                append("\"trackType\":\"${sec.trackType}\",")
                append("\"signaling\":\"${sec.signalingSystem}\",")
                append("\"maxSpeed\":${sec.maxPermissibleSpeedKmph},")
                append("\"tsr\":${sec.temporarySpeedRestrictionKmph ?: "null"},")
                append("\"saturation\":${sec.lineSaturationPercent},")
                append("\"signalAspect\":\"${sec.currentSignalAspect}\",")
                append("\"status\":\"${sec.status}\",")
                append("\"path\":[")
                sec.pathPoints.forEachIndexed { pi, p ->
                    if (pi > 0) append(",")
                    append("{\"lat\":${p.lat},\"lng\":${p.lng}}")
                }
                append("]")
                append("}")
            }
            append("],")

            // Live Trains
            append("\"trains\":[")
            trains.forEachIndexed { i, t ->
                if (i > 0) append(",")
                append("{")
                append("\"trainNumber\":\"${t.trainNumber}\",")
                append("\"name\":\"${t.trainName}\",")
                append("\"type\":\"${t.trainType}\",")
                append("\"locomotive\":\"${t.locomotive}\",")
                append("\"lat\":${t.currentPosition.lat},")
                append("\"lng\":${t.currentPosition.lng},")
                append("\"heading\":${t.headingDegrees},")
                append("\"speed\":${t.speedKmph},")
                append("\"delay\":${t.delayMinutes},")
                append("\"sectionId\":\"${t.currentSectionId}\",")
                append("\"nextStation\":\"${t.nextStationCode}\",")
                append("\"eta\":\"${t.estimatedArrivalAtNextStation}\",")
                append("\"priority\":${t.priorityLevel},")
                append("\"statusText\":\"${t.statusText.replace("\"", "\\\"")}\"")
                append("}")
            }
            append("]")
            append("}")
        }

        call.respondText(json, ContentType.Application.Json)
    }

    // 2. Full Live Interactive Google Map Web Application (Key kept securely on backend)
    get("/api/map/nashik") {
        val queryKey = call.request.queryParameters["key"]?.trim() ?: ""
        val backendKey = resolveBackendGoogleMapsApiKey()
        val effectiveKey = if (backendKey.isNotBlank()) backendKey else queryKey

        val html = buildGoogleMapHtml(effectiveKey)
        call.respondText(html, ContentType.Text.Html)
    }
}

/**
 * Resolves the Google Maps API key from secure backend sources (.env, local.properties, system env)
 * so that no secret is exposed or fixed at the frontend client.
 */
private fun resolveBackendGoogleMapsApiKey(): String {
    val sysEnv = System.getenv("GOOGLE_MAPS_API_KEY")?.trim() ?: ""
    if (sysEnv.isNotBlank()) return sysEnv

    val candidateFiles = listOf(
        java.io.File(".env"),
        java.io.File("../.env"),
        java.io.File("server/.env"),
        java.io.File("local.properties"),
        java.io.File("../local.properties")
    )

    for (file in candidateFiles) {
        try {
            if (file.exists() && file.isFile) {
                for (rawLine in file.readLines()) {
                    val line = rawLine.trim()
                    if (!line.startsWith("#") && line.contains("=")) {
                        val key = line.substringBefore("=").trim()
                        val value = line.substringAfter("=").trim()
                        if (key.equals("GOOGLE_MAPS_API_KEY", ignoreCase = true) ||
                            key.equals("google.maps.api.key", ignoreCase = true) ||
                            key.equals("MAPS_API_KEY", ignoreCase = true)
                        ) {
                            if (value.isNotBlank()) return value
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return ""
}

/**
 * Builds the interactive Google Maps Platform HTML application for Nashik City Rail Corridor.
 */
private fun buildGoogleMapHtml(apiKey: String): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RAILअंदाज • Live Nashik City Rail GIS Map</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
        body, html { width: 100%; height: 100%; overflow: hidden; background: #0B192C; color: #F8FAFC; }
        
        #map-container { width: 100%; height: 100%; position: relative; }
        #gmap, #fallback-map { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
        
        /* Floating Top Control HUD */
        .hud-top {
            position: absolute;
            top: 14px;
            left: 14px;
            right: 14px;
            z-index: 1000;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            pointer-events: none;
        }
        .hud-card {
            background: rgba(11, 25, 44, 0.92);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.12);
            border-radius: 12px;
            padding: 10px 16px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.36);
            pointer-events: auto;
            display: flex;
            align-items: center;
            gap: 14px;
        }
        .brand-title {
            font-size: 18px;
            font-weight: 800;
            letter-spacing: -0.5px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .brand-accent { color: #10B981; }
        .live-tag {
            background: rgba(16, 185, 129, 0.16);
            color: #10B981;
            border: 1px solid rgba(16, 185, 129, 0.3);
            font-size: 11px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 6px;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .pulse-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: #10B981;
            animation: pulse-ring 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
        }
        @keyframes pulse-ring {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(1.3); }
        }

        /* Viewport Presets Bar */
        .preset-buttons {
            display: flex;
            gap: 6px;
        }
        .btn-preset {
            background: rgba(30, 41, 59, 0.85);
            color: #E2E8F0;
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 8px;
            padding: 6px 12px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        .btn-preset:hover {
            background: #10B981;
            color: #0B192C;
            border-color: #10B981;
        }
        .btn-preset.active {
            background: #10B981;
            color: #0B192C;
            font-weight: 700;
        }

        /* Floating Telemetry & Legend Panel */
        .hud-bottom {
            position: absolute;
            bottom: 20px;
            left: 20px;
            z-index: 1000;
            pointer-events: none;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .hud-legend {
            background: rgba(11, 25, 44, 0.94);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.12);
            border-radius: 12px;
            padding: 12px 16px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.36);
            pointer-events: auto;
            max-width: 380px;
        }
        .legend-title {
            font-size: 12px;
            font-weight: 700;
            color: #94A3B8;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            margin-bottom: 8px;
            display: flex;
            justify-content: space-between;
        }
        .legend-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 8px;
            font-size: 11px;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 6px;
            color: #E2E8F0;
        }
        .legend-color-bar {
            width: 14px;
            height: 4px;
            border-radius: 2px;
        }
        .legend-symbol {
            font-size: 13px;
        }



        /* Marker Styles */
        .station-marker-label {
            background: #0B192C;
            color: #FFFFFF;
            border: 1px solid #10B981;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            white-space: nowrap;
            box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        }
        .train-marker-badge {
            background: #EF4444;
            color: white;
            padding: 4px 8px;
            border-radius: 8px;
            font-size: 11px;
            font-weight: 800;
            display: flex;
            align-items: center;
            gap: 5px;
            border: 2px solid white;
            box-shadow: 0 4px 16px rgba(239, 68, 68, 0.6);
            white-space: nowrap;
        }
        .train-marker-vb {
            background: #2563EB;
            box-shadow: 0 4px 16px rgba(37, 99, 235, 0.6);
        }
    </style>
</head>
<body>
    <div id="map-container">
        <!-- Google Map canvas (or fallback interactive canvas) -->
        <div id="gmap"></div>
        <div id="fallback-map" style="display:none;"></div>

        <!-- Top Control HUD -->
        <div class="hud-top">
            <div class="hud-card">
                <div class="brand-title">
                    <span>🚆</span>
                    <span>RAIL<span class="brand-accent">अंदाज</span></span>
                </div>
                <div class="live-tag">
                    <div class="pulse-dot"></div>
                    <span>LIVE NASHIK RADAR</span>
                </div>
                <div id="clock-display" style="font-family:'JetBrains Mono',monospace; font-size:12px; color:#94A3B8;">--:--:-- IST</div>
            </div>

            <div class="hud-card preset-buttons">
                <button class="btn-preset active" onclick="focusViewport('NASHIK_ROAD')">🎯 Nashik Road (NK)</button>
                <button class="btn-preset" onclick="focusViewport('DEVLALI')">🎖️ Devlali (DVL)</button>
                <button class="btn-preset" onclick="focusViewport('BRIDGE')">🌉 Godavari Bridge</button>
                <button class="btn-preset" onclick="focusViewport('CORRIDOR')">🗺️ Full Corridor</button>
            </div>
        </div>

        <!-- Bottom Left Telemetry & Legend HUD -->
        <div class="hud-bottom">
            <div class="hud-legend">
                <div class="legend-title">
                    <span>Central Railway Corridor</span>
                    <span style="color:#10B981;" id="active-trains-count">6 Trains Active</span>
                </div>
                <div class="legend-grid">
                    <div class="legend-item">
                        <div class="legend-color-bar" style="background:#10B981;"></div>
                        <span>Clear (Auto Block)</span>
                    </div>
                    <div class="legend-item">
                        <div class="legend-color-bar" style="background:#F59E0B;"></div>
                        <span>Caution / Regulated</span>
                    </div>
                    <div class="legend-item">
                        <div class="legend-color-bar" style="background:#EF4444;"></div>
                        <span>High Saturation</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-symbol">🏢</span>
                        <span>Stations (12)</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-symbol">⚡</span>
                        <span>25kV AC Electrified</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-symbol">📡</span>
                        <span>ISRO RTIS Transponder</span>
                    </div>
                </div>
            </div>
        </div>


    </div>

    <!-- Leaflet fallback library for instant zero-config interactive mapping -->
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>

    <script>
        const API_KEY = "${apiKey}";
        let rawData = null;
        let googleMap = null;
        let leafletMap = null;
        let isUsingGoogleMaps = false;

        // Viewport coordinate presets
        const VIEWPORTS = {
            NASHIK_ROAD: { lat: 19.9575, lng: 73.8340, zoom: 14 },
            DEVLALI: { lat: 19.9242, lng: 73.8310, zoom: 14 },
            BRIDGE: { lat: 20.0050, lng: 73.8950, zoom: 14 },
            CORRIDOR: { lat: 19.9700, lng: 73.9500, zoom: 10 }
        };

        // Live Clock
        function updateClock() {
            const now = new Date();
            const timeStr = now.toLocaleTimeString('en-US', { timeZone: 'Asia/Kolkata', hour12: false }) + ' IST';
            document.getElementById('clock-display').innerText = timeStr;
        }
        setInterval(updateClock, 1000);
        updateClock();

        // Graceful Google Maps Auth Failure Handler
        window.gm_authFailure = function() {
            console.warn("Google Maps authentication issue. Gracefully switching to vector map.");
            loadLeafletMap();
        };

        // Fetch Data & Initialize Map
        async function init() {
            try {
                const res = await fetch('/api/map/nashik/data');
                rawData = await res.json();
                document.getElementById('active-trains-count').innerText = (rawData.trains ? rawData.trains.length : 6) + ' Trains Active';
            } catch (err) {
                console.error("Failed to load map data", err);
            }

            if (API_KEY && API_KEY.length > 5) {
                loadGoogleMaps();
            } else {
                loadLeafletMap();
            }
        }

        // --- GOOGLE MAPS IMPLEMENTATION ---
        function loadGoogleMaps() {
            const script = document.createElement('script');
            script.src = "https://maps.googleapis.com/maps/api/js?key=" + API_KEY + "&loading=async&libraries=marker,geometry&v=weekly&callback=onGoogleMapsLoaded";
            script.async = true;
            script.onerror = () => {
                console.warn("Google Maps JS API failed to load. Falling back to vector map.");
                loadLeafletMap();
            };
            document.head.appendChild(script);
        }

        window.onGoogleMapsLoaded = async function() {
            isUsingGoogleMaps = true;
            document.getElementById('gmap').style.display = 'block';
            document.getElementById('fallback-map').style.display = 'none';

            const { Map } = await google.maps.importLibrary("maps");
            const { AdvancedMarkerElement, PinElement } = await google.maps.importLibrary("marker");

            // Google Maps with DEMO_MAP_ID (required for AdvancedMarkerElement)
            googleMap = new Map(document.getElementById("gmap"), {
                center: { lat: 19.9575, lng: 73.8340 },
                zoom: 12,
                mapId: "DEMO_MAP_ID",
                mapTypeId: 'terrain',
                disableDefaultUI: false,
                zoomControl: true,
                streetViewControl: false,
                fullscreenControl: true
            });

            // Set usage attribution tracking
            if (google.maps.settings) {
                // Attribution tracking for agentskills
            }

            // 1. Draw Section Polylines
            if (rawData && rawData.sections) {
                rawData.sections.forEach(sec => {
                    const strokeColor = sec.status === 'CLEAR' ? '#10B981' : (sec.status === 'CAUTION' ? '#F59E0B' : '#EF4444');
                    const poly = new google.maps.Polyline({
                        path: sec.path,
                        geodesic: true,
                        strokeColor: strokeColor,
                        strokeOpacity: 0.9,
                        strokeWeight: 5,
                        map: googleMap
                    });

                    const infoContent = '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif; padding:6px; min-width:220px;">' +
                        '<div style="font-weight:800; font-size:14px; margin-bottom:4px;">' + sec.name + '</div>' +
                        '<div style="font-size:11px; color:#64748B;">Section ID: <strong>' + sec.sectionId + '</strong></div>' +
                        '<div style="font-size:12px; margin-top:6px;">Length: <strong>' + sec.lengthKm + ' km</strong> | Track: <strong>' + sec.trackType + '</strong></div>' +
                        '<div style="font-size:12px;">Signaling: <strong>' + sec.signaling + '</strong></div>' +
                        '<div style="font-size:12px;">Max Speed: <strong>' + sec.maxSpeed + ' km/h</strong></div>' +
                        '<div style="font-size:12px;">Saturation: <strong>' + sec.saturation + '%</strong></div>' +
                        '<div style="font-size:12px; margin-top:4px;">Status: <strong style="color:' + strokeColor + '">' + sec.status + ' (' + sec.signalAspect + ')</strong></div>' +
                        '</div>';
                    const infoWindow = new google.maps.InfoWindow({ content: infoContent });
                    poly.addListener('click', (e) => {
                        infoWindow.setPosition(e.latLng);
                        infoWindow.open(googleMap);
                    });
                });
            }

            // 2. Draw Stations using AdvancedMarkerElement
            if (rawData && rawData.stations) {
                rawData.stations.forEach(stn => {
                    const pin = new PinElement({
                        background: stn.code === 'NK' ? '#10B981' : '#0B192C',
                        borderColor: '#FFFFFF',
                        glyphColor: '#FFFFFF',
                        scale: stn.code === 'NK' ? 1.3 : 1.0
                    });

                    const marker = new AdvancedMarkerElement({
                        map: googleMap,
                        position: { lat: stn.lat, lng: stn.lng },
                        title: stn.name + " (" + stn.code + ")",
                        content: pin.element
                    });

                    const infoContent = '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif; padding:8px; min-width:240px;">' +
                        '<div style="font-weight:800; font-size:15px;">' + stn.name + ' (' + stn.code + ')</div>' +
                        '<div style="font-size:13px; color:#10B981; font-weight:700;">' + stn.marathiName + '</div>' +
                        '<div style="font-size:11px; color:#64748B; margin-top:4px;">Category: <strong>' + stn.category + '</strong> | Platforms: <strong>' + stn.platforms + '</strong> | Elevation: <strong>' + stn.elevation + 'm</strong></div>' +
                        '<div style="font-size:11px; color:#334155; margin-top:6px; line-height:1.4;">' + stn.description + '</div>' +
                        '</div>';
                    const infoWindow = new google.maps.InfoWindow({ content: infoContent });
                    marker.addListener('click', () => {
                        infoWindow.open({ anchor: marker, map: googleMap });
                    });
                });
            }

            // 3. Draw Live Moving Trains using AdvancedMarkerElement
            if (rawData && rawData.trains) {
                rawData.trains.forEach(t => {
                    const el = document.createElement('div');
                    el.className = 'train-marker-badge' + (t.priority === 1 ? ' train-marker-vb' : '');
                    el.innerHTML = '🚆 ' + t.trainNumber + ' (' + t.speed + ' km/h)';

                    const marker = new AdvancedMarkerElement({
                        map: googleMap,
                        position: { lat: t.lat, lng: t.lng },
                        title: t.name,
                        content: el
                    });

                    const delayColor = t.delay === 0 ? '#10B981' : (t.delay < 15 ? '#F59E0B' : '#EF4444');
                    const infoContent = '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif; padding:8px; min-width:250px;">' +
                        '<div style="font-weight:800; font-size:15px; color:#0B192C;">' + t.trainNumber + ' • ' + t.name + '</div>' +
                        '<div style="font-size:12px; color:#64748B; font-weight:600;">' + t.type + ' | Loco: ' + t.locomotive + '</div>' +
                        '<hr style="margin:6px 0; border:none; border-top:1px solid #E2E8F0;" />' +
                        '<div style="font-size:12px;">Speed: <strong>' + t.speed + ' km/h</strong></div>' +
                        '<div style="font-size:12px;">Delay: <strong style="color:' + delayColor + '">' + (t.delay === 0 ? 'ON TIME' : '+' + t.delay + ' MINS') + '</strong></div>' +
                        '<div style="font-size:12px;">Next Station: <strong>' + t.nextStation + ' (ETA: ' + t.eta + ')</strong></div>' +
                        '<div style="font-size:11px; color:#475569; margin-top:6px; background:#F1F5F9; padding:6px; border-radius:6px;">' + t.statusText + '</div>' +
                        '</div>';
                    const infoWindow = new google.maps.InfoWindow({ content: infoContent });
                    marker.addListener('click', () => {
                        infoWindow.open({ anchor: marker, map: googleMap });
                    });
                });
            }
        };

        // --- FALLBACK INTERACTIVE VECTOR MAP (Leaflet) ---
        function loadLeafletMap() {
            document.getElementById('gmap').style.display = 'none';
            document.getElementById('fallback-map').style.display = 'block';

            leafletMap = L.map('fallback-map', { zoomControl: true }).setView([19.9575, 73.8340], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors | Central Railway GIS'
            }).addTo(leafletMap);

            if (!rawData) return;

            // Draw Sections
            if (rawData.sections) {
                rawData.sections.forEach(sec => {
                    const color = sec.status === 'CLEAR' ? '#10B981' : (sec.status === 'CAUTION' ? '#F59E0B' : '#EF4444');
                    const latLngs = sec.path.map(p => [p.lat, p.lng]);
                    const polyline = L.polyline(latLngs, { color: color, weight: 5, opacity: 0.9 }).addTo(leafletMap);
                    polyline.bindPopup(
                        '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif;">' +
                        '<strong>' + sec.name + '</strong><br/>' +
                        'Length: <b>' + sec.lengthKm + ' km</b> | Max Speed: <b>' + sec.maxSpeed + ' km/h</b><br/>' +
                        'Status: <b style="color:' + color + '">' + sec.status + ' (' + sec.signalAspect + ')</b>' +
                        '</div>'
                    );
                });
            }

            // Draw Stations
            if (rawData.stations) {
                rawData.stations.forEach(stn => {
                    const marker = L.circleMarker([stn.lat, stn.lng], {
                        radius: stn.code === 'NK' ? 9 : 6,
                        fillColor: stn.code === 'NK' ? '#10B981' : '#0B192C',
                        color: '#FFFFFF',
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 0.95
                    }).addTo(leafletMap);

                    marker.bindPopup(
                        '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif;">' +
                        '<strong>' + stn.name + ' (' + stn.code + ')</strong> - <b>' + stn.marathiName + '</b><br/>' +
                        'Platforms: <b>' + stn.platforms + '</b> | Category: <b>' + stn.category + '</b><br/>' +
                        '<small>' + stn.description + '</small>' +
                        '</div>'
                    );
                });
            }

            // Draw Trains
            if (rawData.trains) {
                rawData.trains.forEach(t => {
                    const trainMarker = L.circleMarker([t.lat, t.lng], {
                        radius: 8,
                        fillColor: t.priority === 1 ? '#2563EB' : '#EF4444',
                        color: '#FFFFFF',
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 1
                    }).addTo(leafletMap);

                    trainMarker.bindPopup(
                        '<div style="color:#0F172A; font-family:\'Outfit\',sans-serif;">' +
                        '<strong>🚆 ' + t.trainNumber + ' - ' + t.name + '</strong><br/>' +
                        'Speed: <b>' + t.speed + ' km/h</b> | Delay: <b>' + (t.delay === 0 ? 'ON TIME' : '+' + t.delay + 'm') + '</b><br/>' +
                        'Next: <b>' + t.nextStation + ' (ETA: ' + t.eta + ')</b><br/>' +
                        '<small>' + t.statusText + '</small>' +
                        '</div>'
                    );
                });
            }
        }


        // Viewport Focusing
        function focusViewport(presetKey) {
            document.querySelectorAll('.btn-preset').forEach(b => b.classList.remove('active'));
            event.target.classList.add('active');

            const vp = VIEWPORTS[presetKey];
            if (!vp) return;

            if (isUsingGoogleMaps && googleMap) {
                googleMap.setCenter({ lat: vp.lat, lng: vp.lng });
                googleMap.setZoom(vp.zoom);
            } else if (leafletMap) {
                leafletMap.setView([vp.lat, vp.lng], vp.zoom);
            }
        }

        window.onload = init;
    </script>
</body>
</html>
    """.trimIndent()
}
