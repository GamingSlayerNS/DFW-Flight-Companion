package com.example.dfwflightcompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

data class AmenityDetail(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val subType: String = "",
    var congestion: String = "",
    val lastUpdated: Long = 0L,
    val isAccessible: Boolean = true,
    val nodeId: String = ""
)

data class MapNode(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val level: Int,
    val type: String = "",
    val name: String = ""
)

fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

@Composable
fun MapScreen(
    navController: NavHostController,
    mapViewModel: MapViewModel
) {
    val CLOSE_THRESHOLD = 0.00005
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isNavigating by remember { mutableStateOf(false) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null)}
    
    // User's current location (Simulation)
    val userLocation = remember { mutableStateOf(LatLng(32.8993, -97.0446)) }
    val initialCameraPosition = remember { LatLng(32.8974, -97.0446) }
    var currentDestination by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }
    var selectedDest by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val amenities = remember { mutableStateListOf<AmenityDetail>() }
    var selectedAmenity by remember { mutableStateOf<AmenityDetail?>(null) }

    val mapNodes = remember { mutableStateListOf<MapNode>() }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Custom Filter Checkbox states
    var wheelchair by remember { mutableStateOf(false) }
    var mens by remember { mutableStateOf(false) }
    var womens by remember { mutableStateOf(false) }

    var selectionFromAmenityScreen by remember { mutableStateOf<String?>(null) }
    var cameraBearing by remember { mutableStateOf(0.0) } // tracking the camera angle

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
                    setupSourcesAndLayers(context, style, userLocation.value)
                    fetchDataFromFunctions(style, amenities, mapNodes, mapViewModel)
                    
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(initialCameraPosition)
                                .zoom(16.0)
                                .build()
                        )
                    )
                }
            }
        }
    }

    // Function to cancel navigation
    val cancelNavigation = {
        isNavigating = false
        currentDestination = null
        mapRef.value?.let { map ->
            map.style?.let { style ->
                // Clear the route by providing a valid empty FeatureCollection
                style.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson("""{"type": "FeatureCollection", "features": []}""")
            }
            // Reset camera to normal
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(initialCameraPosition)
                        .zoom(16.0)
                        .bearing(0.0)
                        .tilt(0.0)
                        .build()
                ), 2000
            )
        }
    }

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

        if(pathNodes.isEmpty()){
            val style = mapRef.value?.style
            val routeSource = style?.getSourceAs<GeoJsonSource>("route-source")
            routeSource?.setGeoJson(
                FeatureCollection.fromFeatures(arrayOf())
            )

            return null
        }

        val dx = startNode.lng - endNode.lng
        val dy = startNode.lat - endNode.lat
        val distance = sqrt(dx * dx + dy * dy)
        if(pathNodes.size == 2 && distance < CLOSE_THRESHOLD){
            cancelNavigation()
            return null
        }

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
        val points = path.map {
            Point.fromLngLat(it.lng, it.lat)
        }

        val lineString = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(lineString)

        // Update source
        routeSource?.setGeoJson(feature)

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
                                    selectedAmenity?.let { amenity ->
                                        val functions = Firebase.functions
                                        val data = hashMapOf(
                                            "amenityId" to amenity.id,
                                            "congestion" to level
                                        )
                                        functions.getHttpsCallable("updateAmenityCongestion").call(data)
                                            .addOnSuccessListener {
                                                Log.d("FirestoreDB", "Amenity congestion updated successfully to $level")
                                                // Update local state
                                                val index = amenities.indexOfFirst { it.id == amenity.id }
                                                if (index != -1) {
                                                    val updated = amenities[index].copy(
                                                        congestion = level,
                                                        lastUpdated = System.currentTimeMillis()
                                                    )
                                                    amenities[index] = updated
                                                    selectedAmenity = updated
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("FirestoreDB", "Error updating amenity congestion", e)
                                            }
                                    }
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

    LaunchedEffect(mapRef.value) {
        while (true) {
            val map = mapRef.value ?: return@LaunchedEffect
            cameraBearing = map.cameraPosition.bearing
            delay(16) // ~60fps
        }
    }

    // Checks to see if marker on the map is clicked
    DisposableEffect(Unit) {
    mapView.getMapAsync { map ->
            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(screenPoint, "marker-layer")
                val amenityFeatures = map.queryRenderedFeatures(screenPoint, "amenity-layer") // when user clicks on map marker after setting custom filters

                if (features.isNotEmpty() || amenityFeatures.isNotEmpty()) {
                    val clickedFeature = when {
                        features.isNotEmpty() -> features[0]
                        amenityFeatures.isNotEmpty() -> amenityFeatures[0]
                        else -> null
                    }
                    val nodeId = clickedFeature?.getStringProperty("id")

                    // Find the amenity linked to this NodeID
                    selectedAmenity = when {
                        features.isNotEmpty() -> amenities.find { it.nodeId == nodeId }
                        amenityFeatures.isNotEmpty() -> amenities.find { it.id == nodeId }
                        else -> null
                    }

                    val geometry = clickedFeature?.geometry()

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

        // Route from User Location to destination
        val points = path.map {
            Point.fromLngLat(it.lng, it.lat)
        }

        val lineString = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(lineString)

        // Update source
        routeSource.setGeoJson(feature)
    }

    // Updating the users location when the user is moved
    fun updateUserLocation(newLng: Double, newLat: Double) {
        val map = mapRef.value ?: return
        val style = map.style ?: return

        val source = style.getSourceAs<GeoJsonSource>("user-source") ?: return

        // Create a MapLibre Point for the new location
        val point = Point.fromLngLat(newLng, newLat)

        // Wrap it in a Feature
        val feature = Feature.fromGeometry(point)

        // Update the source
        source.setGeoJson(feature)

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

    LaunchedEffect(mapViewModel.selectedAmenityId, amenities.size, mapNodes.size) {
        selectionFromAmenityScreen = mapViewModel.selectedAmenityId
        if (selectionFromAmenityScreen == null) {
            selectedAmenity = null
            showAmenityBox = false
            return@LaunchedEffect
        }
        // Wait until both lists are ready
        if (amenities.isEmpty() || mapNodes.isEmpty()) {
            Log.d("Loading Lists", "Skipping lookup: amenities=${amenities.size}, mapNodes=${mapNodes.size}")
            return@LaunchedEffect
        }
        if (selectionFromAmenityScreen != null && amenities.isNotEmpty()) {
            selectionFromAmenityScreen.let { id ->
                val amenity = amenities.find { it.id == id }
                if (amenity != null) {
                    val amenityScreenSelectionNode = mapNodes.find { it.id == amenity.nodeId }
                    if (amenityScreenSelectionNode != null) {
                        val nodeLat = amenityScreenSelectionNode.latitude
                        val nodeLng = amenityScreenSelectionNode.longitude
                        mapRef.value?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(nodeLat, nodeLng))
                                    .zoom(19.0)
                                    .bearing(180.0)   // Face south
                                    .tilt(45.0)
                                    .build()
                            )
                        )
                        selectedDest = Pair(nodeLng, nodeLat)
                    }
                    selectedAmenity = amenity
                    showAmenityBox = true
                }
            }
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
        val step = 0.00005  // adjust for speed

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

    fun applyCustomFilters() {
        val filtered = amenities.filter { amenity ->
            val matchesWheelchair = wheelchair && amenity.subType.equals("Handicap", true)
            val matchesMens = mens && amenity.subType.equals("Male", true)
            val matchesWomens = womens && amenity.subType.equals("Female", true)

            if (!wheelchair && !mens && !womens) {
                true
            } else {
                matchesWheelchair || matchesMens || matchesWomens
            }
        }

        val filteredFeatures = filtered.mapNotNull { amenity ->
            val node = mapNodes.find { it.id == amenity.nodeId }
            if (node == null) {
                Log.e("DEBUG", "No node found for amenity ${amenity.id} nodeId=${amenity.nodeId}")
                return@mapNotNull null
            }

            Feature.fromGeometry(
                Point.fromLngLat(node.longitude, node.latitude)
            ).apply {
                addStringProperty("id", amenity.id)
                addStringProperty("name", amenity.name)
                addStringProperty("type", amenity.type)
                addStringProperty("subType", amenity.subType)
                addStringProperty("congestion", amenity.congestion)
                addNumberProperty("lastUpdated", amenity.lastUpdated)
                addBooleanProperty("isAccessible", amenity.isAccessible)
                addStringProperty("nodeId", amenity.nodeId)
            }
        }

        mapRef.value?.style?.getSourceAs<GeoJsonSource>("amenity-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(filteredFeatures))
        mapRef.value?.style?.getLayer("marker-layer")
            ?.setProperties(visibility(Property.NONE))
    }

    val filterButtonPadding = if (cameraBearing in 0.0000000001..359.9999999999) {
        Modifier.padding(top = 56.dp, end = 6.dp)   // facing off-north
    } else {
        Modifier.padding(top = 8.dp, end = 6.dp)    // default (facing north)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Filter Button
        Box(
            modifier = filterButtonPadding
                .size(54.dp)
                .align(Alignment.TopEnd)
                .border(
                    width = 1.dp,
                    color = androidx.compose.ui.graphics.Color.Black,
                    shape = CircleShape
                )
                .background(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    shape = CircleShape
                )
                .clickable {
                    showFilterDialog = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.filter_icon),
                contentDescription = "Filter",
                tint = androidx.compose.ui.graphics.Color.Black
            )
        }

        // View Amenity Details Box
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
                            text = selectedAmenity?.name ?: selectedBackground?.name ?: "Unknown",
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
                                text = selectedAmenity?.congestion ?: "Low",
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
                            Text(
                                text = selectedAmenity?.let { formatTimeAgo(it.lastUpdated) } ?: "1min Ago",
                                color = androidx.compose.ui.graphics.Color(0xFF00C853),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.alignBy(FirstBaseline)
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

        // AlertDialog with Checkboxes
        if (showFilterDialog) {
            var tempWheelchair by remember { mutableStateOf(wheelchair) }
            var tempMens by remember { mutableStateOf(mens) }
            var tempWomens by remember { mutableStateOf(womens) }

            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = {
                    Text(
                        "Custom Preferences",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                },
                text = {
                    Column {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                tempWheelchair = !tempWheelchair
                            }
                        ) {
                            Checkbox(
                                checked = tempWheelchair,
                                onCheckedChange = { tempWheelchair = it }
                            )
                            Text("Wheelchair Accessible")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                tempMens = !tempMens
                            }
                        ) {
                            Checkbox(
                                checked = tempMens,
                                onCheckedChange = { tempMens = it }
                            )
                            Text("Men's Restrooms")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                tempWomens = !tempWomens
                            }
                        ) {
                            Checkbox(
                                checked = tempWomens,
                                onCheckedChange = { tempWomens = it }
                            )
                            Text("Women's Restrooms")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAmenityBox = false
                        showFilterDialog = false
                        wheelchair = tempWheelchair
                        mens = tempMens
                        womens = tempWomens

                        applyCustomFilters()

                        // Reset camera to normal
                        mapRef.value?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(initialCameraPosition)
                                    .zoom(16.0)
                                    .bearing(0.0)
                                    .tilt(0.0)
                                    .build()
                            ), 2000
                        )
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Stop Navigation Button
        if (isNavigating) {
            Button(
                onClick = { cancelNavigation() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Red,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Navigation", fontWeight = FontWeight.Bold)
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
        PropertyFactory.lineColor(color(Color.BLUE)),
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
            iconAllowOverlap(true)
        )
    )

    // For displaying the markers when user sets custom filters
    style.addSource(
        GeoJsonSource("amenity-source", FeatureCollection.fromFeatures(emptyList()))
    )
    style.addLayerAbove(
        SymbolLayer("amenity-layer", "amenity-source").withProperties(
            iconImage("marker-icon"),
            iconSize(
                interpolate(
                    exponential(1.5f),
                    zoom(),
                    stop(12, 0.4f),
                    stop(16, 0.8f),
                    stop(20, 1.6f)
                )
            ),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        ), "marker-layer"
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

private fun fetchDataFromFunctions(style: Style, amenities: MutableList<AmenityDetail>, mapNodes: MutableList<MapNode>, mapViewModel: MapViewModel) {
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
                if (id != "node_entrance" && id != "node_exit") {
                    mapNodes.add(
                        MapNode(
                            id = id,
                            latitude = lat,
                            longitude = lng,
                            level = level,
                            type = type,
                            name = name
                        )
                    )
                    nodeList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level" }, "geometry": {"type": "Point", "coordinates": [$lng, $lat]}}""")
                }
            }
            val geoJson = """{"type": "FeatureCollection", "features": [${nodeList.take(7).joinToString(",")}]}""" // only display markers for the first 7 restrooms from DB
            style.getSourceAs<GeoJsonSource>("marker-source")?.setGeoJson(geoJson)
        }

    // 4. Fetch Amenities
    functions.getHttpsCallable("getAmenities").call()
        .addOnSuccessListener { result ->
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? List<Map<String, Any>> ?: return@addOnSuccessListener
            amenities.clear()
            data.forEach { map ->
                amenities.add(AmenityDetail(
                    id = map["id"] as? String ?: "",
                    name = map["Name"] as? String ?: "Unknown",
                    type = map["AmenityType"] as? String ?: "",
                    subType = map["SubTypeName"] as? String ?: "",
                    congestion = map["Congestion"] as? String ?: "Low",
                    lastUpdated = (map["LastUpdated"] as? Number)?.toLong() ?: 0L,
                    isAccessible = map["IsAccessible"] as? Boolean ?: false,
                    nodeId = map["NodeID"] as? String ?: ""
                ))
            }
            // only store the first 7 restrooms from DB
            if (amenities.size > 7) {
                amenities.subList(7, amenities.size).clear()
            }
            mapViewModel.storeAmenities(amenities)
        }
}
