package com.example.dfwflightcompanion.amenities

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val ISSUE_TYPES = listOf(
    "Closed or unavailable",
    "Incorrect location",
    "Out of service",
    "Information is incorrect",
    "Other"
)

private const val DETAILS_MAX_LENGTH = 500

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserReportScreen(
    navController: NavController,
    selectedAmenityId: String,
    selectedAmenityName: String
) {
    val context = LocalContext.current
    var selectedIssue by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report an Issue") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tell us what\'s wrong with this amenity so we can review and improve the information.",
                fontSize = 14.sp
            )

            Text(
                text = selectedAmenityName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "What\'s the issue?",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ISSUE_TYPES.forEach { issue ->
                    FilterChip(
                        selected = selectedIssue == issue,
                        onClick = {
                            selectedIssue = if (selectedIssue == issue) null else issue
                        },
                        label = { Text(issue) }
                    )
                }
            }

            Text(
                text = "Additional details (optional)",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = details,
                onValueChange = {
                    if (it.length <= DETAILS_MAX_LENGTH) details = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("Describe the issue") },
                supportingText = {
                    Text("${details.length} / $DETAILS_MAX_LENGTH")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val issue = selectedIssue ?: return@Button
                    isSubmitting = true
                    val report = hashMapOf(
                        "AmenityID" to selectedAmenityId,
                        "AmenityName" to selectedAmenityName,
                        "ReportType" to issue,
                        "Description" to details,
                        "CreatedAt" to FieldValue.serverTimestamp()
                    )
                    FirebaseFirestore.getInstance()
                        .collection("UserReports")
                        .add(report)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Report submitted", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                        .addOnFailureListener { e ->
                            isSubmitting = false
                            Toast.makeText(
                                context,
                                "Failed to submit: ${e.localizedMessage ?: "unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                },
                enabled = selectedIssue != null && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSubmitting) "Submitting\u2026" else "Submit Report")
            }
        }
    }
}
