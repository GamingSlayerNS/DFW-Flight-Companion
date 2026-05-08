package com.example.dfwflightcompanion.map

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.dfwflightcompanion.R
import com.example.dfwflightcompanion.Routes
import com.example.dfwflightcompanion.helpers.AmenityDetail
import com.example.dfwflightcompanion.helpers.Direction
import com.example.dfwflightcompanion.helpers.MapBackground
import com.example.dfwflightcompanion.helpers.MapNode
import com.example.dfwflightcompanion.helpers.haversine
import com.example.dfwflightcompanion.helpers.loadVectorToBitmap
import com.example.dfwflightcompanion.navigation.Edge
import com.example.dfwflightcompanion.navigation.GraphBuilder
import com.example.dfwflightcompanion.navigation.NavigationGraphRepository
import com.example.dfwflightcompanion.navigation.Node
import com.example.dfwflightcompanion.navigation.Pathfinding
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import java.util.Locale
import kotlin.math.acos
import kotlin.math.sqrt
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private var lastSegmentIndex: Int = -1

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
    val coroutineScope = rememberCoroutineScope()

    // User's current location (Simulation)
    val userLocation = mapViewModel.userLocation
    val initialCameraPosition = remember { LatLng(32.897546, -97.044471) }
    var currentDestination by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }
    var selectedDest by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // val amenities = remember { mutableStateListOf<AmenityDetail>() }
    val databaseAmenities = mapViewModel.amenities
    val amenities = remember(databaseAmenities) {
        databaseAmenities.map { amenity ->
            AmenityDetail(
                id = amenity.id,
                name = amenity.name,
                type = amenity.type,
                subType = amenity.subType,
                congestion = amenity.congestion,
                lastUpdated = amenity.lastUpdated,
                isAccessible = amenity.isAccessible,
                nodeId = amenity.nodeId
            )
        }.toMutableList()
    }

    var selectedAmenity by remember { mutableStateOf<AmenityDetail?>(null) }
    var selectedAmenityId by remember { mutableStateOf<String?>(null) }
    var clickedOnMapAmenity by remember { mutableStateOf(false) }
    var reroutedAmenity by remember { mutableStateOf<AmenityDetail?>(null) }

    val mapNodes = remember { mutableStateListOf<MapNode>() }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Custom Filter Checkbox states
    var wheelchairFilter by remember { mutableStateOf(false) }
    var mensFilter by remember { mutableStateOf(false) }
    var womensFilter by remember { mutableStateOf(false) }
    var amenityStatusFilter by remember { mutableStateOf<String?>(null) }
    var lowCrowdLvlFilter by remember { mutableStateOf(false) }
    var mediumCrowdLvlFilter by remember { mutableStateOf(false) }
    var highCrowdLvlFilter by remember { mutableStateOf(false) }
    var nearestAvailableFilter by remember { mutableStateOf(false) }

    var noFilteringResultsFound by remember { mutableStateOf(false) }
    var amenityClosedInRoute by remember { mutableStateOf(false) }
    var hasArrived by remember { mutableStateOf(false) }
    var saveDestCoordsForArrivedMsg by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var selectionFromAmenityScreen by remember { mutableStateOf<String?>(null) }
    var cameraBearing by remember { mutableStateOf(0.0) } // tracking the camera angle
    var currentStepIndex by remember { mutableStateOf(0) }
    var currentDirections by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentDirectionsWithDistances by remember { mutableStateOf<List<String>>(emptyList()) }
    var turnSegments by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentNavInstruction by remember { mutableStateOf("") }
    var currentNavDistanceToTurn by remember { mutableStateOf("") }
    var navPath by remember { mutableStateOf<List<Node>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) } // for expanding the navigation instructions to display full route

    var graph by remember { mutableStateOf<Map<Node, List<Edge>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        NavigationGraphRepository.startListening()
    }

    val navigationGraph by NavigationGraphRepository.navigationGraph.collectAsState()

    LaunchedEffect(navigationGraph) {
        navigationGraph?.let {
            graph = GraphBuilder.fromNavigationGraph(it)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NavigationGraphRepository.stopListening()
        }
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
                    setupSourcesAndLayers(context, style, userLocation)
                    fetchDataFromFunctions(style, amenities, mapNodes, mapViewModel)
                    
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(initialCameraPosition)
                                .zoom(17.5)
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
        if (graph.isEmpty()) return null

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
            hasArrived = true
            return null
        }

        return pathNodes
    }

    // Re-compute the navigation path to display updated navigation instructions when user goes off-route
    fun reroute(destLng: Double, destLat: Double) {
        val userLng = userLocation.longitude
        val userLat = userLocation.latitude
        val newPath = computeRoute(userLng, userLat, destLng, destLat)

        if (newPath.isNullOrEmpty() || newPath.size < 2) {
            Log.e("NAV", "Reroute failed: path too short (size=${newPath?.size})")
            return
        }

        // Replace navPath with the UI path
        navPath = newPath
        val (newDirections, newTurnSegments) = generateDirections(newPath)
        currentDirections = newDirections
        turnSegments = newTurnSegments
        currentStepIndex = 0
        lastSegmentIndex = -1

        if (currentDirections.isNotEmpty() && turnSegments.isNotEmpty()) {
            val firstTurnSegment = turnSegments[0]
            val turnNode = newPath[firstTurnSegment]
            val distanceToTurn = haversine(userLat,userLng,turnNode.lat,turnNode.lng).roundToInt()
            currentNavInstruction = currentDirections[0]
            currentNavDistanceToTurn = "${distanceToTurn}ft"
            currentDirectionsWithDistances = generateFullRouteList(newPath, currentDirections, turnSegments, currentStepIndex, distanceToTurn)
        } else {
            currentNavInstruction = "Continue"
            currentNavDistanceToTurn = "—"
        }
    }

    fun updateNavigationStep(destLng: Double, destLat: Double) {
        val currPath = navPath
        if (currPath.isEmpty()) return

        val userLng = userLocation.longitude
        val userLat = userLocation.latitude

        val userNode = Node(userLng, userLat)
        val segmentIndex = findClosestSegmentIndex(userNode, currPath)
        if (currPath.size < 2 || segmentIndex >= currPath.lastIndex) {
            return
        }
        // OFF-ROUTE DETECTION
        if ((lastSegmentIndex != -1 && segmentIndex < lastSegmentIndex) || (distanceToSegment(userNode,currPath[segmentIndex],currPath[segmentIndex + 1]) > 4.0)) {
            reroute(destLng, destLat)
            return
        }
        lastSegmentIndex = segmentIndex

        if (currentStepIndex < currentDirections.size) {
            var turnSegmentIndex = turnSegments[currentStepIndex]

            if (segmentIndex >= turnSegmentIndex) {
                currentStepIndex++
                if (currentStepIndex >= currentDirections.size) return
                turnSegmentIndex = turnSegments[currentStepIndex]
            }

            val turnNode = currPath[turnSegmentIndex]
            val distanceToTurn = haversine(userLat, userLng, turnNode.lat, turnNode.lng).roundToInt()
            currentNavInstruction = currentDirections[currentStepIndex]
            currentNavDistanceToTurn = "${distanceToTurn}ft"
            currentDirectionsWithDistances = generateFullRouteList(currPath, currentDirections, turnSegments, currentStepIndex, distanceToTurn)
        }
    }

    // calculates the distance of the computed route from user to a selected amenity
    fun distanceToUser(amenity: AmenityDetail): Double {
        val userLng = userLocation.longitude
        val userLat = userLocation.latitude
        val selectedRR = mapNodes.find { it.id == amenity.nodeId } ?: return Double.POSITIVE_INFINITY
        val route = computeRoute(userLng, userLat,selectedRR.longitude,selectedRR.latitude) ?: return Double.POSITIVE_INFINITY

        return pathLength(route)
    }

    fun applyCustomFilters(wheelchairFilter: Boolean, mensFilter: Boolean, womensFilter: Boolean, amenityStatusFilter: String?, lowCrowdLvlFilter: Boolean, mediumCrowdLvlFilter: Boolean, highCrowdLvlFilter: Boolean, nearestAvailableFilter: Boolean) {
        val filtered = amenities.filter { amenity ->
            val matchesWheelchair = wheelchairFilter && amenity.subType.equals("Neutral", true)
            val matchesMens = mensFilter && amenity.subType.equals("Male", true)
            val matchesWomens = womensFilter && amenity.subType.equals("Female", true)
            val matchesLowLvl = lowCrowdLvlFilter && amenity.congestion.equals("Low", true)
            val matchesMediumLvl = mediumCrowdLvlFilter && amenity.congestion.equals("Medium", true)
            val matchesHighLvl = highCrowdLvlFilter && amenity.congestion.equals("High", true)

            val typeMatch = (!mensFilter && !womensFilter && !wheelchairFilter) || (matchesMens || matchesWomens || matchesWheelchair)
            val statusMatch = when (amenityStatusFilter) {
                "open" -> amenity.isAccessible
                "closed" -> !amenity.isAccessible
                else -> true   // null → when no status filter applied
            }
            val crowdLvlMatch = (!lowCrowdLvlFilter && !mediumCrowdLvlFilter && !highCrowdLvlFilter) || (matchesLowLvl || matchesMediumLvl || matchesHighLvl)

            typeMatch && statusMatch && crowdLvlMatch
        }

        // if "nearest available option" is selected
        val selectedTypes = listOfNotNull(
            if (mensFilter) "Male" else null,
            if (womensFilter) "Female" else null,
            if (wheelchairFilter) "Neutral" else null
        )
        val nearestByType: List<AmenityDetail> = if (nearestAvailableFilter) {
            if (selectedTypes.isNotEmpty()) {
                selectedTypes.mapNotNull { type ->
                    filtered
                        .filter { it.subType.equals(type, true) && it.isAccessible }
                        .minByOrNull { distanceToUser(it) }
                }
            } else {
                listOfNotNull(filtered
                    .filter { it.isAccessible }
                    .minByOrNull { distanceToUser(it) })
            }
        } else { filtered }

        val finalFiltered = if (nearestAvailableFilter && nearestByType.isNotEmpty()) {
            nearestByType
        } else { filtered }

        if (finalFiltered.isNullOrEmpty()) {
            noFilteringResultsFound = true
            return
        }

        if (isNavigating && amenityClosedInRoute) {
            reroutedAmenity = finalFiltered[0]
            return
        }

        val filteredFeatures = finalFiltered.mapNotNull { amenity ->
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

    fun resetFilterTypes() {
        wheelchairFilter = false
        mensFilter = false
        womensFilter = false
        amenityStatusFilter = null
        lowCrowdLvlFilter = false
        mediumCrowdLvlFilter = false
        highCrowdLvlFilter = false
        nearestAvailableFilter = false

        applyCustomFilters(wheelchairFilter, mensFilter, womensFilter, amenityStatusFilter, lowCrowdLvlFilter, mediumCrowdLvlFilter, highCrowdLvlFilter, nearestAvailableFilter)
    }

    // Function to simulate navigation
    val startNavigation = startNav@{ destLng: Double, destLat: Double ->
        isNavigating = true
        val map = mapRef.value ?: return@startNav
        val style = map.style ?: return@startNav

        val routeSource = style.getSourceAs<GeoJsonSource>("route-source")

        // Retrieving user's location
        val userLng = userLocation.longitude
        val userLat = userLocation.latitude

        saveDestCoordsForArrivedMsg = destLng to destLat

        // Calculate the route path
        val path = computeRoute(userLng, userLat, destLng, destLat) ?: return@startNav

        // Generate directions
        val (directions, segments) = generateDirections(path)
        currentDirections = directions
        turnSegments = segments
        navPath = path
        currentStepIndex = 0
        lastSegmentIndex = -1
        updateNavigationStep(destLng, destLat)
        expanded = false

        // reset custom filter values
        resetFilterTypes()

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
                    .target(userLocation)
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
                    /* selectedAmenity = when {
                        features.isNotEmpty() -> amenities.find { it.nodeId == nodeId }
                        amenityFeatures.isNotEmpty() -> amenities.find { it.id == nodeId }
                        else -> null
                    } */
                    clickedOnMapAmenity = true
                    selectedAmenityId = when {
                        features.isNotEmpty() -> nodeId
                        amenityFeatures.isNotEmpty() -> nodeId
                        else -> null
                    }

                    val geometry = clickedFeature?.geometry()

                    if (geometry is Point) {
                        val destLng = geometry.longitude()
                        val destLat = geometry.latitude()
                        // currentDestination = Pair(destLng, destLat)
                        selectedDest = Pair(destLng, destLat)   // store selected destination
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
        val userLng = userLocation.longitude
        val userLat = userLocation.latitude

        // Calculate the route path
        val path = computeRoute(userLng, userLat, destLng, destLat) ?: return

        if (isNavigating) {
            updateNavigationStep(destLng, destLat)
        }

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

    LaunchedEffect(userLocation, currentDestination) {
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
                        selectedDest = Pair(nodeLng, nodeLat)
                        currentDestination = Pair(nodeLng, nodeLat)
                        selectedAmenity = amenity

                        if (mapViewModel.autoStartNavigation) {
                            showAmenityBox = false

                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(nodeLat, nodeLng))
                                        .zoom(18.0)
                                        .bearing(180.0)   // Face south, same as the marker-click path
                                        .tilt(45.0)
                                        .build()
                                ),
                                1000
                            )

                            delay(1000)

                            startNavigation(nodeLng, nodeLat)
                            mapViewModel.requestAutoStartNavigation(false)
                        } else {
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(nodeLat, nodeLng))
                                        .zoom(19.0)
                                        .bearing(180.0)
                                        .tilt(45.0)
                                        .build()
                                )
                            )
                            showAmenityBox = true
                        }
                    } else {
                        selectedAmenity = amenity
                        showAmenityBox = !mapViewModel.autoStartNavigation
                    }
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

    LaunchedEffect(mapViewModel.amenities) {
        amenities.clear()
        amenities.addAll(
            mapViewModel.amenities.map { amenity ->
                AmenityDetail(
                    id = amenity.id,
                    name = amenity.name,
                    type = amenity.type,
                    subType = amenity.subType,
                    congestion = amenity.congestion,
                    lastUpdated = amenity.lastUpdated,
                    isAccessible = amenity.isAccessible,
                    nodeId = amenity.nodeId
                )
            }
        )

        val style = mapRef.value?.style
        if (style != null) {
            fetchDataFromFunctions(style, amenities, mapNodes, mapViewModel)
        }

        val (lng, lat) = currentDestination ?: return@LaunchedEffect
        val node = mapNodes.find { (it.latitude == String.format(Locale.US, "%.5f", lat).toDouble() || it.latitude == String.format(Locale.US, "%.6f", lat).toDouble()) && (it.longitude == String.format(Locale.US, "%.5f", lng).toDouble() || it.longitude == String.format(Locale.US, "%.6f", lng).toDouble()) } ?: return@LaunchedEffect
        val amenity = amenities.firstOrNull { it.nodeId == node.id } ?: return@LaunchedEffect
        if (isNavigating && !amenity.isAccessible) {
            amenityClosedInRoute = true
        }
    }

    LaunchedEffect(amenities, clickedOnMapAmenity) {
        if (mapRef.value?.style?.getLayer("marker-layer")?.visibility?.value == Property.VISIBLE)
            selectedAmenity = amenities.firstOrNull { it.nodeId == selectedAmenityId }
        else
            selectedAmenity = amenities.firstOrNull { it.id == selectedAmenityId }
        clickedOnMapAmenity = false
    }

    fun moveUser(direction: Direction) {
        val step = 0.00005  // adjust for speed

        val currentLng = userLocation.longitude
        val currentLat = userLocation.latitude

        val (newLng, newLat) = when (direction) {
            Direction.UP -> currentLng to (currentLat + step)
            Direction.DOWN -> currentLng to (currentLat - step)
            Direction.LEFT -> (currentLng - step) to currentLat
            Direction.RIGHT -> (currentLng + step) to currentLat
        }

        // update your state
        mapViewModel.updateLocation(LatLng(newLat, newLng))

        // update map + camera
        updateUserLocation(newLng, newLat)
    }

    fun moveUserToNextNode(){
        if (turnSegments.isEmpty() || currentStepIndex >= turnSegments.size) return
        val nextTurnSegment = turnSegments[currentStepIndex]
        val turnNode = navPath.getOrNull(nextTurnSegment) ?: return

        val startLat = userLocation.latitude
        val startLng = userLocation.longitude
        val targetLat = turnNode.lat   // .toDouble() if it's a String
        val targetLng = turnNode.lng  // .toDouble() if it's a String

        val deltaLat = targetLat - startLat
        val deltaLng = targetLng - startLng

        // Constant speed: same per-frame step you had before
        val stepDeg = 0.000005
        val frameMs = 16L  // ~60fps
        val distance = sqrt(deltaLat * deltaLat + deltaLng * deltaLng)
        val totalSteps = (distance / stepDeg).toInt().coerceAtLeast(1)

        coroutineScope.launch {
            for (i in 1..totalSteps) {
                val t = i.toDouble() / totalSteps   // 0.0 -> 1.0
                val newLat = startLat + deltaLat * t
                val newLng = startLng + deltaLng * t

                mapViewModel.updateLocation(LatLng(newLat, newLng))
                updateUserLocation(newLng, newLat)
                delay(frameMs)
            }
        }
    }

    val filterButtonPadding = if (cameraBearing in 0.0000000001..359.9999999999) {
        Modifier.padding(top = 56.dp, end = 6.dp)   // facing off-north
    } else {
        Modifier.padding(top = 8.dp, end = 6.dp)    // default (facing north)
    }

    // dismiss the error message displayed on screen when no results are found using custom filters
    LaunchedEffect(noFilteringResultsFound) {
        if (noFilteringResultsFound) {
            delay(2500)
            noFilteringResultsFound = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        if (!isNavigating) {
            // Custom Filter Button (displayed only when not navigating)
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
            // Confirmation message when user arrives at destination
            if (hasArrived) {
                val arrivedAmenityName = amenities.find { it.nodeId == mapNodes.find { (it.latitude == String.format(Locale.US, "%.5f", saveDestCoordsForArrivedMsg?.second).toDouble() || it.latitude == String.format(Locale.US, "%.6f", saveDestCoordsForArrivedMsg?.second).toDouble()) && (it.longitude == String.format(Locale.US, "%.5f", saveDestCoordsForArrivedMsg?.first).toDouble() || it.longitude == String.format(Locale.US, "%.6f", saveDestCoordsForArrivedMsg?.first).toDouble()) }?.id }?.name
                AnimatedVisibility(
                    visible = hasArrived,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = androidx.compose.ui.graphics.Color.LightGray,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(horizontal = 18.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.arrived_icon_black),
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row {
                                    Text(
                                        "You have arrived!",
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 20.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row {
                                    Text(
                                        "Destination: $arrivedAmenityName",
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { hasArrived = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text("Close", color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
            }
        }

        // View Amenity Details Box
        if (showAmenityBox) {
            hasArrived = false
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
                            .clickable {
                                showAmenityBox = false
                                if (isNavigating) {
                                    mapRef.value?.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(userLocation)
                                                .zoom(18.0)
                                                .bearing(mapRef.value?.cameraPosition?.bearing ?: 0.0)
                                                .tilt(45.0)
                                                .build()
                                        ),2000
                                    )
                                } else {
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
                                }
                            },
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
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(end = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                text = if (selectedAmenity?.isAccessible == true) "OPEN" else "CLOSED",
                                color = if (selectedAmenity?.isAccessible == true) androidx.compose.ui.graphics.Color(0xFF00C853) else androidx.compose.ui.graphics.Color(0xFFE74C3C),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

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

                        Spacer(modifier = Modifier.height(10.dp))

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
                                    currentDestination = selectedDest
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
                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 36.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                            ){
                            Button(
                                onClick = {
                                    navController.navigate(Routes.userReport(
                                        selectedAmenityId,
                                        selectedAmenity?.name
                                    ))
                                },
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Submit an Issue", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Custom Filters with Checkboxes
        if (showFilterDialog) {
            hasArrived = false
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.White)
                    .zIndex(10f) // ensure it sits above the map
                    .padding(24.dp)
            ) {
                var tempWheelchair by remember { mutableStateOf(wheelchairFilter) }
                var tempMens by remember { mutableStateOf(mensFilter) }
                var tempWomens by remember { mutableStateOf(womensFilter) }
                var tempStatus by remember { mutableStateOf(amenityStatusFilter) }
                var tempLowLvl by remember { mutableStateOf(lowCrowdLvlFilter) }
                var tempMediumLvl by remember { mutableStateOf(mediumCrowdLvlFilter) }
                var tempHighLvl by remember { mutableStateOf(highCrowdLvlFilter) }
                var tempNearestAvailable by remember { mutableStateOf(nearestAvailableFilter) }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { showFilterDialog = false }
                        )

                        Text(
                            "Custom Filters",
                            modifier = Modifier.padding(end = 70.dp),
                            fontFamily = FontFamily(Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold)),
                            fontSize = 30.sp
                        )

                        Box(
                            modifier = Modifier
                                .background(androidx.compose.ui.graphics.Color.Black, shape = RoundedCornerShape(8.dp))
                                .clickable {
                                    // Reset all filters
                                    tempWheelchair = false
                                    tempMens = false
                                    tempWomens = false
                                    tempStatus = null
                                    tempLowLvl = false
                                    tempMediumLvl = false
                                    tempHighLvl = false
                                    tempNearestAvailable = false
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Reset",
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = androidx.compose.ui.graphics.Color.LightGray
                    )

                    // Checkboxes/Filter Options
                    Text(
                        text = "Restroom Types",
                        fontFamily = FontFamily(Font(R.font.barlowcondensed_medium, FontWeight.Medium)),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempWheelchair = !tempWheelchair }
                    ) {
                        Checkbox(
                            checked = tempWheelchair,
                            onCheckedChange = { tempWheelchair = it }
                        )
                        Text("Wheelchair Accessible")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempMens = !tempMens }
                    ) {
                        Checkbox(
                            checked = tempMens,
                            onCheckedChange = { tempMens = it }
                        )
                        Text("Men's Restrooms")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempWomens = !tempWomens }
                    ) {
                        Checkbox(
                            checked = tempWomens,
                            onCheckedChange = { tempWomens = it }
                        )
                        Text("Women's Restrooms")
                    }
                    Text(
                        text = "Restroom Status",
                        fontFamily = FontFamily(Font(R.font.barlowcondensed_medium, FontWeight.Medium)),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!tempNearestAvailable) {
                                    tempStatus = if (tempStatus == "open") null else "open"
                                }
                            }
                    ) {
                        RadioButton(
                            selected = tempStatus == "open",
                            onClick = {
                                if (!tempNearestAvailable) {
                                    tempStatus = if (tempStatus == "open") null else "open"
                                }
                            }
                        )
                        Text("Open")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!tempNearestAvailable) {
                                    tempStatus = if (tempStatus == "closed") null else "closed"
                                }
                            }
                    ) {
                        RadioButton(
                            selected = tempStatus == "closed",
                            onClick = {
                                if (!tempNearestAvailable) {
                                    tempStatus = if (tempStatus == "closed") null else "closed"
                                }
                            }
                        )
                        Text("Closed")
                    }
                    Text(
                        text = "Proximity",
                        fontFamily = FontFamily(Font(R.font.barlowcondensed_medium, FontWeight.Medium)),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempNearestAvailable = !tempNearestAvailable
                                if (tempNearestAvailable) tempStatus = "open" else tempStatus = null
                            }
                    ) {
                        Checkbox(
                            checked = tempNearestAvailable,
                            onCheckedChange = { checked ->
                                tempNearestAvailable = checked
                                if (checked) tempStatus = "open" else tempStatus = null
                            }
                        )
                        Text("Nearest Available Option")
                    }
                    Text(
                        text = "Crowd Level",
                        fontFamily = FontFamily(Font(R.font.barlowcondensed_medium, FontWeight.Medium)),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempLowLvl = !tempLowLvl }
                    ) {
                        Checkbox(
                            checked = tempLowLvl,
                            onCheckedChange = { tempLowLvl = it }
                        )
                        Text("Low")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempMediumLvl = !tempMediumLvl }
                    ) {
                        Checkbox(
                            checked = tempMediumLvl,
                            onCheckedChange = { tempMediumLvl = it }
                        )
                        Text("Medium")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempHighLvl = !tempHighLvl }
                    ) {
                        Checkbox(
                            checked = tempHighLvl,
                            onCheckedChange = { tempHighLvl = it }
                        )
                        Text("High")
                    }

                    Spacer(Modifier.weight(1f))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Color.Black, shape = RoundedCornerShape(10.dp))
                                .clickable {
                                    applyCustomFilters(tempWheelchair, tempMens, tempWomens, tempStatus, tempLowLvl, tempMediumLvl, tempHighLvl, tempNearestAvailable)
                                    if (!noFilteringResultsFound) {
                                        showAmenityBox = false
                                        showFilterDialog = false

                                        wheelchairFilter = tempWheelchair
                                        mensFilter = tempMens
                                        womensFilter = tempWomens
                                        amenityStatusFilter = tempStatus
                                        lowCrowdLvlFilter = tempLowLvl
                                        mediumCrowdLvlFilter = tempMediumLvl
                                        highCrowdLvlFilter = tempHighLvl
                                        nearestAvailableFilter = tempNearestAvailable

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
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Apply Filters",
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // Error message overlay with fade animation when no results found using custom filters
                AnimatedVisibility(
                    visible = noFilteringResultsFound,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.Red, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "No results were found.",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 22.sp
                        )
                    }
                }
            }
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
            if (currentNavInstruction.isNotEmpty() && !showAmenityBox) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 12.dp, bottom = 8.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFF61A2FF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {moveUserToNextNode()}
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "Move to Next Position",
                            color = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = screenHeight * 0.7f)   // cap at 70% of screen
                            .animateContentSize()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    if (delta < -5) expanded =
                                        true   // drag navigation instruction box up
                                    if (delta > 5) expanded =
                                        false   // drag navigation instruction box down
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.95f)
                                .padding(bottom = 12.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(0xFF00008B)
                                        .copy(alpha = 0.9f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Column {
                                // Drag handle for navigation instruction box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                androidx.compose.ui.graphics.Color.White.copy(
                                                    alpha = 0.4f
                                                )
                                            )
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val iconRes = when {
                                            currentNavInstruction.contains(
                                                "right",
                                                ignoreCase = true
                                            ) ->
                                                R.drawable.right_turn_arrow

                                            currentNavInstruction.contains(
                                                "left",
                                                ignoreCase = true
                                            ) ->
                                                R.drawable.left_turn_arrow

                                            else ->
                                                R.drawable.arrived_icon
                                        }

                                        Image(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp)
                                        )

                                        Text(
                                            text = currentNavDistanceToTurn,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontSize = 24.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(20.dp))

                                    Column(
                                        modifier = Modifier
                                            .width(175.dp)
                                            .align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = currentNavInstruction,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontSize = 32.sp,
                                            style = TextStyle(
                                                lineHeight = 32.sp // increase this for more spacing
                                            )
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(start = 18.dp)
                                                .background(
                                                    androidx.compose.ui.graphics.Color(
                                                        0xffdd0d3c
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { cancelNavigation() }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "End",
                                                color = androidx.compose.ui.graphics.Color(
                                                    0xFFF5F5F5
                                                ),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                val tempCurrDirections = currentDirectionsWithDistances.drop(1)
                                if (expanded && tempCurrDirections.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .padding(bottom = 4.dp)
                                    ) {
                                        items(tempCurrDirections) { step ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (step.contains("Left", ignoreCase= true)) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.left_turn_arrow),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                } else if (step.contains("Right", ignoreCase= true)) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.right_turn_arrow),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                } else if (step.contains("U-Turn", ignoreCase= true)) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.left_turn_arrow),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                } else if (step.contains("Arrive", ignoreCase= true)) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.arrived_icon),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = step,
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                    fontSize = 20.sp,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (amenityClosedInRoute) {
                val lat = currentDestination?.second
                val lng = currentDestination?.first
                val currDest = amenities.find { it.nodeId == mapNodes.find { (it.latitude == String.format(Locale.US, "%.5f", lat).toDouble() || it.latitude == String.format(Locale.US, "%.6f", lat).toDouble()) && (it.longitude == String.format(Locale.US, "%.5f", lng).toDouble() || it.longitude == String.format(Locale.US, "%.6f", lng).toDouble()) }?.id }
                val reroutedNode = mapNodes.find { it.id == reroutedAmenity?.nodeId }
                AnimatedVisibility(
                    visible = amenityClosedInRoute,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 4.dp
                    ) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text("Destination Closed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                            if (currDest?.subType.equals("Male", true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                applyCustomFilters(false, true, false, "open", false, false, false, true)
                                Text(
                                    "Would you like to reroute to the nearest available Men's restroom?",
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            } else if (currDest?.subType.equals("Female", true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                applyCustomFilters(false, false, true, "open", false, false, false, true)
                                Text(
                                    "Would you like to reroute to the nearest available Women's restroom?",
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            } else if (currDest?.subType.equals("Neutral", true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                applyCustomFilters(true, false, false, "open", false, false, false, true)
                                Text(
                                    "Would you like to reroute to the nearest available Wheelchair Accessible restroom?",
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val rerouteDist = reroutedAmenity?.let { distanceToUser(it) }?.toInt()
                            val rerouteTime = rerouteDist?.div(40.0)?.toInt()
                            if (rerouteTime?.equals(0) == true)
                                Text("Estimated Distance: ${"${rerouteDist}ft (Less than 1min)"}",
                                    color = androidx.compose.ui.graphics.Color.White)
                            else
                                Text("Estimated Distance: ${"${rerouteDist}ft (${rerouteTime}min)"}",
                                    color = androidx.compose.ui.graphics.Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                // cancel reroute button
                                Button(
                                    onClick = {
                                        amenityClosedInRoute = false
                                        reroutedAmenity = null
                                        val bearing = mapRef.value?.cameraPosition?.bearing
                                        if (bearing != null) {
                                            mapRef.value?.animateCamera(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(
                                                            LatLng(
                                                                userLocation.latitude,
                                                                userLocation.longitude
                                                            )
                                                        )
                                                        .zoom(18.0)
                                                        .tilt(45.0)
                                                        .bearing(bearing) // Face direction of the user
                                                        .build()
                                                ),
                                                500
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.width(12.dp))
                                // accept reroute button
                                Button(
                                    onClick = {
                                        amenityClosedInRoute = false
                                        if (reroutedNode != null) {
                                            startNavigation(reroutedNode.longitude, reroutedNode.latitude)
                                            currentDestination = (reroutedNode.longitude to reroutedNode.latitude)
                                        }
                                        reroutedAmenity = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Reroute") }
                            }
                        }
                    }
                }
                if (reroutedNode != null) {
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(reroutedNode.latitude, reroutedNode.longitude))
                                .zoom(19.0)
                                .bearing(180.0) // Face south
                                .tilt(45.0)
                                .build()
                        ), 2000
                    )
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
        PropertyFactory.fillColor(color("#F5F5F7".toColorInt())),
        PropertyFactory.fillOpacity(0.5f),
        PropertyFactory.fillOutlineColor(color("#BCBCBC".toColorInt()))
    ).withFilter(eq(get("type"), literal("building"))))

    style.addLayer(FillLayer("room-layer", "floorplan-source").withProperties(
        PropertyFactory.fillColor(match(get("gender"),
            literal("male"), color("#3399FF".toColorInt()),
            literal("female"), color("#FF66B2".toColorInt()),
            literal("neutral"), color("#9933FF".toColorInt()),
            color(Color.GRAY))),
        PropertyFactory.fillOutlineColor(Color.DKGRAY)
    ).withFilter(any(
        eq(get("type"), literal("restroom"))
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
    val mensIcon = loadVectorToBitmap(context, R.drawable.mens, 60, 60)
    val womensIcon = loadVectorToBitmap(context, R.drawable.womens, 60, 60)
    val neutralIcon = loadVectorToBitmap(context, R.drawable.neutral, 60, 60)

    // Register with the Style
    style.addImage("icon-male", mensIcon)
    style.addImage("icon-female", womensIcon)
    style.addImage("icon-neutral", neutralIcon)


    // Add Markers pulled from firebase
    val markerSourceId = "marker-source"
    style.addSource(GeoJsonSource(markerSourceId))

    style.addLayer(
        SymbolLayer("marker-layer", markerSourceId).withProperties(
            // The "match" expression checks the 'gender' property of each feature
            iconImage(
                match(
                    get("gender"),
                    literal("icon-neutral"), // Default value
                    stop("male", "icon-male"),
                    stop("female", "icon-female"),
                    stop("neutral", "icon-neutral")
                )
            ),
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
        )
    )

    // For displaying the markers when user sets custom filters
    style.addSource(
        GeoJsonSource("amenity-source", FeatureCollection.fromFeatures(emptyList()))
    )
    style.addLayerAbove(
        SymbolLayer("amenity-layer", "amenity-source").withProperties(
            iconImage(
                match(
                    get("subType"),
                    literal("icon-neutral"), // Default value
                    stop("male", "icon-male"),
                    stop("female", "icon-female"),
                    stop("neutral", "icon-neutral")
                )
            ),
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
private fun angleBetween(a: Node, b: Node, c: Node): Double {
    val v1x = b.lng - a.lng
    val v1y = b.lat - a.lat
    val v2x = c.lng - b.lng
    val v2y = c.lat - b.lat

    val dot = v1x * v2x + v1y * v2y
    val mag1 = sqrt(v1x * v1x + v1y * v1y)
    val mag2 = sqrt(v2x * v2x + v2y * v2y)

    val cos = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cos))
}

private fun generateDirections(path: List<Node>): Pair<List<String>, List<Int>> {
    val directions = mutableListOf<String>()
    val segments = mutableListOf<Int>()

    for (i in 1 until path.size - 1) {
        val a = path[i - 1]
        val b = path[i]
        val c = path[i + 1]

        val angle = angleBetween(a, b, c)

        val instruction = when {
            angle > 150 -> "Make a U-turn"
            angle > 30 && angle <= 150 -> {
                val cross = (b.lng - a.lng) * (c.lat - b.lat) -
                        (b.lat - a.lat) * (c.lng - b.lng)
                if (cross > 0) "Turn left" else "Turn right"
            }
            else -> null
        }

        if (instruction != null) {
            directions.add(instruction)
            segments.add(i)
        }
    }

    directions.add("Arrive at destination")
    segments.add(path.size - 1)

    return directions to segments
}

private fun distanceToSegment(p: Node, a: Node, b: Node): Double {
    // Convert to vectors
    val px = p.lng
    val py = p.lat
    val ax = a.lng
    val ay = a.lat
    val bx = b.lng
    val by = b.lat

    // Segment vector
    val abx = bx - ax
    val aby = by - ay

    // Handle degenerate segment (a == b)
    val abLenSq = abx * abx + aby * aby
    if (abLenSq == 0.0) {
        return haversine(py, px, ay, ax)
    }

    // Project point p onto segment AB (normalized t from 0 to 1)
    val apx = px - ax
    val apy = py - ay
    var t = (apx * abx + apy * aby) / abLenSq

    // Clamp projection to segment endpoints
    t = t.coerceIn(0.0, 1.0)

    // Closest point on segment
    val closestX = ax + t * abx
    val closestY = ay + t * aby

    // Return distance in meters
    return haversine(py, px, closestY, closestX)
}

private fun findClosestSegmentIndex(user: Node, path: List<Node>): Int {
    var bestIndex = 0
    var bestDist = Double.MAX_VALUE

    for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        val projected = Pathfinding.closestPointOnSegment(user, a, b)

        val dx = projected.lng - user.lng
        val dy = projected.lat - user.lat
        val dist = dx*dx + dy*dy

        if (dist <= bestDist) {
            bestDist = dist
            bestIndex = i
        }
    }

    return bestIndex
}

// calculates total distance of the computed path from the user to a specific amenity
private fun pathLength(path: List<Node>): Double {
    if (path.size < 2) return Double.POSITIVE_INFINITY

    var total = 0.0
    for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        total += haversine(a.lat, a.lng, b.lat, b.lng)
    }
    return total
}

private fun generateFullRouteList(path: List<Node>, instructions: List<String>, segments: List<Int>, currentStepIndex: Int, distanceToTurn: Int): List<String> {
    val results = mutableListOf<String>()

    for (i in currentStepIndex until instructions.size) {
        val instruction = instructions[i]
        var distance = 0.0
        if (i != currentStepIndex) {
            val startIndex = maxOf(0, segments[i] - 1)

            // Distance until next turn or destination
            val endIndex = if (i < segments.size - 1) segments[i + 1] else path.size - 1

            for (j in (startIndex + 1)..endIndex) {
                val a = path[j - 1]
                val b = path[j]
                distance += haversine(a.lat, a.lng, b.lat, b.lng)
            }
        } else { distance = distanceToTurn.toDouble() }

        val text = "In ${"%.0f".format(distance)}ft, $instruction"

        results.add(text)
    }
    Log.d("NAV ROUTE RESULTS", "$results")

    return results
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
                val gender = doc["gender"] as? String ?: ""
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
                featureList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level", "gender":"$gender" }, "geometry": {"type": "Polygon", "coordinates": [[$coordString]]}}""")
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
                val gender = doc["gender"] as? String ?: ""
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
                    nodeList.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", "id": "$id", "level": "$level", "gender":"$gender" }, "geometry": {"type": "Point", "coordinates": [$lng, $lat]}}""")
                }
            }
            val geoJson = """{"type": "FeatureCollection", "features": [${nodeList.joinToString(",")}]}"""
            style.getSourceAs<GeoJsonSource>("marker-source")?.setGeoJson(geoJson)
        }

    // 4. Fetch Amenities
    functions.getHttpsCallable("getAmenities").call()
        .addOnSuccessListener { result ->
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? List<Map<String, Any>> ?: return@addOnSuccessListener
            amenities.clear()
            data.forEach { map ->
                amenities.add(
                    AmenityDetail(
                        id = map["AmenityID"] as? String ?: "",
                        name = map["Name"] as? String ?: "Unknown",
                        type = map["AmenityType"] as? String ?: "",
                        subType = map["SubTypeName"] as? String ?: "",
                        congestion = map["Congestion"] as? String ?: "Low",
                        lastUpdated = (map["LastUpdated"] as? Number)?.toLong() ?: 0L,
                        isAccessible = map["IsAccessible"] as? Boolean ?: false,
                        nodeId = map["NodeID"] as? String ?: ""
                    )
                )
            }
            mapViewModel.storeAmenities(amenities)
        }
}
