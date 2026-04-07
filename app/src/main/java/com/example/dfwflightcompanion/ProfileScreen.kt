package com.example.dfwflightcompanion

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.functions.functions

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val createdAt: Long = 0
)

@Composable
fun ProfileScreen() {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
