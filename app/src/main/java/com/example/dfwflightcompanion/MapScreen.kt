package com.example.dfwflightcompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isNavigating by remember { mutableStateOf(false) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null)}
    
    // User's current location (Simulation)
    val userLocation = remember { mutableStateOf(LatLng(32.8993, -97.0446)) }
    var currentDestination by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }
    var selectedDest by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val graph = remember{
        GraphBuilder.fromGeoJson(context)
    }

    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                mapRef.value = map
                map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                    Log.d("DEBUG", "Before fetch: mapBackgrounds size = ${mapBackgrounds.size}")
                    setupSourcesAndLayers(context, style, userLocation.value)
                    fetchDataFromFunctions(style)
                    
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(32.8974, -97.0446))
                                .zoom(16.0)
                                .build()
                        )
                    )
                }
            }
        }
    }

    var selectedDest by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    fun computeRoute(userLng: Double, userLat: Double, destLng: Double, destLat: Double): List<Node>? {
        // Find nearest nodes for start location
        val userNode = Node(userLng, userLat)
        val (snappedPoint, a, b) = Pathfinding.findClosestPointOnGraph(userNode, graph)

        // Insert snappedPoint into graph
        val updatedGraph = Pathfinding.insertTemporaryNode(graph, a, b, snappedPoint)

        // Now snappedPoint IS the start node
        val startNode = snappedPoint
        val endNode = Pathfinding.findNearestNode(destLng, destLat, graph.keys)

        // Run A*
        val pathNodes = Pathfinding.aStar(updatedGraph, startNode, endNode)
        if(pathNodes.isEmpty()) return null

        return pathNodes
    }

    // Function to simulate navigation
    val startNavigation = startNav@{ destLng: Double, destLat: Double ->
        isNavigating = true
        val map = mapRef.value ?: return@startNav
        val style = map.style ?: return@startNav

        val routeSource = style.getSourceAs<GeoJsonSource>("route-source")

        // Retrieving user's location
        val userLng = userLocation.value.longitude
        val userLat = userLocation.value.latitude

        // Calculate the route path
        val path = computeRoute(userLng, userLat, destLng, destLat) ?: return@startNav

        // Convert to GeoJSON coordinates
        val coordinates = path.joinToString(","){
            "[${it.lng}, ${it.lat}]"
        }

        // Route from User Location to destination
        val routeJson = """
            {
              "type": "Feature",
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  $coordinates
                ]
              }
            }
        """.trimIndent()

        routeSource?.setGeoJson(routeJson)

        // Animate camera to follow route
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(userLocation.value)
                    .zoom(18.0)
                    .bearing(map.cameraPosition.bearing) // Face direction of the user
                    .tilt(45.0)
                    .build()
            ), 2000
        )
    }

    var showAmenityBox by remember { mutableStateOf(false) } // box for viewing amenity details
    var offsetY by remember { mutableStateOf(0f) }
    val closeThreshold = with(LocalDensity.current) { 120.dp.toPx() }

    // alert box for updating crowd level
    var showCrowdLvlBox by remember { mutableStateOf(false) }
    if (showCrowdLvlBox) {
        AlertDialog(
            onDismissRequest = { showCrowdLvlBox = false },
            title = {
                Text(
                    "Current Crowd Level",
                    modifier = Modifier.padding(top = 12.dp, start = 10.dp)
                )
            },
            text = {
                Column {
                    listOf("Low", "Medium", "High").forEach { level ->
                        Text(
                            text = level,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // viewModel.updateCrowdLevel(level)
                                    showCrowdLvlBox = false
                                }
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { showCrowdLvlBox = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

        // Checks to see if marker on the map is clicked
    DisposableEffect(Unit) {
    mapView.getMapAsync { map ->
            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(screenPoint, "marker-layer")

                if (features.isNotEmpty()) {
                    val clickedFeature = features[0]
                    val geometry = clickedFeature.geometry()

                    if (geometry is Point) {
                        val destLng = geometry.longitude()
                        val destLat = geometry.latitude()
                        currentDestination = Pair(destLng, destLat)
                        selectedDest = Pair(destLng, destLat)   // store destination
                        showAmenityBox = true
                        // startNavigation(destLng, destLat)
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(destLat, destLng))
                                    .zoom(18.0)
                                    .bearing(180.0) // Face south
                                    .tilt(45.0)
                                    .build()
                            ), 2000
                        )
                    }
                    true
                } else {
                    false
                }
            }
        }

        onDispose { }
    }

    // Function to update the navigation route as the user moves along it
    fun updateNavigation(destLng: Double, destLat: Double) {
        val map = mapRef.value ?: return
        val style = map.style ?: return
        val routeSource = style.getSourceAs<GeoJsonSource>("route-source") ?: return

        // Retrieving user's location
        val userLng = userLocation.value.longitude
        val userLat = userLocation.value.latitude

        // Calculate the route path
        val path = computeRoute(userLng, userLat, destLng, destLat) ?: return

        // Turn path into coordinates and add to map
        val coordinates = path.joinToString(",") {
            "[${it.lng}, ${it.lat}]"
        }

        val routeJson = """
        {
          "type": "Feature",
          "geometry": {
            "type": "LineString",
            "coordinates": [ $coordinates ]
          }
        }
    """.trimIndent()

        routeSource.setGeoJson(routeJson)
    }

    // Updating the users location when the user is moved
    fun updateUserLocation(newLng: Double, newLat: Double) {
        val map = mapRef.value ?: return
        val style = map.style ?: return

        val source = style.getSourceAs<GeoJsonSource>("user-source") ?: return

        val updatedJson = """
        {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [$newLng, $newLat]
          }
        }
        """.trimIndent()

        source.setGeoJson(updatedJson)

        if(isNavigating){
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(newLat, newLng))
                        .zoom(18.0)
                        .tilt(45.0)
                        .bearing(map.cameraPosition.bearing) // Face direction of the user
                        .build()
                ),
                500
            )
        }
    }

    LaunchedEffect(userLocation.value, currentDestination) {
        if(isNavigating) {
            val destination = currentDestination ?: return@LaunchedEffect

            val (destLng, destLat) = destination

            updateNavigation(destLng, destLat)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun moveUser(direction: Direction) {
        val step = 0.0001  // adjust for speed

        val currentLng = userLocation.value.longitude
        val currentLat = userLocation.value.latitude

        val (newLng, newLat) = when (direction) {
            Direction.UP -> currentLng to (currentLat + step)
            Direction.DOWN -> currentLng to (currentLat - step)
            Direction.LEFT -> (currentLng - step) to currentLat
            Direction.RIGHT -> (currentLng + step) to currentLat
        }

        // update your state
        userLocation.value = LatLng(newLat, newLng)

        // update map + camera
        updateUserLocation(newLng, newLat)
    }



    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Compose UI layer
        if (showAmenityBox) {
            val selectedBackground = remember(selectedDest, mapBackgrounds) {
                findBackgroundForSelectedDest(selectedDest, mapBackgrounds)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetY.roundToInt()) }
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 32.dp,
                                topEnd = 32.dp
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .background(androidx.compose.ui.graphics.Color(0xFFF5F5F5))
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                offsetY = (offsetY + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = {
                                if (offsetY > closeThreshold) {
                                    showAmenityBox = false   // closes the box
                                }
                                offsetY = 0f
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(androidx.compose.ui.graphics.Color.LightGray)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.Gray)
                            .clickable { showAmenityBox = false },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✕",
                            color = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
                            fontSize = 24.sp
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp, start = 36.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = selectedBackground?.name ?: "Unknown",
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Occupancy Status",
                                color = androidx.compose.ui.graphics.Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "OPEN",
                                color = androidx.compose.ui.graphics.Color(0xFF00C853),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Crowd Level",
                                color = androidx.compose.ui.graphics.Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.alignBy(FirstBaseline)
                            )
                            Text(
                                text = "Low",
                                color = androidx.compose.ui.graphics.Color(0xFF00C853),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.alignBy(FirstBaseline)
                            )
                            Text(
                                text = "Last Updated",
                                color = androidx.compose.ui.graphics.Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier
                                    .alignBy(FirstBaseline)
                                    .padding(start = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Button(
                                onClick = {
                                    showCrowdLvlBox = true
                                }
                            ) {
                                Text("Update Crowd Level")
                            }

                            Button(
                                onClick = {
                                    showAmenityBox = false
                                    selectedDest?.let { (lng, lat) ->
                                        startNavigation(lng, lat)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Navigation,
                                    contentDescription = "Start Navigation",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Navigation")
                            }
                        }
                    }
                }
            }
        }

        Column {
            Button(onClick = { moveUser(Direction.UP) }, modifier = Modifier.padding(start = 35.dp)) {
                Text("N")
            }

            Row {
                Button(onClick = { moveUser(Direction.LEFT) }) {
                    Text("W")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = { moveUser(Direction.RIGHT) }) {
                    Text("E")
                }
            }

            Button(onClick = { moveUser(Direction.DOWN) }, modifier = Modifier.padding(start = 35.dp)) {
                Text("S")
            }
        }
    }
}

