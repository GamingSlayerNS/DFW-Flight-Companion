package com.example.dfwflightcompanion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String
) {
    MAP("map", Icons.Default.Map, "Map", "Map Screen"),
    AMENITIES("amenities", Icons.Default.List, "Amenities", "Amenities Screen"),
    PROFILE("profile", Icons.Default.Person, "Profile", "Profile Screen")
}