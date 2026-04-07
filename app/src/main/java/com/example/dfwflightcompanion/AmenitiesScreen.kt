package com.example.dfwflightcompanion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

data class Amenity(
    val amenityId: String = "",
    val amenityType: String = "",
    val isAccessible: Boolean = false,
    val name: String = "",
    val nodeId: String = "",
    val subTypeName: String = ""
)

@Composable
fun AmenitiesScreen() {
    val db = FirebaseFirestore.getInstance()
    var amenities by remember { mutableStateOf<List<Amenity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter States
    var filterAccessible by remember { mutableStateOf(true) }
    var filterNotAccessible by remember { mutableStateOf(true) }
    var selectedSubTypes by remember { mutableStateOf(setOf<String>()) }
    
    var isAccessMenuExpanded by remember { mutableStateOf(false) }
    var isSubTypeMenuExpanded by remember { mutableStateOf(false) }

    val filteredAmenities = remember(searchQuery, amenities, filterAccessible, filterNotAccessible, selectedSubTypes) {
        amenities.filter { amenity ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val query = searchQuery.trim().lowercase()
                amenity.name.lowercase().contains(query) ||
                        amenity.subTypeName.lowercase().contains(query)
            }

            val matchesAccessibility = (amenity.isAccessible && filterAccessible) || 
                                       (!amenity.isAccessible && filterNotAccessible)

            val matchesSubType = if (selectedSubTypes.isEmpty()) true else {
                selectedSubTypes.contains(amenity.subTypeName)
            }

            matchesSearch && matchesAccessibility && matchesSubType
        }
    }

    LaunchedEffect(Unit) {
        db.collection("Amenity")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    try {
                        Amenity(
                            amenityId = doc.getString("AmenityID") ?: "",
                            amenityType = doc.getString("AmenityType") ?: "",
                            isAccessible = doc.getBoolean("IsAccessible") ?: false,
                            name = doc.getString("Name") ?: "",
                            nodeId = doc.getString("NodeID") ?: "",
                            subTypeName = doc.getString("SubTypeName") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                amenities = list
                isLoading = false
            }
            .addOnFailureListener { exception ->
                errorMessage = exception.message
                isLoading = false
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accessibility Menu
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isAccessMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Accessibility")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = isAccessMenuExpanded,
                        onDismissRequest = { isAccessMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Accessible") },
                            onClick = { filterAccessible = !filterAccessible },
                            leadingIcon = { Checkbox(checked = filterAccessible, onCheckedChange = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Not Accessible") },
                            onClick = { filterNotAccessible = !filterNotAccessible },
                            leadingIcon = { Checkbox(checked = filterNotAccessible, onCheckedChange = null) }
                        )
                    }
                }

                // Sub-type Menu
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { isSubTypeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sub-type")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = isSubTypeMenuExpanded,
                        onDismissRequest = { isSubTypeMenuExpanded = false }
                    ) {
                        listOf("Male", "Female", "Handicap").forEach { type ->
                            val isSelected = selectedSubTypes.contains(type)
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedSubTypes = if (isSelected) {
                                        selectedSubTypes - type
                                    } else {
                                        selectedSubTypes + type
                                    }
                                },
                                leadingIcon = { Checkbox(checked = isSelected, onCheckedChange = null) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (errorMessage != null) {
                    Text(
                        text = "Error: $errorMessage",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (filteredAmenities.isEmpty()) {
                    Text(
                        text = "No results match your filters.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAmenities) { amenity ->
                            AmenityCard(amenity = amenity)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AmenityCard(amenity: Amenity) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = amenity.name, style = MaterialTheme.typography.titleLarge)
            Text(text = "Type: ${amenity.amenityType}", style = MaterialTheme.typography.bodyMedium)
            if (amenity.subTypeName.isNotEmpty()) {
                Text(text = "Subtype: ${amenity.subTypeName}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (amenity.isAccessible) "Accessible" else "Not Accessible",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (amenity.isAccessible) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
