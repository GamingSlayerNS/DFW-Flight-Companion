package com.example.dfwflightcompanion.amenities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.dfwflightcompanion.map.MapViewModel
import com.example.dfwflightcompanion.Routes
import com.example.dfwflightcompanion.helpers.Amenity
import com.example.dfwflightcompanion.navigation.Destination
import com.example.dfwflightcompanion.helpers.haversine
import kotlin.math.roundToInt

enum class GenderFilter { NONE, MALE, FEMALE, NEUTRAL }
enum class SortMode { ALPHABETICAL, DISTANCE }

@Composable
fun AmenitiesScreen(
    navController: NavHostController,
    mapViewModel: MapViewModel
) {
    // var amenities by remember { mutableStateOf<List<Amenity>>(emptyList()) }
    val amenities = mapViewModel.amenities
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var genderFilter by remember { mutableStateOf(GenderFilter.NONE) }
    var sortMode by remember { mutableStateOf(SortMode.ALPHABETICAL) }

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
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp)
        ) {
            FilterChip(
                selected = genderFilter == GenderFilter.MALE,
                onClick = {
                    genderFilter =
                        if (genderFilter == GenderFilter.MALE) GenderFilter.NONE
                        else GenderFilter.MALE
                },
                label = { Text("Male") }
            )
            FilterChip(
                selected = genderFilter == GenderFilter.FEMALE,
                onClick = {
                    genderFilter =
                        if (genderFilter == GenderFilter.FEMALE) GenderFilter.NONE
                        else GenderFilter.FEMALE
                },
                label = { Text("Female") }
            )
            FilterChip(
                selected = genderFilter == GenderFilter.NEUTRAL,
                onClick = {
                    genderFilter =
                        if (genderFilter == GenderFilter.NEUTRAL) GenderFilter.NONE
                        else GenderFilter.NEUTRAL
                },
                label = { Text("Neutral / Accessible") },
                trailingIcon = {
                    Icon(Icons.Default.Accessible, contentDescription = null)
                }
            )
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = sortMode == SortMode.ALPHABETICAL,
                onClick = { sortMode = SortMode.ALPHABETICAL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("A–Z") }
            SegmentedButton(
                selected = sortMode == SortMode.DISTANCE,
                onClick = { sortMode = SortMode.DISTANCE },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Distance") }
        }
        Spacer(modifier = Modifier.height(10.dp))

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
            val mapNodes = mapViewModel.mapNodes
            val userLoc = mapViewModel.userLocation

            val visibleAmenities = remember(amenities, genderFilter, sortMode, mapNodes, userLoc) {
                amenities
                    .filter { a ->
                        val genderOk = when (genderFilter) {
                            GenderFilter.NONE    -> true
                            GenderFilter.MALE    -> a.subType.equals("Male", true)
                            GenderFilter.FEMALE  -> a.subType.equals("Female", true)
                            GenderFilter.NEUTRAL -> a.subType.equals("Neutral", true)
                        }
                        genderOk
                    }
                    .let { list ->
                        when (sortMode) {
                            SortMode.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
                            SortMode.DISTANCE -> list.sortedBy { a ->
                                val node = mapNodes.firstOrNull { it.id == a.nodeId }
                                if (node == null) Double.MAX_VALUE
                                else haversine(userLoc.latitude, userLoc.longitude, node.latitude, node.longitude)
                            }
                        }
                    }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleAmenities) { amenity ->
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
        val node = mapViewModel.mapNodes.find { it.id == amenity.nodeId }
        val distanceFt: Int? = node?.let {
            val meters = haversine(
                mapViewModel.userLocation.latitude,
                mapViewModel.userLocation.longitude,
                it.latitude,
                it.longitude
            )
            (meters * 3.28084).roundToInt()
        }
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
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
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
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            distanceFt?.let {
                                Text(
                                    text = "$it ft away",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                if (amenity.subType.equals("Neutral", true)) {
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
