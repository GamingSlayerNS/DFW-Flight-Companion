package com.example.dfwflightcompanion.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
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