private fun setupSourcesAndLayers(context: Context, style: Style, userLoc: LatLng) {
    Log.d("FirestoreDB", "MapScreen Initializing Map Generation")

    // 1. Floorplan Sources
    style.addSource(GeoJsonSource("floorplan-source"))
    style.addLayer(FillLayer("building-layer", "floorplan-source").withProperties(
        PropertyFactory.fillColor(color(Color.LTGRAY)),
        PropertyFactory.fillOpacity(0.5f)
    ).withFilter(eq(get("type"), literal("building"))))

    style.addLayer(FillLayer("hallway-layer", "floorplan-source").withProperties(
        PropertyFactory.fillColor(color(Color.WHITE))
    ).withFilter(eq(get("type"), literal("hallway"))))

    style.addLayer(FillLayer("room-layer", "floorplan-source").withProperties(
        PropertyFactory.fillColor(match(get("type"),
            literal("room"), color("#BBDEFB".toColorInt()),
            literal("restroom"), color("#C8E6C9".toColorInt()),
            literal("entrance"), color("#FFF9C4".toColorInt()),
            literal("exit"), color("#FFCDD2".toColorInt()),
            color(Color.GRAY))),
        PropertyFactory.fillOutlineColor(Color.DKGRAY)
    ).withFilter(any(
        eq(get("type"), literal("room")), eq(get("type"), literal("restroom")),
        eq(get("type"), literal("entrance")), eq(get("type"), literal("exit"))
    )))

    // 2. Routing Sources
    style.addSource(GeoJsonSource("routing-source"))
    style.addLayer(LineLayer("routing-layer", "routing-source").withProperties(
        PropertyFactory.lineColor(Color.RED),
        PropertyFactory.lineWidth(1f),
        PropertyFactory.lineOpacity(0.6f)
    ))

    // 3. Active Navigation Route Source
    style.addSource(GeoJsonSource("route-source"))
    style.addLayer(LineLayer("route-layer", "route-source").withProperties(
        PropertyFactory.lineColor(Color.BLUE),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineCap("round"),
        PropertyFactory.lineJoin("round")
    ))

    // 4. User Location
    val userPoint = """{"type": "Feature", "geometry": {"type": "Point", "coordinates": [${userLoc.longitude}, ${userLoc.latitude}]}}"""
    style.addSource(GeoJsonSource("user-source", userPoint))
    style.addLayer(CircleLayer("user-layer", "user-source").withProperties(
        PropertyFactory.circleColor(Color.BLUE),
        PropertyFactory.circleRadius(8f),
        PropertyFactory.circleStrokeColor(Color.WHITE),
        PropertyFactory.circleStrokeWidth(2f)
    ))

    // 5. Adding Markers
    val markerBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.restroom)
    val safeMarkerBitmap = markerBitmap.copy(Bitmap.Config.ARGB_8888, false)
    val scaledMarkerBitmap = Bitmap.createScaledBitmap(safeMarkerBitmap, 200, 200, true)

    if(style.getImage("marker-icon") == null) {
        style.addImage("marker-icon", scaledMarkerBitmap)
    }

                    // Add Markers pulled from firebase
                    val markerSourceId = "marker-source"
                    style.addSource(GeoJsonSource(markerSourceId))

    style.addLayer(
        SymbolLayer("marker-layer", markerSourceId).withProperties(
            iconImage("marker-icon"),
            iconSize(
                interpolate(
                    exponential(1.5f),    // scaling factor for smooth growth
                    zoom(),
                    stop(12, 0.4f),
                    stop(16, 0.8f),
                    stop(20, 1.6f)
                )
            ),
            iconAllowOverlap(false)
        )
    )
}

