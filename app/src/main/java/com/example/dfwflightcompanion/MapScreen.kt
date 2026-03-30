package com.example.dfwflightcompanion

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
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isNavigating by remember { mutableStateOf(false) }
    
    // User's current location (Simulation)
    val userLocation = remember { mutableStateOf(LatLng(32.8993, -97.0446)) }

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
    val startNavigation = {
        mapView.getMapAsync { map ->
            val style = map.style
            if (style != null) {
                val routeSource = style.getSourceAs<GeoJsonSource>("route-source")
                
                // Route from User Location to Gate A1
                val routeJson = """
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [${userLocation.value.longitude}, ${userLocation.value.latitude}],
                          [-97.0446, 32.8985]
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

        FloatingActionButton(
            onClick = {
                isNavigating = !isNavigating
                if (isNavigating) startNavigation() else {
                    // Reset route
                    mapView.getMapAsync { map ->
                        map.style?.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson("{}")
                        map.animateCamera(CameraUpdateFactory.zoomTo(16.0))
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = if (isNavigating) "Stop Navigation" else "Start Navigation"
            )
        }
    }
}
