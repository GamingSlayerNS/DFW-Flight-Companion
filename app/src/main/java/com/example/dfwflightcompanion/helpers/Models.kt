package com.example.dfwflightcompanion.helpers

import org.maplibre.android.geometry.LatLng

data class MapBackground(
    val id: String,
    val name: String,
    val type: String,
    val level: Int,
    val coordinates: List<LatLng>,
    val gender: String? = null
)

data class MapNode(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val level: Int,
    val type: String = "",
    val name: String = "",
    val gender: String = ""
)

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

data class Amenity(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val subType: String = "",
    val congestion: String = "",
    val lastUpdated: Long = 0L,
    val isAccessible: Boolean = false,
    val nodeId: String = ""
)

data class RoutingNode(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val level: Int = 0,
    val wait_time: Double = 0.0
)

data class RoutingEdge(
    val distance: Double = 0.0,
    val type: String = "",
    val congestion: Double = 0.0
)

data class RoutingGraph(
    val nodes: Map<String, RoutingNode> = emptyMap(),
    // Edges are a Map of Node ID -> Map of Connected Node ID -> Edge Details
    val edges: Map<String, Map<String, RoutingEdge>> = emptyMap()
)