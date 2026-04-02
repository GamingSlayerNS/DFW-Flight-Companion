package com.example.dfwflightcompanion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraphBuilder
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
                    startNavigation(destLng, destLat)
                }
                true
            } else {
                false
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

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
