package com.example.dfwflightcompanion.amenities

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.dfwflightcompanion.Routes
import com.example.dfwflightcompanion.helpers.AmenityDetail
import com.example.dfwflightcompanion.map.MapViewModel
import com.example.dfwflightcompanion.map.formatTimeAgo
import com.example.dfwflightcompanion.navigation.Destination
import com.google.firebase.Firebase
import com.google.firebase.functions.functions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmenityDetailsScreen(
    amenityId: String,
    navController: NavHostController,
    mapViewModel: MapViewModel
) {
    var details by remember { mutableStateOf<AmenityDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCrowdDialog by remember { mutableStateOf(false) }

    // Fetch fresh data from Firebase whenever the screen opens or the id changes
    LaunchedEffect(amenityId) {
        isLoading = true
        errorMessage = null
        try {
            val functions = Firebase.functions
            //functions.useEmulator("10.0.2.2", 5001) // remove for production builds

            val data = hashMapOf("amenityId" to amenityId)
            functions.getHttpsCallable("getAmenityById").call(data)
                .addOnSuccessListener { result ->
                    @Suppress("UNCHECKED_CAST")
                    val map = result.getData() as? Map<String, Any>
                    if (map != null) {
                        details = AmenityDetail(
                            id = map["id"] as? String ?: amenityId,
                            name = map["Name"] as? String ?: "Unknown",
                            type = map["AmenityType"] as? String ?: "",
                            subType = map["SubTypeName"] as? String ?: "",
                            congestion = map["Congestion"] as? String ?: "Unknown",
                            lastUpdated = (map["LastUpdated"] as? Number)?.toLong() ?: 0L,
                            isAccessible = map["IsAccessible"] as? Boolean ?: false,
                            nodeId = map["NodeID"] as? String ?: ""
                        )
                    } else {
                        errorMessage = "Amenity data was empty."
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    Log.e("AmenityDetails", "getAmenityById failed", e)
                    errorMessage = "Failed to load amenity: ${e.message}"
                    isLoading = false
                }
        } catch (e: Exception) {
            Log.e("AmenityDetails", "Failed to initialize functions", e)
            errorMessage = "Initialization error: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amenity Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                errorMessage != null -> Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )

                details != null -> AmenityDetailsContent(
                    navController = navController,
                    details = details!!,
                    onUpdateCrowd = { showCrowdDialog = true },
                    onNavigate = {
                        mapViewModel.selectAmenity(details!!.id)
                        mapViewModel.requestAutoStartNavigation(true)
                        navController.navigate(Destination.MAP.route) {
                            popUpTo(Destination.MAP.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }

    if (showCrowdDialog && details != null) {
        UpdateCrowdLevelDialog(
            amenityId = details!!.id,
            onLevelUpdated = { newLevel, timestamp ->
                details = details!!.copy(
                    congestion = newLevel,
                    lastUpdated = timestamp
                )
                showCrowdDialog = false
            },
            onDismiss = { showCrowdDialog = false }
        )
    }
}

@Composable
private fun AmenityDetailsContent(
    navController: NavHostController,
    details: AmenityDetail,
    onUpdateCrowd: () -> Unit,
    onNavigate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = details.name,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(label = "Type", value = details.type)
                if (details.subType.isNotEmpty()) {
                    DetailRow(label = "Subtype", value = details.subType)
                }

                HorizontalDivider()

                DetailRow(
                    label = "Crowd Level",
                    value = details.congestion,
                    valueColor = crowdLevelColor(details.congestion)
                )
                DetailRow(
                    label = "Last Updated",
                    value = formatTimeAgo(details.lastUpdated)
                )

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Accessible,
                        contentDescription = null,
                        tint = if (details.isAccessible)
                            MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (details.isAccessible)
                            "Wheelchair Accessible"
                        else
                            "Not Wheelchair Accessible"
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Node: ${details.nodeId}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onUpdateCrowd,
                modifier = Modifier.weight(1f)
            ) {
                Text("Update Crowd Level")
            }
            Button(
                onClick = onNavigate,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Navigate")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ){
            Button(
                onClick = {
                    navController.navigate(Routes.userReport(
                        amenityId = details.id,
                        amenityName = details.name
                    ))
                },
                //modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Submit an Issue")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value, color = valueColor)
    }
}

private fun crowdLevelColor(level: String): Color = when (level.lowercase()) {
    "low" -> Color(0xFF00C853)
    "medium" -> Color(0xFFFFA000)
    "high" -> Color(0xFFD50000)
    else -> Color.Unspecified
}

@Composable
private fun UpdateCrowdLevelDialog(
    amenityId: String,
    onLevelUpdated: (level: String, timestamp: Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Crowd Level") },
        text = {
            Column {
                listOf("Low", "Medium", "High").forEach { level ->
                    Text(
                        text = level,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val functions = Firebase.functions
                                val data = hashMapOf(
                                    "amenityId" to amenityId,
                                    "congestion" to level
                                )
                                functions.getHttpsCallable("updateAmenityCongestion")
                                    .call(data)
                                    .addOnSuccessListener {
                                        Log.d("AmenityDetails", "Crowd level updated to $level")
                                        onLevelUpdated(level, System.currentTimeMillis())
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("AmenityDetails", "Failed to update crowd level", e)
                                    }
                            }
                            .padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}