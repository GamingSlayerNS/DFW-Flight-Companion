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
