package com.example.dfwflightcompanion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

data class Amenity(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val subType: String = "",
    val isAccessible: Boolean = false,
    val nodeId: String = ""
)

@Composable
fun AmenitiesScreen(
    navController: NavHostController,
    mapViewModel: MapViewModel
) {
    // var amenities by remember { mutableStateOf<List<Amenity>>(emptyList()) }
    val amenities = mapViewModel.amenities
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    /* LaunchedEffect(Unit) {
        try {
            val functions = Firebase.functions
            // ONLY if testing locally:
            functions.useEmulator("10.0.2.2", 5001)

            functions.getHttpsCallable("getAmenities")
                .call()
                .addOnSuccessListener { result ->
                    val data = result.getData() as? List<Map<String, Any>>
                    if (data != null) {
                        val fetchedAmenities = data.map { map ->
                            Amenity(
                                id = map["AmenityID"] as? String ?: "",
                                name = map["Name"] as? String ?: "Unknown",
                                type = map["AmenityType"] as? String ?: "",
                                subType = map["SubTypeName"] as? String ?: "",
                                isAccessible = map["IsAccessible"] as? Boolean ?: false,
                                nodeId = map["NodeID"] as? String ?: ""
                            )
                        }
                        amenities = fetchedAmenities
                    }
                    isLoading = false
                }
                .addOnFailureListener { exception ->
                    Log.e("AmenitiesScreen", "Error calling getAmenities function", exception)
                    errorMessage = "Failed to load amenities: ${exception.message}"
                    isLoading = false
                }
        } catch (e: Exception) {
            Log.e("AmenitiesScreen", "Failed to initialize functions", e)
            errorMessage = "Initialization error: ${e.message}"
            isLoading = false
        }
        amenities = mapViewModel.amenities
    } */

    LaunchedEffect(amenities) {
        if (amenities.isNotEmpty()) {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Terminal Amenities",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (amenities.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No amenities found.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(amenities) { amenity ->
                    AmenityCard(
                        amenity = amenity,
                        navController = navController,
                        mapViewModel = mapViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AmenityCard(
    amenity: Amenity,
    navController: NavHostController,
    mapViewModel: MapViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            /*.clickable {
                mapViewModel.selectAmenity(amenity.id)
                navController.navigate(Destination.MAP.route)
            }*/,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = amenity.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${amenity.type}${if (amenity.subType.isNotEmpty()) " - ${amenity.subType}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = " Node: ${amenity.nodeId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                if (amenity.isAccessible) {
                    Icon(
                        imageVector = Icons.Default.Accessible,
                        contentDescription = "Accessible",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ){
                OutlinedButton(
                    onClick = {
                        navController.navigate(Routes.amenityDetails(amenity.id))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Details")
                }

                Button(
                    onClick = {
                        mapViewModel.selectAmenity(amenity.id)
                        navController.navigate(Destination.MAP.route)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Locate Amenity")
                }
            }
        }
    }
}