private val mapBackgrounds = mutableStateListOf<MapBackground>() // list used for fetching map background data from db
data class MapBackground(
    val id: String,
    val name: String,
    val type: String,
    val level: Int,
    val coordinates: List<LatLng>
)
private fun findBackgroundForSelectedDest(selectedDest: Pair<Double, Double>?, mapBackgrounds: List<MapBackground>): MapBackground? {
    if (selectedDest == null) return null
    val (lng, lat) = selectedDest
    val point = LatLng(lat, lng)

    return mapBackgrounds.firstOrNull { background ->
        isPointInsidePolygon(point, background.coordinates)
    }
}
private fun isPointInsidePolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    var intersects = false
    val x = point.longitude
    val y = point.latitude

    for (i in polygon.indices) {
        val j = (i + 1) % polygon.size

        val xi = polygon[i].longitude
        val yi = polygon[i].latitude
        val xj = polygon[j].longitude
        val yj = polygon[j].latitude

        val intersectsEdge = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / (yj - yi) + xi)

        if (intersectsEdge) intersects = !intersects
    }

    return intersects
}

private fun fetchDataFromFunctions(style: Style) {
    Log.d("FirestoreDB", "MapScreen Initializing map node generation.")
    val functions = Firebase.functions
    // ONLY if testing locally:
    functions.useEmulator("10.0.2.2", 5001)

    // 1. Fetch MapBackgrounds
    functions.getHttpsCallable("getMapBackgrounds").call()
        .addOnSuccessListener { result ->
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? List<Map<String, Any>> ?: return@addOnSuccessListener
            val featureList = mutableListOf<String>()
            data.forEach { doc ->
                @Suppress("UNCHECKED_CAST")
                val coords = doc["coordinates"] as? List<Map<String, Any>> ?: return@forEach
                val type = doc["type"] as? String ?: ""
                val name = doc["name"] as? String ?: ""
                val id = doc["id"] as? String ?: ""
                val level = (doc["level"] as? Number)?.toInt() ?: 0

                val latLngs = coords.map {
                    LatLng(
                        it["latitude"] as Double,
                        it["longitude"] as Double
                    )
                }
                mapBackgrounds.add(
                    MapBackground(
                        id = id,
                        name = name,
                        type = type,
                        level = level,
                        coordinates = latLngs
                    )
                )

                val coordString = coords.joinToString(",") { "[${it["longitude"]}, ${it["latitude"]}, $level]" }
                featureList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level" }, "geometry": {"type": "Polygon", "coordinates": [[$coordString]]}}""")
            }
            val geoJson = """{"type": "FeatureCollection", "features": [${featureList.joinToString(",")}]}"""
            style.getSourceAs<GeoJsonSource>("floorplan-source")?.setGeoJson(geoJson)
        }

    // 2. Fetch PathEdges
    functions.getHttpsCallable("getPathEdges").call()
        .addOnSuccessListener { result ->
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? List<Map<String, Any>> ?: return@addOnSuccessListener
            val pathList = mutableListOf<String>()
            data.forEach { doc ->
                @Suppress("UNCHECKED_CAST")
                val coords = doc["coordinates"] as? List<Map<String, Any>> ?: return@forEach
                val type = doc["type"] as? String ?: ""
                val name = doc["name"] as? String ?: ""
                val id = doc["id"] as? String ?: ""
                val level = (doc["level"] as? Number)?.toInt() ?: 0
                val weight = (doc["weight"] as? Number)?.toFloat() ?: 0f
                val coordString = coords.joinToString(",") { "[${it["longitude"]}, ${it["latitude"]}, $level]" }
                pathList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level", "weight": "$weight" }, "geometry": {"type": "LineString", "coordinates": [$coordString]}}""")
            }
            val geoJson = """{"type": "FeatureCollection", "features": [${pathList.joinToString(",")}]}"""
            style.getSourceAs<GeoJsonSource>("routing-source")?.setGeoJson(geoJson)
        }

    // 3. Fetch MapNodes
    functions.getHttpsCallable("getMapNodes").call()
        .addOnSuccessListener { result ->
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? List<Map<String, Any>> ?: return@addOnSuccessListener
            val nodeList = mutableListOf<String>()
            data.forEach { doc ->
                @Suppress("UNCHECKED_CAST")
                val coordMap = doc["coordinates"] as? Map<String, Any> ?: return@forEach
                val lng = (coordMap["longitude"] as? Number)?.toDouble() ?: return@forEach
                val lat = (coordMap["latitude"] as? Number)?.toDouble() ?: return@forEach
                val type = doc["type"] as? String ?: ""
                val name = doc["name"] as? String ?: ""
                val id = doc["id"] as? String ?: ""
                val level = (doc["level"] as? Number)?.toInt() ?: 0

                nodeList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level" }, "geometry": {"type": "Point", "coordinates": [$lng, $lat]}}""")
            }
            val geoJson = """{"type": "FeatureCollection", "features": [${nodeList.joinToString(",")}]}"""
            style.getSourceAs<GeoJsonSource>("marker-source")?.setGeoJson(geoJson)
        }
}
