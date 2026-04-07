package com.example.dfwflightcompanion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isNavigating by remember { mutableStateOf(false) }
    
    // User's current location (Simulation)
    val userLocation = remember { mutableStateOf(LatLng(32.8993, -97.0446)) }

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
                map.setStyle(
                    Style.Builder()
                        .fromUri("https://demotiles.maplibre.org/style.json")
                ) { style ->
                    val sourceId = "floorplan-source"
                    style.addSource(GeoJsonSource(sourceId, java.net.URI("asset://floorplan.geojson")))

                    // Background Layer (Building)
                    style.addLayer(
                        FillLayer("building-layer", sourceId).withProperties(
                            PropertyFactory.fillColor(color(Color.LTGRAY)),
                            PropertyFactory.fillOpacity(0.5f)
                        ).withFilter(eq(get("type"), literal("building")))
                    )

                    // Hallway Layer
                    style.addLayer(
                        FillLayer("hallway-layer", sourceId).withProperties(
                            PropertyFactory.fillColor(color(Color.WHITE))
                        ).withFilter(eq(get("type"), literal("hallway")))
                    )

                    // Room Layer (Gates, Restrooms, etc.)
                    style.addLayer(
                        FillLayer("room-layer", sourceId).withProperties(
                            PropertyFactory.fillColor(
                                match(
                                    get("type"),
                                    literal("room"), color("#BBDEFB".toColorInt()),
                                    literal("restroom"), color("#C8E6C9".toColorInt()),
                                    literal("entrance"), color("#FFF9C4".toColorInt()),
                                    literal("exit"), color("#FFCDD2".toColorInt()),
                                    color(Color.GRAY)
                                )
                            ),
                            PropertyFactory.fillOutlineColor(Color.DKGRAY)
                        ).withFilter(
                            any(
                                eq(get("type"), literal("room")),
                                eq(get("type"), literal("restroom")),
                                eq(get("type"), literal("entrance")),
                                eq(get("type"), literal("exit"))
                            )
                        )
                    )

                    // Routing Layer (Red Lines)
                    val routingSourceId = "routing-source"
                    style.addSource(GeoJsonSource(routingSourceId, java.net.URI("asset://routing.geojson")))
                    style.addLayer(
                        LineLayer("routing-layer", routingSourceId).withProperties(
                            PropertyFactory.lineColor(Color.RED),
                            PropertyFactory.lineWidth(1f),
                            PropertyFactory.lineOpacity(0.6f)
                        ).withFilter(eq(get("type"), literal("path")))
                    )

                    // Navigation Route Layer (Active path)
                    val routeSourceId = "route-source"
                    style.addSource(GeoJsonSource(routeSourceId))
                    style.addLayer(
                        LineLayer("route-layer", routeSourceId).withProperties(
                            PropertyFactory.lineColor(Color.BLUE),
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineJoin("round")
                        )
                    )

                    // User Location Layer (Blue Dot)
                    val userSourceId = "user-location-source"
                    val userPointJson = """
                        {
                          "type": "Feature",
                          "geometry": {
                            "type": "Point",
                            "coordinates": [${userLocation.value.longitude}, ${userLocation.value.latitude}]
                          }
                        }
                    """.trimIndent()
                    style.addSource(GeoJsonSource(userSourceId, userPointJson))
                    style.addLayer(
                        CircleLayer("user-location-layer", userSourceId).withProperties(
                            PropertyFactory.circleColor(Color.BLUE),
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleStrokeColor(Color.WHITE),
                            PropertyFactory.circleStrokeWidth(2f)
                        )
                    )

                    // Adding Markers
                    val markerBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.restroom)
                    val safeMarkerBitmap = markerBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    val scaledMarkerBitmap = Bitmap.createScaledBitmap(safeMarkerBitmap, 200, 200, true)

                    if(style.getImage("marker-icon") == null) {
                        style.addImage("marker-icon", scaledMarkerBitmap)
                    }

                    // This adds a single marker
                    // This will have to be replaced with all of the markers from Firebase and be displayed
                    val markerPoint = Feature.fromGeometry(Point.fromLngLat(-97.04492, 32.89880)) // Restroom NW
                    val markerSourceId = "marker-source"
                    style.addSource(GeoJsonSource(markerSourceId, markerPoint))

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

                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(32.897, -97.042))
                                .zoom(16.0)
                                .build()
                        )
                    )
                }
            }
        }
    }

    var selectedDest by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Function to simulate navigation
    val startNavigation = { destLng: Double, destLat: Double ->
        mapView.getMapAsync { map ->
            val style = map.style
            if (style != null) {
                val routeSource = style.getSourceAs<GeoJsonSource>("route-source")

                // Retrieving user's location
                val userLng = userLocation.value.longitude
                val userLat = userLocation.value.latitude

                // Find nearest nodes for start and end locations
                val startNode = Pathfinding.findNearestNode(
                    userLng,
                    userLat,
                    graph.keys
                )
                val endNode = Pathfinding.findNearestNode(
                    destLng,
                    destLat,
                    graph.keys
                )

                // Compute path
                val pathNodes = Pathfinding.aStar(graph, startNode, endNode)
                if(pathNodes.isEmpty()) return@getMapAsync

                // Convert to GeoJSON coordinates
                val coordinates = pathNodes.joinToString(","){
                    "[${it.lng}, ${it.lat}]"
                }

                // Route from User Location to Gate A1
                //                          [${userLocation.value.longitude}, ${userLocation.value.latitude}],
                //                          [-97.04492, 32.89880]
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
                            .target(LatLng(32.8985, -97.0446))
                            .zoom(18.0)
                            .bearing(180.0) // Face south
                            .tilt(45.0)
                            .build()
                    ), 2000
                )
            }
        }
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
                        selectedDest = destLng to destLat   // store destination
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Compose UI layer
        if (showAmenityBox) {
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
                            text = "Women's Restroom",
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

//        FloatingActionButton(
//            onClick = {
//                isNavigating = !isNavigating
//                if (isNavigating) startNavigation() else {
//                    // Reset route
//                    mapView.getMapAsync { map ->
//                        map.style?.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson("{}")
//                        map.animateCamera(CameraUpdateFactory.zoomTo(16.0))
//                    }
//                }
//            },
//            modifier = Modifier
//                .align(Alignment.BottomEnd)
//                .padding(16.dp)
//        ) {
//            Icon(
//                imageVector = Icons.Default.Navigation,
//                contentDescription = if (isNavigating) "Stop Navigation" else "Start Navigation"
//            )
//        }
    }
}
