package com.example.dfwflightcompanion

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.Firebase
import com.google.firebase.functions.functions

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val createdAt: Long = 0
)

@Composable
fun ProfileScreen(
    navController: NavHostController,
    disabilityProfileViewModel: DisabilityProfileViewModel
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val disabilityProfile by disabilityProfileViewModel.profile.collectAsState()

    LaunchedEffect(Unit) {
        try {
            val functions = Firebase.functions
            // ONLY if testing locally:
            // functions.useEmulator("10.0.2.2", 5001)

            functions.getHttpsCallable("getUserProfile")
                .call()
                .addOnSuccessListener { result ->
                    val data = result.getData() as? Map<String, Any>
                    if (data != null) {
                        profile = UserProfile(
                            id = data["id"] as? String ?: "",
                            username = data["Username"] as? String ?: "Unknown User",
                            email = data["Email"] as? String ?: "No email provided",
                            createdAt = data["CreatedAt"] as? Long ?: 0
                        )
                    }
                    isLoading = false
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileScreen", "Error calling getUserProfile", exception)
                    errorMessage = "Failed to load profile: ${exception.message}"
                    isLoading = false
                }
        } catch (e: Exception) {
            Log.e("ProfileScreen", "Failed to initialize functions", e)
            errorMessage = "Initialization error: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "My Profile",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        } else if (profile != null) {
            ProfileInfoCard(profile!!)
        } else {
            Text(text = "No profile found.")
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Accessibility Profile",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 4.dp)
        )
        Text(
            text = "Stored only on this device — your accessibility information is never shared with anyone or sent to any server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 12.dp)
        )

        DisabilityProfileSection(
            profile = disabilityProfile,
            onCreate = { navController.navigate(Routes.DISABILITY_PROFILE_FORM) },
            onEdit = { navController.navigate(Routes.DISABILITY_PROFILE_FORM) },
            onDelete = { disabilityProfileViewModel.delete() }
        )
    }
}

@Composable
fun ProfileInfoCard(profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ProfileRow(icon = Icons.Default.Person, label = "Username", value = profile.username)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            ProfileRow(icon = Icons.Default.Email, label = "Email", value = profile.email)
        }
    }
}

@Composable
fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DisabilityProfileSection(
    profile: DisabilityProfile?,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (profile == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No accessibility profile yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Create one to get personalized routes and amenity suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(onClick = onCreate) { Text("Create Disability Profile") }
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DisabilityProfileSummary(profile)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text("Edit")
                    }
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Delete") }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete accessibility profile?") },
            text = { Text("Your saved preferences will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DisabilityProfileSummary(profile: DisabilityProfile) {
    val lines = buildList {
        if (profile.usesWheelchair) add("Uses a wheelchair")
        if (profile.avoidStairs) add("Avoids stairs (prefers elevators)")
        if (profile.hasVisualImpairment) add("Visual impairment")
        if (profile.hasHearingImpairment) add("Hearing impairment")
        if (profile.requiresAccessibleRestroom) add("Requires accessible restroom")
        if (profile.prefersFamilyRestroom) add("Prefers family restroom")
        if (profile.restroomGenderPreference != RestroomPreference.ANY) {
            add("Restroom preference: ${
                profile.restroomGenderPreference.name.lowercase().replace('_', ' ')
            }")
        }
        add("Route priority: ${profile.routePriority.name.lowercase().replace('_', ' ')}")
    }
    Column {
        if (lines.isEmpty()) {
            Text(
                "No preferences selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }

        if (profile.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = profile.notes,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}